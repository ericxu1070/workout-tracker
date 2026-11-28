package dev.gzclp.tracker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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

/** The page under everything: [Palette.bg], in Liquid Glass too, which keeps the theme's own flat page. */
@Composable
fun Modifier.page(): Modifier = background(LocalPalette.current.bg)

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
    val blur = rememberGraphicsLayer()
    val frame = rememberGraphicsLayer()
    val lens = if (bar && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) remember { RuntimeShader(LENS) } else null
    var at by remember { mutableStateOf(Offset.Zero) }
    val tint = if (bar) p.bar else p.chrome
    return onGloballyPositioned { at = it.positionInRoot() }.clip(shape).drawWithContent {
        if (b != null) {
            // The page is flat, so it needs no blur; only the content over it does.
            drawRect(p.bg)
            // Blurred at a quarter of its size and scaled back up: a blur this wide looks the same and costs a sixteenth.
            val s = BlurScale
            val radius = (if (bar) BarBlurRadius else BlurRadius).toPx() * s
            // A lens records a rim's width more all round, so its edge gathers in what lies just outside the pane.
            val m = if (lens != null) LensRim.toPx() else 0f
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
            val area = IntSize(ceil((size.width + 2 * m) * s).toInt(), ceil((size.height + 2 * m) * s).toInt())
            blur.record(area) {
                scale(s, Offset.Zero) { translate(b.origin.x - at.x + m, b.origin.y - at.y + m) { drawLayer(b.layer) } }
            }
            // The effect runs only over the clip it is drawn under, and the lens reads past the pane's edge, where under the pane's own
            // clip a GPU leaves stale texture (a band of colored noise along the switch's bottom). Drawn first into an offscreen frame of its
            // whole size, it runs over all of it, and only then is clipped to the pane.
            frame.compositingStrategy = CompositingStrategy.Offscreen
            frame.pivotOffset = Offset.Zero
            frame.scaleX = 1 / s
            frame.scaleY = 1 / s
            frame.record(area) { drawLayer(blur) }
            translate(-m, -m) { drawLayer(frame) }
            drawRect(tint)
        } else drawRect(tint.copy(alpha = 1f))
        drawContent()
    }.then(if (shape == RectangleShape) Modifier else Modifier.glassEdge(shape))
}

/**
 * iOS's scroll edge behind chrome floating over a page, in place of a hairline: the page's color, solid but for [fade] along its
 * bottom edge (its top edge if [up]) where it fades out, so what scrolls under the chrome's text is hidden and slips out of sight softly.
 * Drawn straight onto the page: masked in a layer of its own, it came out empty after a pushed screen's rise or drop, and the list showed through the bar.
 */
@Composable
fun Modifier.scrollEdge(fade: Dp = ScrollEdgeFade, up: Boolean = false): Modifier {
    val bg = LocalPalette.current.bg
    return drawBehind {
        val f = fade.toPx()
        val clear = bg.copy(alpha = 0f)
        if (up) drawRect(Brush.verticalGradient(listOf(clear, bg), 0f, f)) else drawRect(Brush.verticalGradient(listOf(bg, clear), size.height - f, size.height))
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

/** A see-through bar's blur: what passes under it keeps its shapes and colors but not its words. */
private val BarBlurRadius = 20.dp

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
