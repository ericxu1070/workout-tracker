package dev.gzclp.tracker.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.food.FoodLog
import dev.gzclp.tracker.food.Meal
import dev.gzclp.tracker.food.Nutrition
import dev.gzclp.tracker.food.Prediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files
import java.time.OffsetDateTime

/** Every control in a meal's sheet, its estimates and the custom estimate's Low and High, is whole and on screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h914dp-420dpi")
class MealSheetTest {
    @get:Rule(order = 0) val activity = ComposeActivity()
    @get:Rule(order = 1) val rule = createComposeRule()

    private val at = OffsetDateTime.parse("2026-11-27T12:30:00-08:00")

    /** A portrait phone photo, the tallest the sheet draws (3:4). */
    private fun meal(): Meal {
        val bmp = android.graphics.Bitmap.createBitmap(900, 1200, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bmp).drawColor(0xFF6D4C41.toInt())
        val cam = File.createTempFile("cam", ".jpg").apply { outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) } }
        return FoodLog(Files.createTempDirectory("food").toFile()).add(cam, at)
    }

    private val guesses = listOf(
        Prediction("Chicken rice bowl", 0.6f, Nutrition.KcalRange(550, 750)),
        Prediction("Burrito bowl", 0.2f, Nutrition.KcalRange(650, 900)),
        Prediction("Poke bowl", 0.1f, Nutrition.KcalRange(450, 650)),
    )

    /** A button's bounds, or with [own] the text's own (not the control it is merged into). */
    private fun bounds(text: String, own: Boolean = false) = rule.onNodeWithText(text, useUnmergedTree = own).fetchSemanticsNode().boundsInRoot

    private fun assertWhole(name: String, r: Rect, root: Rect) {
        assertTrue("$name is squashed: $r", r.height >= 44f * density() - 1f && r.width > 0f)
        assertTrue("$name runs off the sheet: $r in $root", r.top >= root.top && r.bottom <= root.bottom && r.left >= root.left && r.right <= root.right)
    }

    private fun density() = rule.density.density

    private fun check(p: Palette, name: String) {
        val m = meal()
        rule.setContent { GzTheme(p) { Sheet(true, {}) { MealSheet(m, at.toLocalDate(), {}, {}) { guesses } } } }
        rule.waitForIdle()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        for (t in listOf("Custom estimate", "Delete meal", "Done")) assertWhole(t, bounds(t), root)
        rule.onNodeWithText("Custom estimate").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/shots/meal-sheet-$name.png")
        val steps = listOf("Decrease", "Increase").flatMap { d -> rule.onAllNodesWithContentDescription(d, substring = true).fetchSemanticsNodes().map { it.boundsInRoot } }
        assertEquals("two steppers' buttons", 4, steps.size)
        steps.forEach { assertWhole("stepper button", it, root) }
        for (i in steps.indices) for (j in i + 1 until steps.size) assertTrue("buttons overlap: ${steps[i]} ${steps[j]}", !steps[i].overlaps(steps[j]))
        for (t in listOf("Cancel", "Save")) assertWhole(t, bounds(t), root)
        val photo = rule.onNodeWithContentDescription("Meal at", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the photo runs onto the title: $photo", photo.top >= bounds(Fmt.weighInTime(at, at.toLocalDate()), own = true).bottom)
        assertTrue("the photo runs onto Edit estimate: $photo", photo.bottom <= bounds("Edit estimate", own = true).top)
    }

    @Test fun fitsFlat() = check(Palettes.tokyo, "flat")

    @Test fun fitsLiquidGlass() = check(Palettes.tokyo.glassed, "glass")

    @Test @Config(qualifiers = "w360dp-h640dp-320dpi")
    fun fitsSmallPhoneAtLargeText() {
        RuntimeEnvironment.setFontScale(1.3f)
        check(Palettes.tokyo, "small")
    }
}
