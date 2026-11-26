package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.log.Kind
import dev.gzclp.tracker.log.Segment
import dev.gzclp.tracker.ui.Type.tabular
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
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

/** "Lifting 52 min, cardio 30 min" for screen readers. */
fun dayLabel(segments: List<Segment>) =
    segments.joinToString(", ") { it.kind.label + " " + Fmt.minutes(it.sec) }.lowercase().replaceFirstChar { it.uppercase() }

/** Mon, Tue: the column headers, Monday first in Week and Month alike. */
internal fun weekday(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, Locale.US)

/**
 * A calendar day, the same in Week and Month: a plain number, today's on an accent-filled circle, and under it one 4 dp
 * dot per kind the day held, in [Kind] order. Opens the day when [onClick] is given.
 */
@Composable
fun DayCell(date: LocalDate, today: LocalDate, mix: List<Segment>?, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val isToday = date == today
    val desc = listOfNotNull("Today".takeIf { isToday }, mix?.let { "${dayLabel(it)} on ${Fmt.shortDate(date)}" }).joinToString(", ")
    val body: @Composable BoxScope.() -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(36.dp).clip(CircleShape).then(if (isToday) Modifier.background(p.accent) else Modifier), contentAlignment = Alignment.Center) {
                Txt(
                    "${date.dayOfMonth}", Type.subhead.tabular().copy(fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal),
                    color = when {
                        isToday -> p.onAccent
                        date > today -> p.fg2
                        else -> p.fg
                    },
                )
            }
            Spacer(Modifier.height(Space.xs))
            Row(Modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                mix?.forEach { Box(Modifier.size(4.dp).clip(CircleShape).background(p.kinds.of(it.kind))) }
            }
        }
    }
    val m = if (desc.isEmpty()) modifier else modifier.semantics { contentDescription = desc }
    if (onClick != null) Pressable(onClick, m, label = desc, scale = SMALL_PRESS, content = body)
    else Box(m, contentAlignment = Alignment.Center, content = body)
}
