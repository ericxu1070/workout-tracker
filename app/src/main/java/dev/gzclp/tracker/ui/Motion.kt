package dev.gzclp.tracker.ui

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.pow

/**
 * What Loaded adds over Performance, one entry per effect, so any of them can be dropped by deleting its entry and the code that
 * asks for it. Performance has none of them; the squash-and-grow selector pill is in both.
 */
enum class Effect {
    /** Sheets, pushed screens and opened rows rise toward the viewer from the tap, and drop back with a ripple. */
    Whoosh,
    /** A control sinks deeper under the finger and comes back without overshoot. */
    Sink,
    /** A logged set's check lands with a squash and a ring along its row. */
    PlateDrop,
    /** Haptics with mass: a rise on open, a thud on landing, ticks after a ripple. */
    Feel,
    /** The rest bar is a channel of liquid that sloshes when the set lands and trembles in its last seconds. */
    RestLiquid,
    /** Starting a workout raises its exercises; ending it lands with the big ripple, as does a finished stretch routine. */
    Bookends,
    /** Logged water pours from the cup into the day's ring. */
    WaterPour,
    /** A set that beats the lift's best lands harder, with a splash. */
    PrSplash,
    /** A new theme or motion mode spreads from the tap as a wave of color. */
    PaletteWipe,
    /** Swiped pages cross-fade as they slide: the one leaving dims as the one arriving comes up. */
    PageFade,
}

/** Every Loaded effect; the place to drop one. */
val LoadedEffects: Set<Effect> = Effect.entries.toSet()

/** The effects on: [LoadedEffects] in Loaded, none in Performance or while the system's Remove animations is on. */
/**
 * Whether Remove animations is off, read as Compose reads it (an unset scale is 1). ValueAnimator's own scale can sit at 0 after
 * the setting is deleted while Compose still animates, which turned Loaded off on a Galaxy S21 for no reason.
 */
fun animatorsEnabled(resolver: android.content.ContentResolver): Boolean =
    android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

fun effectsFor(loaded: Boolean, animatorsOn: Boolean): Set<Effect> = if (loaded && animatorsOn) LoadedEffects else emptySet()

/**
 * The app's motion: which [Effect]s are on, the [ripples] they drop into, the last [tap], and [feel] for their haptics.
 * [water] is the screen's one surface, kept across a change of mode so rings already spreading run on to their end.
 */
class Motion(val effects: Set<Effect>, val water: RippleField = RippleField(), val feel: Feel? = null) {
    /** Where an effect drops a ring: [water], or nowhere with every effect off. */
    val ripples: RippleField? get() = water.takeIf { effects.isNotEmpty() }

    /** Where the last finger came down, in the window; a whoosh rises from it. */
    var tap: Offset = Offset.Unspecified
    var window: IntSize = IntSize.Zero

    operator fun contains(e: Effect) = e in effects

    companion object {
        val Off = Motion(emptySet())

        /** [tap] as a pivot for a box at [topLeft] of [size], all in the window: what rises from the tap scales about it. Its center without a tap. */
        fun pivot(tap: Offset, topLeft: Offset, size: androidx.compose.ui.geometry.Size): TransformOrigin {
            if (tap == Offset.Unspecified || size.width <= 0f || size.height <= 0f) return TransformOrigin.Center
            return TransformOrigin((tap.x - topLeft.x) / size.width, (tap.y - topLeft.y) / size.height)
        }
    }
}

val LocalMotion = staticCompositionLocalOf { Motion.Off }

/** Whether Loaded's [e] is on here. */
@Composable
@ReadOnlyComposable
fun loaded(e: Effect): Boolean = e in LocalMotion.current

/** A whoosh's rise: fast out of the gate and just past full size, as something lifted toward the viewer. */
fun <T> riseSpring() = spring<T>(dampingRatio = 0.72f, stiffness = 380f)

/** A drop back onto the screen: falling, it shrinks past where it lands, then settles a hair back up as it fades. */
val DropScale = androidx.compose.animation.core.keyframes<Float> {
    durationMillis = 260
    0.86f at 200 using androidx.compose.animation.core.EaseIn
}

