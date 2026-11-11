package dev.gzclp.tracker.food

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files
import java.time.OffsetDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FoodLogTest {
    private val dir = Files.createTempDirectory("food").toFile()
    private val log = FoodLog(File(dir, "food"))
    private val noon = OffsetDateTime.parse("2026-11-10T12:30:05-08:00")

    private fun photo(w: Int, h: Int, orientation: Int? = null) = File(dir, "camera.jpg").apply {
        outputStream().use { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (orientation != null) ExifInterface(path).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "$orientation"); saveAttributes() }
    }

    private fun size(f: File) = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(f.path, it) }.let { it.outWidth to it.outHeight }

    @Test fun namesRoundTripWithTheirOffset() {
        assertEquals("20261110T123005-0800.jpg", FoodLog.name(noon))
        assertEquals(noon, FoodLog.parse(FoodLog.name(noon)))
        assertNull(FoodLog.parse("20261110T123005-0800.jpg.tmp"))
        assertNull(FoodLog.parse("notes.jpg"))
    }

    @Test fun addDownscalesListsAndDeletes() {
        val cam = photo(4000, 3000)
        val meal = log.add(cam, noon.plusNanos(5))
        assertFalse(cam.exists())
        assertEquals(noon, meal.at)
        assertEquals(1280 to 960, size(meal.file))

        // A second photo in the same second gets the next second, and meals come back oldest first.
        val second = log.add(photo(800, 600), noon)
        assertEquals(noon.plusSeconds(1), second.at)
        assertEquals(800 to 600, size(second.file))
        val early = log.add(photo(100, 100), noon.minusHours(4))
        assertEquals(listOf(early, meal, second), log.meals())

        assertTrue(log.delete(meal))
        assertEquals(listOf(early, second), log.meals())
    }

    @Test fun cameraRotationIsAppliedSoSavedPhotosAreUpright() {
        val meal = log.add(photo(4000, 3000, ExifInterface.ORIENTATION_ROTATE_90), noon)
        assertEquals(960 to 1280, size(meal.file))
    }

    @Test fun anUnreadablePhotoIsNotSaved() {
        val bad = File(dir, "camera.jpg").apply { writeText("not a jpeg") }
        assertTrue(runCatching { log.add(bad, noon) }.isFailure)
        assertEquals(emptyList<Meal>(), log.meals())
    }
}
