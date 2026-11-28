package dev.gzclp.tracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.currentCoroutineContext
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * How a theme paints the segmented control's thumb, a pill [lift] of the way (0 to 1) from resting in the bar to held up
 * under the finger; a held glass thumb glows in [glow].
 */
fun interface ThumbPainter {
    fun DrawScope.paint(pill: RoundRect, color: Color, glow: Color, lift: Float)
}

/** A solid pill whether resting or lifted. */
val SolidThumb = ThumbPainter { pill, color, _, _ -> drawPath(Path().apply { addRoundRect(pill) }, color) }

/**
 * Liquid Glass: at rest a pill of [color]; lifted, it turns to clear glass as a held iOS 26 thumb does, a faint frost lit by
 * Liquid Glass's sheen and rim ([glassSheen], [glassRim]), the labels under it bent by [glassLens], a glow of [glow] around it.
 */
val GlassThumb = ThumbPainter { pill, color, glow, lift ->
    val outline = Outline.Rounded(pill)
    if (lift > 0f) {
        // A circle stretched to the pill's proportions, so the glow hugs it evenly all round.
        val c = Offset(pill.left + pill.width / 2, pill.top + pill.height / 2)
        val r = pill.height / 2 * GLOW_REACH
        scale(pill.width / pill.height, 1f, c) { drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = GLOW * lift), Color.Transparent), c, r), r, c) }
    }
    drawOutline(outline, lerp(color, GlassFill, lift))
    glassSheen(outline, lift)
    glassRim(outline, lift)
}

/** A held glass thumb's glow: its glow color at this alpha at its center, fading out to [GLOW_REACH] times its own size. */
private const val GLOW = 0.35f
private const val GLOW_REACH = 2f

/** A fully lifted glass thumb: a faint frost, so the labels it bends read through it. */
private val GlassFill = Color.White.copy(alpha = 0.14f)

/** The stretch a thumb moving at [velocity] segments a second wants: 0 at rest, rising toward [MAX_STRETCH] the faster it goes, either way. */
fun stretchFor(velocity: Float): Float = MAX_STRETCH * tanh(abs(velocity) / 5f)

/**
 * The thumb's width and height scale for [stretch], keeping its area: above 0 it is short and wide (moving fast),
 * below 0 tall and narrow (the bounce back after it stops).
 */
fun thumbScale(stretch: Float): Pair<Float, Float> {
    val s = 1f + stretch.coerceIn(-0.4f, MAX_STRETCH)
    return s to 1f / s
}

const val MAX_STRETCH = 0.18f

/** How much a lifted thumb grows: mostly taller, well past the bar's height, and only a little wider, so it stands out from the bar. */
const val LIFT_GROW_X = 0.06f
const val LIFT_GROW_Y = 0.6f

/** The pill for a thumb centered at [cx], [cy] in a [w] by [h] segment, at [lift] and [stretch]. */
fun thumbPill(cx: Float, cy: Float, w: Float, h: Float, lift: Float, stretch: Float): RoundRect {
    val (sx, sy) = thumbScale(stretch)
    val pw = w * (1f + LIFT_GROW_X * lift) * sx
    val ph = h * (1f + LIFT_GROW_Y * lift) * sy
    return RoundRect(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2, CornerRadius(ph / 2))
}

/**
 * The system's animator duration scale, as Compose's own animations read it: 0 when Remove animations is on.
 * Hand-stepped frame loops use it so they cut when everything else cuts.
 */
suspend fun motionScale(): Float = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f

/** One step of a damped spring ([stiffness], [damping] ratio) pulling [x], moving at [v], toward [to] over [dt] seconds. */
fun springStep(x: Float, v: Float, to: Float, dt: Float, stiffness: Float, damping: Float): Pair<Float, Float> {
    var nx = x
    var nv = v
    val c = 2f * damping * sqrt(stiffness)
    val h = dt / 4
    repeat(4) {
        nv += (-stiffness * (nx - to) - c * nv) * h
        nx += nv * h
    }
    return nx to nv
}

/**
 * A segmented thumb's motion: [x] follows its target on a stiff spring. Moved by a gesture with [momentum] (a drag or a swipe), the spring is
 * lightly underdamped, so the thumb arrives just after the finger with a small bounce, and [stretch] chases [stretchFor] its speed on a bouncier
 * one, so stopping overshoots into tall and narrow. Moved by a tap, it glides there on a softer spring, critically damped, no squash, landing
 * in about 380 ms so the tap reads as a deliberate slide. [lift] grows it while held, and while a tap carries it.
 * Both springs step in one frame loop ([chase]): restarting an animation each frame would hold the thumb still while its target moves.
 */
class ThumbMotion(start: Float) {
    var x by mutableFloatStateOf(start)
        private set
    var stretch by mutableFloatStateOf(0f)
        private set
    private var v = 0f
    private var sv = 0f
    val lift = Animatable(0f)

    /** Whether a drag or a swipe moved the thumb, so it may bounce; a tap clears it. */
    var momentum = false

    /** Advances [dt] seconds toward [to]; true once it rests there. */
    fun step(to: Float, dt: Float): Boolean {
        springStep(x, v, to, dt, if (momentum) FOLLOW_STIFFNESS else TAP_STIFFNESS, if (momentum) FOLLOW_DAMPING else 1f).let { (a, b) -> x = a; v = b }
        springStep(stretch, sv, if (momentum) stretchFor(v) else 0f, dt, SQUASH_STIFFNESS, if (momentum) SQUASH_DAMPING else 1f).let { (a, b) -> stretch = a; sv = b }
        val rest = abs(x - to) < 1e-3f && abs(v) < 1e-2f && abs(stretch) < 1e-3f && abs(sv) < 1e-2f
        if (rest) {
            x = to; v = 0f; stretch = 0f; sv = 0f
        }
        return rest
    }

    /**
     * Steps every frame toward [to], wherever it moves meanwhile, until the thumb rests on it. Paced by the system's animation
     * scale ([motionScale]); with animations removed it lands at once.
     */
    suspend fun chase(to: () -> Float) {
        val scale = motionScale()
        if (scale == 0f) {
            x = to(); v = 0f; stretch = 0f; sv = 0f
            return
        }
        var last = 0L
        do {
            val now = withFrameNanos { it }
            val dt = (if (last == 0L) 1 / 60f else ((now - last) / 1e9f).coerceAtMost(1 / 20f)) / scale
            last = now
        } while (!step(to(), dt))
    }

    companion object {
        // Stiff and lightly underdamped, as iOS's segmented thumbs: about 35 ms behind a moving finger, a small overshoot on arrival.
        const val FOLLOW_STIFFNESS = 1600f
        const val FOLLOW_DAMPING = 0.7f
        // A tap's glide: within 2% of the segment at about 380 ms, a whole-segment move or a longer one alike.
        const val TAP_STIFFNESS = 250f
        const val SQUASH_STIFFNESS = Spring.StiffnessMedium
        const val SQUASH_DAMPING = 0.4f
        val Lift = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
    }
}
