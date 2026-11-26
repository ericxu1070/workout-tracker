package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** What Liquid Glass bars and sheets blur: the content beside them, recorded each time it draws, and where it sits in the window. */
class Backdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

/** The backdrop for the glass floating here; null where nothing records one, and the glass goes solid. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Space at the bottom of a page that floating glass covers; a page's scroller pads its end by it so its last row clears the glass. */
val LocalBottomInset = staticCompositionLocalOf { 0.dp }

/** A backdrop to record content into, in Liquid Glass only. */
@Composable
fun rememberBackdrop(): Backdrop? {
    if (!LocalPalette.current.glass) return null
    val layer = rememberGraphicsLayer()
    return remember(layer) { Backdrop(layer) }
}

/** The page under everything: [Palette.bg], or in Liquid Glass its wallpaper, pinned to the window so pages line up as they slide. */
@Composable
fun Modifier.page(): Modifier {
    val p = LocalPalette.current
    if (!p.glass) return background(p.bg)
    val window = LocalWindowInfo.current.containerSize
    var at by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { at = it.positionInRoot() }.drawBehind { wallpaper(p, at, window) }
}

/** Records this content over the wallpaper into [b], for the glass floating over it to blur; a no-op without [b]. */
@Composable
fun Modifier.backdropSource(b: Backdrop?): Modifier {
    if (b == null) return this
    val p = LocalPalette.current
    val window = LocalWindowInfo.current.containerSize
    return onGloballyPositioned { b.origin = it.positionInRoot() }.drawWithContent {
        b.layer.record {
            wallpaper(p, b.origin, window)
            this@drawWithContent.drawContent()
        }
        drawLayer(b.layer)
    }
}

/**
 * A Liquid Glass pane for a bar, sheet or floating pill, clipped to [shape]: the backdrop behind it blurred and tinted with
 * [Palette.chrome], lit by a sheen and a rim. Where no backdrop is recorded it is solid, so text never sits on sharp content.
 */
@Composable
fun Modifier.glassPane(shape: Shape, b: Backdrop? = LocalBackdrop.current): Modifier {
    val p = LocalPalette.current
    val blur = rememberGraphicsLayer()
    var at by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { at = it.positionInRoot() }.clip(shape).drawWithContent {
        if (b != null) {
            val r = BlurRadius.toPx()
            blur.renderEffect = BlurEffect(r, r, TileMode.Clamp)
            blur.record { translate(b.origin.x - at.x, b.origin.y - at.y) { drawLayer(b.layer) } }
            drawLayer(blur)
            drawRect(p.chrome)
        } else drawRect(p.chrome.copy(alpha = 1f))
        drawContent()
    }.then(if (shape == RectangleShape) Modifier else Modifier.glassEdge(shape))
}

/** Liquid Glass's lit edge on a control or pane, inside a clip to [shape]: a sheen down from the top under the content, a rim over it. */
fun Modifier.glassEdge(shape: Shape) = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawWithContent {
        glassSheen(outline)
        drawContent()
        glassRim(outline)
    }
}

/** The strongest the sheen gets, at the top edge; PaletteContrastTest holds text on controls to 4.5:1 under it. */
const val SHEEN = 0.10f

fun DrawScope.glassSheen(o: Outline) =
    drawOutline(o, Brush.verticalGradient(0f to Color.White.copy(alpha = SHEEN), 0.5f to Color.Transparent, startY = o.bounds.top, endY = o.bounds.bottom))

/** The specular rim: brightest along the top, where light falls, dim down the sides, a little back up along the bottom. Half its stroke falls outside the clip. */
fun DrawScope.glassRim(o: Outline) = drawOutline(
    o,
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.42f), 0.45f to Color.White.copy(alpha = 0.06f), 1f to Color.White.copy(alpha = 0.16f),
        startY = o.bounds.top, endY = o.bounds.bottom,
    ),
    style = Stroke(2.dp.toPx()),
)

/** A glass card's hairline edge inside its clip; a slice of a card split across lazy items draws only its own [top] and [bottom]. */
fun Modifier.cardRim(p: Palette, radius: Dp, top: Boolean = true, bottom: Boolean = true) = if (!p.glass) this else drawWithContent {
    drawContent()
    val w = 1.dp.toPx()
    val r = radius.toPx()
    val t = if (top) w / 2 else -r
    val b = if (bottom) size.height - w / 2 else size.height + r
    drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(w / 2, t), Size(size.width - w, b - t), CornerRadius(r - w / 2), style = Stroke(w))
}

private val BlurRadius = 24.dp

/** Three soft glows over the page color, laid out on the window: blue high on the left, violet midway on the right, teal low on the left. */
private fun DrawScope.wallpaper(p: Palette, at: Offset, window: IntSize) {
    drawRect(p.bg)
    val w = window.width.toFloat().takeIf { it > 0f } ?: size.width
    val h = window.height.toFloat().takeIf { it > 0f } ?: size.height
    val spots = listOf(Offset(0.1f * w, 0.12f * h) to 0.95f * w, Offset(0.95f * w, 0.5f * h) to 0.85f * w, Offset(0.15f * w, 0.92f * h) to 0.85f * w)
    for ((glow, spot) in p.glows.zip(spots)) {
        val (c, r) = spot
        drawRect(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), c - at, r))
    }
}
