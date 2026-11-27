package dev.gzclp.tracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Loaded's palette wipe: a new theme, or a new motion mode, spreads from the tap as a widening circle over a still of the screen
 * as it was, its edge a bright ring that the screen's ripple bends. A wipe started while others spread joins them: each keeps
 * its own still and circle, so none stops.
 */
class PaletteWipe {
    private class Run(val shot: ImageBitmap, val at: Offset) {
        val spread = Animatable(0f)
    }

    private val runs = mutableStateListOf<Run>()
    private var layer: GraphicsLayer? = null

    /** Makes [change], spreading it from [from] when [on]; without a still of the screen it just changes. */
    suspend fun run(from: Offset, on: Boolean, ripples: () -> RippleField?, change: () -> Unit) {
        // The still is the content alone, taken under the ripples and the other wipes: a copy of the window had its rings bent
        // into it, which then stood still on the screen while the live ones ran over them.
        val still = if (on) runCatching { layer?.toImageBitmap() }.getOrNull() else null
        if (still == null) return change()
        // A still is a screen of pixels: past [MAX_RUNS] the oldest wipe, nearly spread, ends early.
        if (runs.size >= MAX_RUNS) runs.removeAt(0)
        val run = Run(still, from).also { runs += it }
        change()
        try {
            coroutineScope {
                // A change of mode makes its ripples droppable on the next composition, so the edge's ripple waits a frame for it.
                launch {
                    repeat(2) { withFrameNanos {} }
                    ripples()?.drop(from, 0.5f)
                }
                run.spread.animateTo(1f, tween(WIPE_MS, easing = EaseInOutCubic))
            }
        } finally {
            runs -= run
        }
    }

    /** Draws, over this content, each wipe's old screen outside its spreading circle. */
    @Composable
    fun Modifier.wipe(edge: Color): Modifier {
        val content = rememberGraphicsLayer().also { layer = it }
        return drawWithContent {
            content.record { this@drawWithContent.drawContent() }
            drawLayer(content)
            // Newest first: where an older wipe has not reached yet, its still, older again, covers the newer one's.
            for (w in runs.reversed()) {
                val t = w.spread.value
                val at = w.at
                val far = maxOf(hypot(at.x, at.y), hypot(size.width - at.x, at.y), hypot(at.x, size.height - at.y), hypot(size.width - at.x, size.height - at.y))
                val r = far * t
                clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(at, r)) }, ClipOp.Difference) { drawImage(w.shot) }
                drawCircle(edge.copy(alpha = 0.5f * (1f - t)), r, at, style = Stroke(3.dp.toPx()))
            }
        }
    }

    companion object {
        private const val WIPE_MS = 720
        private const val MAX_RUNS = 4
    }
}
