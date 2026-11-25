package dev.gzclp.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Debug-only: the Compose test activity comes from ui-test-manifest, a debug dependency. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class InlineTitleTest {
    @get:Rule val rule = createComposeRule()

    @Test fun titleShowsInlineOnceTheLargeTitleScrollsAway() {
        rule.setContent {
            GzTheme(Palettes.tokyo) {
                ScreenFrame {
                    NavRow({})
                    Txt("Bench press", Type.largeTitle)
                    repeat(30) { GroupRow(first = it == 0) { Txt("Row $it", Type.body) } }
                }
            }
        }
        rule.onAllNodesWithText("Bench press").assertCountEquals(1)
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onAllNodesWithText("Bench press").assertCountEquals(2)
        System.getProperty("gzclp.shots")?.let { rule.onRoot().captureRoboImage(File(it, "tokyo-inline-title.png").path) }
        rule.onRoot().performTouchInput { swipeDown() }
        rule.waitForIdle()
        rule.onAllNodesWithText("Bench press").assertCountEquals(1)
    }
}
