package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import dev.gzclp.tracker.form.Bone
import dev.gzclp.tracker.form.Focus
import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.form.Spot
import dev.gzclp.tracker.form.V
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The form viewer's ink line closes around the figure, whichever side it is seen from. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w300dp-h240dp-420dpi")
class FormOutlineTest {
    @get:Rule(order = 0) val activity = ComposeActivity()
    @get:Rule(order = 1) val rule = createComposeRule()
    private val inked = look

    @After fun wear() { look = inked }

    @Test fun noSkinTouchesThePageAnywhereAroundTheFigure() {
        assertTrue("the look in use has a line", inked.ink > 0f)
        val p = Palettes.light
        // Lit all over in the accent, the body is told by its hue from the grey page and the grey ink.
        val all = Focus(100.0, Spot(Bone.CHEST, V.ZERO))
        val yaw = mutableStateOf(0f)
        val worn = mutableStateOf(Look.UNLINED)
        var view: android.view.View? = null
        rule.setContent {
            view = LocalView.current
            look = worn.value
            // An id with no motion of its own stands and raises its arms, with no gear to touch.
            GzTheme(p) { FormView(Motions.of("arms"), Modifier.fillMaxSize().background(p.bg), focus = all, still = 0.5f, orbit = Orbit().apply { this.yaw = yaw.value; pitch = 12f }) }
        }
        /** How many pixels of skin have the page right beside them. */
        fun bare(): Int = rule.runOnIdle {
            val v = view!!
            val img = android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
            val w = img.width
            val px = IntArray(w * img.height).also { img.getPixels(it, 0, w, 0, 0, w, img.height) }
            fun blue(c: Int) = (c and 255) - (c shr 16 and 255)
            fun skin(c: Int) = blue(c) > 80
            fun page(c: Int) = blue(c) < 16 && (c shr 16 and 255) > 170
            var n = 0
            for (y in 1 until img.height - 1) for (x in 1 until w - 1) {
                val i = y * w + x
                if (skin(px[i]) && (page(px[i - 1]) || page(px[i + 1]) || page(px[i - w]) || page(px[i + w]))) n++
            }
            n
        }
        // Without the line the whole edge of the figure is skin beside the page, which shows that the count sees it.
        val edge = bare()
        assertTrue("an unlined figure's edge: $edge pixels", edge > 400)
        worn.value = inked
        for (at in 0 until 360 step 45) {
            yaw.value = at.toFloat()
            val n = bare()
            assertTrue("skin beside the page from $at°: $n pixels, of $edge with no line", n * 50 < edge)
        }
    }
}