/** How long a drop takes to land, when its ripple starts. */
const val DROP_LAND_MS = 200L

/** The big landing at [at], a workout's end: the strongest ripple, and a thud with its tail. */
fun Motion.touchdown(at: Offset, strength: Float = 1f) {
    if (Effect.Bookends !in effects) return
    ripples?.drop(if (at == Offset.Unspecified) Offset(window.width / 2f, window.height / 2f) else at, strength)
    feel?.touchdown()
}

/** A landing's spring: quick, with one small overshoot, for things set down rather than tapped. */
fun <T> landSpring() = spring<T>(dampingRatio = 0.6f, stiffness = 500f)

/** Watches every finger that comes down, without taking it, so a whoosh knows where it was tapped. */
fun Modifier.watchTaps(m: Motion): Modifier = onSizeChanged { m.window = it }.pointerInput(m) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            e.changes.firstOrNull { it.pressed && !it.previousPressed }?.let { m.tap = it.position }
        }
    }
}

/**
 * The screen as a liquid surface: each [drop] sends one slow, wide ring out across it. While any ring is alive the content under
 * [ripples] is bent by an AGSL shader (API 33 and up) or overdrawn with faint rings (below); with none, nothing runs and no frame
 * is drawn for it.
 */
class RippleField {
    internal class Wave(val at: Offset, val strength: Float) {
        var start = 0L
    }

    internal val waves = mutableStateListOf<Wave>()
    internal var now by mutableLongStateOf(0L)

    /** A ring from [at] in the window; [strength] 1 is a workout's end. A drop right after a stronger one is the same impact. */
    fun drop(at: Offset, strength: Float = 1f) {
        if (waves.any { (it.start == 0L || now - it.start < SAME_IMPACT_NS) && it.strength >= strength }) return
        // Never reached at one ring per [SAME_IMPACT_NS]: a ring is gone before a seventh can start, so none is cut off mid-spread.
        if (waves.size >= MAX_WAVES) waves.removeAt(0)
        waves += Wave(at, strength)
    }

    internal fun tick(t: Long) {
        now = t
        waves.forEach { if (it.start == 0L) it.start = t }
        waves.removeAll { t - it.start > DURATION_NS }
    }

    /** How far wave [w] has run, 0 to 1. */
    internal fun progress(w: Wave) = if (w.start == 0L) 0f else ((now - w.start).toFloat() / DURATION_NS).coerceIn(0f, 1f)

    companion object {
        const val DURATION_NS = 1_800_000_000L
        private const val SAME_IMPACT_NS = 300_000_000L
        const val MAX_WAVES = 6
    }
}

/** Runs [f]'s clock while it has a ring, and stops when the last one has spread out. */
@Composable
fun RippleClock(f: RippleField) {
    if (f.waves.isEmpty()) return
    LaunchedEffect(f) { while (true) withFrameNanos(f::tick) }
}

/** How far a ring has spread at [t] (0 to 1): fast out, slowing as it widens, like a ring on water. */
internal fun rippleRadius(t: Float, reach: Float) = reach * (1f - (1f - t).pow(1.6f))

/** A ring's height at [t]: full at the impact, gone at the end. */
internal fun rippleAmp(t: Float, strength: Float) = strength * (1f - t).pow(1.5f)

