package dev.gzclp.tracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The flat tab bar's held pill, read off real pixels. It is the color of a card and grows past the bar, so over a card that runs down
 * to the bar (History, Upcoming) the part above the bar had nothing to mark it: the pill looked cut off flat along the bar's top edge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class TabBarPillTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun frame(): Bitmap {
        val v = rule.activity.window.decorView
        return Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { rule.runOnUiThread { v.draw(Canvas(it)) } }
    }

    /** How many pixels of the row [dp] above the bar's top edge, across the tab [label], stand off the card's color by more than antialiasing's rounding. */
    private fun marked(p: Palette, label: String, dp: Int): Int {
        val bar = rule.onNode(SemanticsMatcher.keyIsDefined(ThumbLookKey)).fetchSemanticsNode().boundsInRoot
        val tab = rule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        val img = frame()
        val y = (bar.top - dp * rule.density.density).toInt()
        val card = p.card.toArgb()
        fun off(c: Int) = listOf(16, 8, 0).maxOf { abs((c shr it and 0xFF) - (card shr it and 0xFF)) }
        return (tab.left.toInt() - 40..tab.right.toInt() + 40).count { off(img.getPixel(it, y)) >= 8 }
    }

    private fun heldPillShowsOverACard(p: Palette) {
        rule.setContent {
            GzTheme(p) {
                Column(Modifier.fillMaxSize().background(p.bg)) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(p.card))
                    TabBar(Tab.Exercises) {}
                }
            }
        }
        rule.waitForIdle()
        // 6 dp above the bar: over its hairline, under the top of a lifted pill, which stands 10 dp past the bar.
        assertEquals("the card is plain at rest", 0, marked(p, "Exercises", 6))
        val tab = rule.onNodeWithText("Exercises").fetchSemanticsNode().boundsInRoot
        rule.onRoot().performTouchInput { down(tab.center) }
        rule.mainClock.advanceTimeBy(1000)
        // Both sides of the pill cross that row.
        assertTrue("the held pill's edge shows above the bar", marked(p, "Exercises", 6) >= 2)
        rule.onRoot().performTouchInput { up() }
    }

    @Test fun heldPillShowsOverACardInDark() = heldPillShowsOverACard(Palettes.dark)
    @Test fun heldPillShowsOverACardInLight() = heldPillShowsOverACard(Palettes.light)
    @Test fun heldPillShowsOverACardInTokyoNight() = heldPillShowsOverACard(Palettes.tokyo)
}
