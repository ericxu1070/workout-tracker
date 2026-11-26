package dev.gzclp.tracker.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.hypot

/**
 * Loaded's palette wipe: a new theme, or a new motion mode, spreads from the tap as a widening circle over a still of the screen
 * as it was, its edge a bright ring that the screen's ripple bends.
 */
class PaletteWipe {
    private var shot by mutableStateOf<ImageBitmap?>(null)
    private val spread = Animatable(1f)
    private var at = Offset.Zero

    /** Makes [change], spreading it from [from] when [on]; without a still of the screen it just changes. */
    suspend fun run(window: Window, from: Offset, on: Boolean, ripples: () -> RippleField?, change: () -> Unit) {
        val still = if (on) snapshot(window) else null
        if (still == null) return change()
        at = from
        spread.snapTo(0f)
        shot = still
        change()
        coroutineScope {
            // A change of mode makes its ripple field on the next composition, so the edge's ripple waits a frame for it.
            launch {
                repeat(2) { withFrameNanos {} }
                ripples()?.drop(from, 0.5f)
            }
            spread.animateTo(1f, tween(WIPE_MS, easing = EaseInOutCubic))
        }
        shot = null
    }

    /** Draws the old screen outside the spreading circle, over this content. */
    fun Modifier.wipe(edge: Color) = drawWithContent {
        drawContent()
        val img = shot ?: return@drawWithContent
        val t = spread.value
        val far = maxOf(hypot(at.x, at.y), hypot(size.width - at.x, at.y), hypot(at.x, size.height - at.y), hypot(size.width - at.x, size.height - at.y))
        val r = far * t
        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(at, r)) }
        clipPath(circle, ClipOp.Difference) { drawImage(img) }
        drawCircle(edge.copy(alpha = 0.5f * (1f - t)), r, at, style = Stroke(3.dp.toPx()))
    }

    private suspend fun snapshot(window: Window): ImageBitmap? {
        val v = window.decorView
        if (v.width == 0 || v.height == 0) return null
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { c ->
            PixelCopy.request(window, bmp, { r -> c.resume(if (r == PixelCopy.SUCCESS) bmp.asImageBitmap() else null) }, Handler(Looper.getMainLooper()))
        }
    }

    companion object {
        private const val WIPE_MS = 720
    }
}
