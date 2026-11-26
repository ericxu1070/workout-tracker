package dev.gzclp.tracker.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** What Liquid Glass bars and sheets blur: the content beside them, recorded each time it draws, and where it sits in the window. */
class Backdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

/** The backdrop for the glass floating here; null where nothing records one, and the glass goes solid. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Space at the bottom of a page that floating glass covers; a page's scroller pads its end by it so its last row clears the glass. */
val LocalBottomInset = staticCompositionLocalOf { 0.dp }

/** Space at the top of a page that a floating header covers; a page's scroller pads its start by it. */
val LocalTopInset = staticCompositionLocalOf { 0.dp }

/** True inside a glass sheet: its controls drop their own glass, since glass never sits on glass. */
val LocalOnGlass = staticCompositionLocalOf { false }

/** How far a [scrollEdge] fades: about a switch's height. */
val ScrollEdgeFade = 36.dp

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

/** Records this content into [b], for the glass floating over it to blur; a no-op without [b]. */
fun Modifier.backdropSource(b: Backdrop?): Modifier = if (b == null) this else onGloballyPositioned { b.origin = it.positionInRoot() }.drawWithContent {
    // The wallpaper stays out: it is drawn once, behind the page, and each pane paints its own patch of it.
    b.layer.record { this@drawWithContent.drawContent() }
    drawLayer(b.layer)
}

/**
 * A Liquid Glass pane for a bar, sheet or floating pill, clipped to [shape]: its patch of wallpaper and the backdrop over it,
 * blurred and frosted with [Palette.chrome], lit by a sheen and a rim. Where no backdrop is recorded it is solid, so text never sits on sharp content.
 * A [bar] (a capsule: the tab bar, a switch floating over pages) is see-through: tinted only by [Palette.bar], blurred lightly, and on
 * API 33 and up its rim bends what passes under it at rest too ([LENS]), as an iOS 26 tab bar's does; below, it is only blurred.
 */
@Composable
fun Modifier.glassPane(shape: Shape, b: Backdrop? = LocalBackdrop.current, bar: Boolean = false): Modifier {
    val p = LocalPalette.current
    val window = LocalWindowInfo.current.containerSize
    val blur = rememberGraphicsLayer()
    val lens = if (bar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) remember { RuntimeShader(LENS) } else null
    var at by remember { mutableStateOf(Offset.Zero) }
    val tint = if (bar) p.bar else p.chrome
    return onGloballyPositioned { at = it.positionInRoot() }.clip(shape).drawWithContent {
        if (b != null) {
            // The wallpaper is smooth, so it needs no blur; only the content over it does.
            wallpaper(p, at, window)
            // Blurred at a quarter of its size and scaled back up: a blur this wide looks the same and costs a sixteenth.
            val s = BlurScale
            val radius = (if (bar) BarBlurRadius else BlurRadius).toPx() * s
            // A lens records a rim's width more all round, so its edge gathers in what lies just outside the pane.
            val m = if (lens != null) LensRim.toPx() else 0f
            blur.pivotOffset = Offset.Zero
            blur.scaleX = 1 / s
            blur.scaleY = 1 / s
            blur.renderEffect = if (lens == null) BlurEffect(radius, radius, TileMode.Clamp) else lens.run {
                setFloatUniform("box", m * s, m * s, (m + size.width) * s, (m + size.height) * s)
                setFloatUniform("radius", minOf(size.width, size.height) / 2 * s)
                setFloatUniform("amount", 1f)
                setFloatUniform("rim", LensRim.toPx() * s)
                RenderEffect.createChainEffect(
                    RenderEffect.createRuntimeShaderEffect(this, "content"),
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP),
                ).asComposeRenderEffect()
            }
            blur.record(IntSize(ceil((size.width + 2 * m) * s).toInt(), ceil((size.height + 2 * m) * s).toInt())) {
                scale(s, Offset.Zero) { translate(b.origin.x - at.x + m, b.origin.y - at.y + m) { drawLayer(b.layer) } }
            }
            translate(-m, -m) { drawLayer(blur) }
            drawRect(tint)
        } else drawRect(tint.copy(alpha = 1f))
        drawContent()
    }.then(if (shape == RectangleShape) Modifier else Modifier.glassEdge(shape))
}

/**
 * iOS's scroll edge behind chrome floating over a page, in place of a hairline: the page's wallpaper, solid but for [fade] along its
 * bottom edge (its top edge if [up]) where it fades out, so what scrolls under the chrome's text is hidden and slips out of sight softly.
 */