/** Bends this content with [f]'s rings while any are alive. */
@Composable
fun Modifier.ripples(f: RippleField?): Modifier {
    if (f == null) return this
    val fallbackColor = LocalPalette.current.fg
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return drawWithContent {
        drawContent()
        f.now // read so each frame redraws
        val reach = hypot(size.width, size.height)
        for (w in f.waves) {
            val t = f.progress(w)
            drawCircle(fallbackColor.copy(alpha = 0.16f * rippleAmp(t, w.strength)), rippleRadius(t, reach), w.at, style = Stroke(2.dp.toPx()))
        }
    }
    val shader = remember { RuntimeShader(RIPPLE) }
    return graphicsLayer {
        f.now
        val live = f.waves.toList()
        renderEffect = if (live.isEmpty()) null else shader.run {
            val reach = hypot(size.width, size.height)
            for (i in 0 until RippleField.MAX_WAVES) {
                val w = live.getOrNull(i)
                val t = w?.let(f::progress) ?: 1f
                setFloatUniform("w$i", w?.at?.x ?: 0f, w?.at?.y ?: 0f, rippleRadius(t, reach), if (w == null) 0f else RippleHeight.toPx() * rippleAmp(t, w.strength))
            }
            setFloatUniform("band", RippleBand.toPx())
            setFloatUniform("size", size.width, size.height)
            RenderEffect.createRuntimeShaderEffect(this, "content").asComposeRenderEffect()
        }
    }
}

/** How far the tallest ring moves what is under it. Small enough that text bends but stays readable. */
private val RippleHeight = 6.dp

/** A ring's wavelength: wide and slow. */
private val RippleBand = 110.dp

/**
 * Up to [RippleField.MAX_WAVES] rings, each [w] = (center x, center y, radius, height) in pixels. Near a ring's crest the content is displaced along the
 * ring's normal by one and a half sine periods under a Gaussian, and lit a little on the side that faces up, as a swell catches light.
 */
val RIPPLE = """
uniform shader content;
${(0 until RippleField.MAX_WAVES).joinToString("\n") { "uniform float4 w$it;" }}
uniform float band;
uniform float2 size;

float3 ring(float2 p, float4 w) {
    if (w.w <= 0.0) return float3(0.0);
    float2 d = p - w.xy;
    float r = length(d) + 0.001;
    float x = (r - w.z) / band;
    float env = exp(-x * x * 2.5);
    float s = sin(x * 6.2832);
    return float3(d / r * s * env * w.w, cos(x * 6.2832) * env * w.w);
}

half4 main(float2 p) {
    float3 a = ${(0 until RippleField.MAX_WAVES).joinToString(" + ") { "ring(p, w$it)" }};
    half4 c = content.eval(clamp(p + a.xy, float2(0.0), size - 1.0));
    return half4(c.rgb + half(a.z * 0.006) * c.a, c.a);
}
"""

/**
 * Haptics with mass: [lift] as something rises, [land] as it lands (heavier for heavier things), [rings] as a ripple spreads.
 * Uses the vibrator's primitives where the motor has them, the view's standard haptics otherwise, and nothing with touch feedback off.
 */
class Feel(private val context: Context, private val view: View) {
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val primitives = vibrator?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_THUD, VibrationEffect.Composition.PRIMITIVE_TICK,
    ) == true

    private fun enabled() = Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private fun play(fallback: Int, build: VibrationEffect.Composition.() -> Unit) {
        if (!enabled()) return
        val v = vibrator
        if (!primitives || v == null) {
            view.performHapticFeedback(fallback)
            return
        }
        val effect = VibrationEffect.startComposition().apply(build).compose()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        else @Suppress("DEPRECATION") v.vibrate(effect)
    }

    fun lift() = play(HapticFeedbackConstants.CONTEXT_CLICK) { addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.35f) }

    /** [weight] 0 to 1: a set is light, a sheet medium, a workout's end heavy. */
    fun land(weight: Float) = play(if (weight > 0.6f) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK) {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, weight.coerceIn(0.1f, 1f))
    }

    /** Two landings, the second a beat after: a record. */
    fun double() = play(HapticFeedbackConstants.CONFIRM) {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 0.8f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 90)
    }

    /** The big landing: a full thud, then the ripple's tail of fading ticks. */
    fun touchdown() = play(HapticFeedbackConstants.CONFIRM) {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 140)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 180)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.15f, 220)
    }

    /** A ring's tail: three fading ticks. */
    fun rings() = play(HapticFeedbackConstants.CLOCK_TICK) {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f, 120)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.3f, 160)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.15f, 200)
    }
}
