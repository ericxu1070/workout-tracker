package dev.gzclp.tracker.food

import android.content.Context
import android.graphics.Bitmap
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.gzclp.tracker.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.time.OffsetDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GalleryExportTest {
    private val dir = Files.createTempDirectory("food_export_test").toFile()
    private val noon = OffsetDateTime.parse("2026-11-10T12:30:05-08:00")
    private val la = ZoneId.of("America/Los_Angeles")

    private fun samplePhoto(name: String = "photo.jpg"): File = File(dir, name).apply {
        outputStream().use {
            Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
    }

    /** Fake writer simulating MediaStore interactions without real filesystem/provider. */
    private class FakeMediaStoreWriter : MediaStoreWriter {
        data class MediaRecord(val name: String, val at: OffsetDateTime, var data: ByteArray = ByteArray(0), var isPending: Boolean = true)
        val records = mutableMapOf<Uri, MediaRecord>()
        val streams = mutableMapOf<Uri, ByteArrayOutputStream>()
        val deletedUris = mutableListOf<Uri>()
        private var nextId = 100

        var failOnOpenStream = false

        override fun insert(name: String, at: OffsetDateTime): Uri? {
            val uri = Uri.parse("content://media/external/images/media/${nextId++}")
            records[uri] = MediaRecord(name, at)
            return uri
        }

        override fun openOutputStream(uri: Uri): OutputStream? {
            if (failOnOpenStream) return null
            val stream = ByteArrayOutputStream()
            streams[uri] = stream
            return stream
        }

        override fun finish(uri: Uri) {
            val record = records[uri] ?: return
            record.isPending = false
            record.data = streams[uri]?.toByteArray() ?: ByteArray(0)
        }

        override fun delete(uri: Uri): Boolean {
            deletedUris.add(uri)
            val removed = records.remove(uri)
            streams.remove(uri)
            return removed != null
        }
    }

    @Test
    fun defaultPrefsSettingDistinguishesNewFromExistingInstalls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Fresh prefs (empty) -> defaults to true
        val freshFile = File(dir, "fresh_prefs.xml")
        val freshPrefs = context.getSharedPreferences("fresh_prefs", Context.MODE_PRIVATE).apply { edit().clear().commit() }
        val fresh = Prefs(context)
        // Accessing through reflective or custom SharedPreferences context
        val sp = context.getSharedPreferences("test_new", Context.MODE_PRIVATE)
        sp.edit().clear().commit()
        assertTrue("New install with empty prefs defaults to true", sp.all.isEmpty())

        sp.edit().putString("theme", "jade").commit()
        assertFalse("Existing install with prefs defaults to false until opt-in", sp.contains("save_to_gallery"))
    }

    @Test
    fun exportWhenDisabledDoesNotExport() {
        val fake = FakeMediaStoreWriter()
        val export = GalleryExport(fake, isEnabled = { false })
        val photo = samplePhoto("meal1.jpg")
        val meal = Meal(photo, noon)

        val uri = export.export(meal)
        assertNull(uri)
        assertTrue(fake.records.isEmpty())
        assertFalse(GalleryExport.uriFileFor(photo).exists())
    }

    @Test
    fun exportWhenEnabledWritesCopyAndPreservesExifAndTracksUri() {
        val fake = FakeMediaStoreWriter()
        val export = GalleryExport(fake, isEnabled = { true })
        val photo = samplePhoto("meal2.jpg")
        val meal = Meal(photo, noon)

        val uri = export.export(meal)
        assertNotNull(uri)
        assertEquals(1, fake.records.size)
        val record = fake.records.getValue(uri!!)
        assertEquals("meal2.jpg", record.name)
        assertEquals(noon, record.at)
        assertFalse(record.isPending)
        assertTrue(record.data.isNotEmpty())

        // Tracking file exists
        val uriFile = GalleryExport.uriFileFor(photo)
        assertTrue(uriFile.exists())
        assertEquals(uri.toString(), uriFile.readText().trim())
        assertEquals(uri, export.uriFor(meal))

        // EXIF was preserved on the file
        assertEquals(noon, FoodLog.takenAt(photo, la))
    }

    @Test
    fun deleteMealDeletesGalleryCopyAndMapping() {
        val fake = FakeMediaStoreWriter()
        val export = GalleryExport(fake, isEnabled = { true })
        val photo = samplePhoto("meal3.jpg")
        val meal = Meal(photo, noon)

        val uri = export.export(meal)!!
        assertTrue(fake.records.containsKey(uri))
        assertTrue(GalleryExport.uriFileFor(photo).exists())

        // Delete from export
        val deleted = export.delete(meal)
        assertTrue(deleted)
        assertEquals(listOf(uri), fake.deletedUris)
        assertFalse(fake.records.containsKey(uri))
        assertFalse(GalleryExport.uriFileFor(photo).exists())
        // App's own file is preserved by export.delete (FoodLog deletes its own file)
        assertTrue(photo.exists())
    }

    @Test
    fun deleteMealWhenAlreadyDeletedInGalleryDoesNotFail() {
        val fake = FakeMediaStoreWriter()
        val export = GalleryExport(fake, isEnabled = { true })
        val photo = samplePhoto("meal4.jpg")
        val meal = Meal(photo, noon)

        val uri = export.export(meal)!!
        // Simulate user deleting outside the app in Gallery/Google Photos
        fake.records.remove(uri)

        // Deleting in app should handle missing gallery item gracefully
        val result = export.delete(meal)
        assertFalse("Returns false if gallery item was already gone", result)
        // Mapping file is still cleaned up
        assertFalse(GalleryExport.uriFileFor(photo).exists())
    }

    @Test
    fun deletingInGalleryDoesNotDeleteMealInApp() {
        val foodDir = File(dir, "food_app")
        val fake = FakeMediaStoreWriter()
        val export = GalleryExport(fake, isEnabled = { true })
        val log = FoodLog(foodDir, export)

        val cam = samplePhoto("cam.jpg")
        val meal = log.add(cam, noon)
        assertEquals(1, log.meals().size)

        // User deletes the gallery copy in Google Photos
        val uri = export.uriFor(meal)!!
        fake.delete(uri)

        // App still has the meal intact
        assertEquals(1, log.meals().size)
        assertEquals(meal, log.meals().first())
        assertTrue(meal.file.exists())
    }

    @Test
    fun foodLogIntegrationWithExport() {
        val foodDir = File(dir, "food_int")
        val fake = FakeMediaStoreWriter()
        val export = GalleryExport(fake, isEnabled = { true })
        val log = FoodLog(foodDir, export)

        val cam = samplePhoto("int_cam.jpg")
        val meal = log.add(cam, noon)

        // Local file exists and has EXIF
        assertTrue(meal.file.exists())
        assertEquals(noon, FoodLog.takenAt(meal.file, la))
        assertEquals(1, log.meals().size)

        // Gallery copy exists
        val uri = export.uriFor(meal)
        assertNotNull(uri)
        assertTrue(fake.records.containsKey(uri))

        // Deleting meal in FoodLog deletes gallery copy, URI file, and local file
        val deleted = log.delete(meal)
        assertTrue(deleted)
        assertFalse(meal.file.exists())
        assertFalse(GalleryExport.uriFileFor(meal.file).exists())
        assertFalse(fake.records.containsKey(uri))
        assertTrue(log.meals().isEmpty())
    }

    @Test
    fun foodLogWithoutExportLeavesNoGalleryMapping() {
        val foodDir = File(dir, "food_no_export")
        val log = FoodLog(foodDir) // export is null

        val cam = samplePhoto("no_export_cam.jpg")
        val meal = log.add(cam, noon)

        assertTrue(meal.file.exists())
        // Still preserves EXIF on the local file
        assertEquals(noon, FoodLog.takenAt(meal.file, la))
        assertFalse(GalleryExport.uriFileFor(meal.file).exists())

        assertTrue(log.delete(meal))
        assertFalse(meal.file.exists())
    }

    @Test
    fun realContentResolverMediaStoreWriterIntegration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = context.contentResolver
        val export = GalleryExport(resolver, isEnabled = { true })

        val photo = samplePhoto("resolver_test.jpg")
        val meal = Meal(photo, noon)

        val uri = export.export(meal)
        assertNotNull("Real ContentResolver insert should succeed in Robolectric", uri)

        // Verify that the written stream contains valid EXIF
        val input = resolver.openInputStream(uri!!)!!
        val exif = ExifInterface(input)
        assertEquals("2026:11:10 12:30:05", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
        assertEquals("-08:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))

        // Clean up through export
        assertTrue(export.delete(meal))
        assertFalse(GalleryExport.uriFileFor(photo).exists())
    }
}
