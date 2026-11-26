package dev.gzclp.tracker.ui

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.R
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the real adaptive launcher icon's layers as a launcher shows them: a circle, a squircle, and the themed monochrome icon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-mdpi")
class IconShots {
    @get:Rule val rule = createComposeRule()

    private val icon = ApplicationProvider.getApplicationContext<android.content.Context>()
        .getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable

    /** The launcher shows the middle 72 of the 108 dp layers, masked to its shape. */
    private fun shot(name: String, shape: Shape, vararg layers: Drawable) {
        val out = System.getProperty("gzclp.shots")
        assumeNotNull(out)
        rule.setContent {
            Box(Modifier.size(256.dp).clip(shape)) {
                Canvas(Modifier.size(256.dp)) {
                    val pad = (size.width * 18 / 72).toInt()
                    drawIntoCanvas { c ->
                        layers.forEach { it.setBounds(-pad, -pad, size.width.toInt() + pad, size.height.toInt() + pad); it.draw(c.nativeCanvas) }
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage(File(out, "icon-$name.png").path)
    }

    @Test fun circle() = shot("circle", CircleShape, icon.background, icon.foreground)

    /** Same corner radius as the brand kit's app-icon SVG (57.6 of 256). */
    @Test fun squircle() = shot("squircle", RoundedCornerShape(57.6.dp), icon.background, icon.foreground)

    /** Android 13+ themed icons tint the monochrome layer over a pale tinted disc. */
    @Test fun themed() {
        val mono = icon.monochrome
        assertNotNull(mono)
        mono!!.setTint(0xFF1F3A73.toInt())
        shot("themed", CircleShape, android.graphics.drawable.ColorDrawable(0xFFD8E2FF.toInt()), mono)
    }
}
