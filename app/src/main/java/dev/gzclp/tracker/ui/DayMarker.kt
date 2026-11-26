package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.rotate
import dev.gzclp.tracker.log.Kind
import dev.gzclp.tracker.log.Segment
import kotlin.math.abs

/** Lifting, cardio and stretching marks, and the day number written on them. */
data class KindColors(val lift: Color, val cardio: Color, val stretch: Color, val on: Color) {
    fun of(kind: Kind) = when (kind) {
        Kind.LIFT -> lift
        Kind.CARDIO -> cardio
        Kind.STRETCH -> stretch
    }
}

/** Blue, orange and pink: hues the common color blindnesses still tell apart. */
private val HUES = listOf(215f, 28f, 330f)

/**
 * The three kinds at fixed hues, each at the lightness nearest a vivid middle where the day number (the darker of the
 * theme's background and foreground, else the lighter) reads at 4.5:1 and the mark clears 3:1 on the page and on a
 * card. The 4.6 below leaves room for desaturating and blending to round. Falls back to the foreground for all three, the old single-color mark, if a theme allows no hue.
 */
internal fun kindColors(bg: Color, elevated: Color, fg: Color): KindColors {
    for (on in listOf(bg, fg).sortedBy { it.luminance() }) {
        val fits = HUES.map { hue ->
            (0..100).map { it / 100f }
                .filter { l -> Color.hsl(hue, 0.8f, l).let { contrast(it, on) >= 4.6f && contrast(it, bg) >= 3f && contrast(it, elevated) >= 3f } }
                .minByOrNull { abs(it - 0.6f) }?.let { Color.hsl(hue, 0.8f, it) }
        }
        if (fits.all { it != null }) return KindColors(fits[0]!!, fits[1]!!, fits[2]!!, on)
    }
    return KindColors(fg, fg, fg, bg)
}

/** The faintest a kind gets: a light day keeps this much of its hue, enough for blue and pink to stay apart under protanopia. */
private const val FLOOR = 0.45f

/**
 * [c] with its saturation scaled by [intensity] (0 to 1, floored at [FLOOR]): mixed in linear light with the gray of
 * the same luminance, so text on it reads exactly as well at every intensity.
 */
internal fun atIntensity(c: Color, intensity: Float): Color = mixLinear(gray(c), c, FLOOR + (1 - FLOOR) * intensity.coerceIn(0f, 1f))

private fun gray(c: Color): Color {
    val l = c.convert(ColorSpaces.LinearSrgb).let { 0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue }
    return Color(l, l, l, c.alpha, ColorSpaces.LinearSrgb).convert(ColorSpaces.Srgb)
}

/** Mixes in linear light, so a blend never dips darker than both ends the way an sRGB blend does. */
internal fun mixLinear(a: Color, b: Color, t: Float): Color {
    val x = a.convert(ColorSpaces.LinearSrgb)
    val y = b.convert(ColorSpaces.LinearSrgb)
    fun m(p: Float, q: Float) = p + (q - p) * t
    return Color(m(x.red, y.red), m(x.green, y.green), m(x.blue, y.blue), m(x.alpha, y.alpha), ColorSpaces.LinearSrgb).convert(ColorSpaces.Srgb)
}

/** The widest blend between two kinds, as a fraction of the circle on each side of their border. */
private const val BLEND = 0.08f

/**
 * Sweep-gradient stops for a day of several kinds: each kind's arc is its share of the day, starting at 12 o'clock in
 * [Kind] order, with a soft blend across each border. Position 0 is the middle of the first arc, so the sweep's seam
 * falls inside solid color; draw it turned by [sweepTurn].
 */
internal fun sweepStops(segments: List<Segment>, colors: List<Color>): List<Pair<Float, Color>> {
    val n = segments.size
    val stops = mutableListOf(0f to colors[0])
    var at = segments[0].share / 2
    for (i in 0 until n) {
        val j = (i + 1) % n
        val w = minOf(BLEND, segments[i].share / 2, segments[j].share / 2)
        for (k in 0..4) stops += (at - w + 2 * w * k / 4) to mixLinear(colors[i], colors[j], k / 4f)
        if (j != 0) at += segments[j].share
    }
    stops += 1f to colors[0]
    return stops.map { (at, c) -> at.coerceIn(0f, 1f) to c }
}

/** Degrees to turn [sweepStops]'s sweep (which starts at 3 o'clock) so the first arc starts at 12. */
internal fun sweepTurn(segments: List<Segment>) = -90f + segments[0].share * 180f

/** "Lifting 52 min, cardio 30 min" for screen readers. */
fun dayLabel(segments: List<Segment>) =
    segments.joinToString(", ") { it.kind.label + " " + Fmt.minutes(it.sec) }.lowercase().replaceFirstChar { it.uppercase() }

/** A day's marker fill: one kind fills it, several share it like a pie, each as deep as that kind's load. */
fun Modifier.dayMarker(segments: List<Segment>, kinds: KindColors): Modifier {
    val colors = segments.map { atIntensity(kinds.of(it.kind), it.intensity) }
    if (segments.size == 1) return background(colors[0], CircleShape)
    val brush = Brush.sweepGradient(*sweepStops(segments, colors).toTypedArray())
    val turn = sweepTurn(segments)
    return drawBehind { rotate(turn) { drawCircle(brush) } }
}
