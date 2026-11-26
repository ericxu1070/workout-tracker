package dev.gzclp.tracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * With a sheet open, the screen behind it went black: washed toward the background and blurred flat, the header and its
 * tab switch vanished. Under a sheet the switch's accent thumb must still stand out from the bar, dimmed only by the scrim.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class SheetBackdropTest {
    @get:Rule val rule = createComposeRule()

    /** The spread of luminance across the header band (the top eighth of the screen), from its darkest pixel to its brightest. */
    private fun headerSpread(p: Palette): Float {
        rule.setContent {
            GzTheme(p) {
                TabFrame(Tab.Workout, {}) { WorkoutPages(1, {}, {}) { ScreenFrame { Txt("Page", Type.body) } } }
                Sheet(true, {}) { Txt("Log past session", Type.title3) }
            }
        }
        rule.waitForIdle()
        val file = File.createTempFile("sheet", ".png").apply { deleteOnExit() }
        rule.onRoot().captureRoboImage(file.path, roborazziOptions = RoborazziOptions(taskType = RoborazziTaskType.Record))
        val img = android.graphics.BitmapFactory.decodeFile(file.path)
        val lum: List<Float> = (0 until img.width step 4).flatMap { x -> (img.height / 20 until img.height / 8 step 4).map { y -> Color(img.getPixel(x, y)).luminance() } }
        return lum.max() - lum.min()
    }

    @Test fun theHeaderShowsUnderADarkSheet() {
        val spread = headerSpread(Palettes.dark)
        assertTrue("spread $spread", spread > 0.08f)
    }

    @Test fun theHeaderShowsUnderALiquidGlassSheet() {
        val spread = headerSpread(Palettes.glass)
        assertTrue("spread $spread", spread > 0.08f)
    }
}