@Composable
fun Modifier.scrollEdge(fade: Dp = ScrollEdgeFade, up: Boolean = false): Modifier {
    val p = LocalPalette.current
    val window = LocalWindowInfo.current.containerSize
    val layer = rememberGraphicsLayer()
    var at by remember { mutableStateOf(Offset.Zero) }
    return onGloballyPositioned { at = it.positionInRoot() }.drawBehind {
        layer.compositingStrategy = CompositingStrategy.Offscreen
        // Read outside the recording: in it, density loops back to this scope's own.
        val f = fade.toPx()
        val mask = if (up) Brush.verticalGradient(listOf(Color.Transparent, Color.Black), 0f, f)
        else Brush.verticalGradient(listOf(Color.Black, Color.Transparent), size.height - f, size.height)
        layer.record {
            wallpaper(p, at, window)
            drawRect(mask, blendMode = BlendMode.DstIn)
        }
        drawLayer(layer)
    }
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

fun DrawScope.glassSheen(o: Outline, alpha: Float = 1f) =
    drawOutline(o, Brush.verticalGradient(0f to Color.White.copy(alpha = SHEEN), 0.5f to Color.Transparent, startY = o.bounds.top, endY = o.bounds.bottom), alpha = alpha)

/** The specular rim: brightest along the top, where light falls, dim down the sides, a little back up along the bottom. Half its stroke falls outside the clip. */
fun DrawScope.glassRim(o: Outline, alpha: Float = 1f) = drawOutline(
    o,
    Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.42f), 0.45f to Color.White.copy(alpha = 0.06f), 1f to Color.White.copy(alpha = 0.16f),
        startY = o.bounds.top, endY = o.bounds.bottom,
    ),
    alpha = alpha,
    style = Stroke(2.dp.toPx()),
)

private val BlurRadius = 24.dp

/** A see-through bar's blur: light enough that what passes under it keeps its shapes. */
private val BarBlurRadius = 12.dp

/**
 * Apple's vibrancy, roughly, for labels on a see-through glass bar: a soft glow of [Palette.bg] under them, so they hold while
 * something brighter than the wallpaper and cards [Palette.bar] is fitted to (an accent button, a red pill) passes under. Only where
 * glass floats over a backdrop.
 */
@Composable
fun Modifier.halo(): Modifier {
    val p = LocalPalette.current
    if (!p.glass || LocalBackdrop.current == null) return this
    val layer = rememberGraphicsLayer()
    return drawWithContent {
        val r = HaloRadius.toPx()
        layer.renderEffect = BlurEffect(r, r, TileMode.Decal)
        layer.colorFilter = ColorFilter.tint(p.bg.copy(alpha = HALO), BlendMode.SrcIn)
        // Recorded with room all round, so the glow is not cut off at the label's edges.
        val m = 2 * r
        layer.record(IntSize(ceil(size.width + 2 * m).toInt(), ceil(size.height + 2 * m).toInt())) { translate(m, m) { this@drawWithContent.drawContent() } }
        translate(-m, -m) { drawLayer(layer) }
        drawContent()
    }
}

private val HaloRadius = 4.dp
private const val HALO = 0.8f
private const val BlurScale = 0.25f

/** The wallpaper spread over the window, drawn from [wall] with the window's top left at -[at]. */
private fun DrawScope.wallpaper(p: Palette, at: Offset, window: IntSize) {
    val w = if (window.width > 0) window else IntSize(size.width.toInt(), size.height.toInt())
    drawImage(wall(p, w), dstOffset = IntOffset(-at.x.toInt(), -at.y.toInt()), dstSize = w, filterQuality = FilterQuality.Low)
}

private var wallCache: Triple<Palette, IntSize, ImageBitmap>? = null

/**
 * Three soft glows over the page color, laid out on the window: blue high on the left, violet midway on the right, teal low on the left.
 * Painted once at an eighth of the window's size, since filling three screen-sized gradients each frame cost the GPU 2 ms on a Galaxy S21.
 */
private fun wall(p: Palette, window: IntSize): ImageBitmap {
    wallCache?.let { (cp, cw, img) -> if (cp === p && cw == window) return img }
    val img = ImageBitmap(maxOf(1, window.width / 8), maxOf(1, window.height / 8))
    val w = img.width.toFloat()
    val h = img.height.toFloat()
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w, h)) {
        drawRect(p.bg)
        val spots = listOf(Offset(0.1f * w, 0.12f * h) to 0.95f * w, Offset(0.95f * w, 0.5f * h) to 0.85f * w, Offset(0.15f * w, 0.92f * h) to 0.85f * w)
        for ((glow, spot) in p.glows.zip(spots)) drawRect(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), spot.first, spot.second))
    }
    wallCache = Triple(p, window, img)
    return img
}
