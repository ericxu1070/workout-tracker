package dev.gzclp.tracker.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.ui.Type.tabular
import kotlin.math.abs

/** The point nearest [x] among [count] points placed by [xAt], or null when none is within [reach]. */
fun nearestPoint(x: Float, count: Int, reach: Float = Float.POSITIVE_INFINITY, xAt: (Int) -> Float): Int? =
    (0 until count).minByOrNull { abs(xAt(it) - x) }?.takeIf { abs(xAt(it) - x) <= reach }

/**
 * Touch for every chart, writing the picked point's index to [pick]. A tap pins the nearest point; tapping it
 * again or empty plot clears it. A sideways drag scrubs with a haptic tick at each new point and clears on
 * release; a vertical drag is left to the page's scroll. [xAt] places point i for a chart [width] px wide.
 * [describe] is the whole chart for screen readers and [read] the picked point's value.
 */
fun Modifier.chartPick(
    pick: MutableState<Int?>,
    count: Int,
    describe: String,
    read: (Int) -> String,
    xAt: Density.(i: Int, width: Float) -> Float,
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val place by rememberUpdatedState(xAt)
    semantics {
        contentDescription = describe
        pick.value?.takeIf { it < count }?.let { stateDescription = read(it) }
    }.pointerInput(count) {
        detectTapGestures { o ->
            val i = nearestPoint(o.x, count, 24.dp.toPx()) { place(it, size.width.toFloat()) }
            pick.value = if (i == pick.value) null else i
            if (i != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }.pointerInput(count) {
        fun scrub(x: Float) {
            val i = nearestPoint(x, count) { place(it, size.width.toFloat()) }
            if (i != pick.value) {
                pick.value = i
                haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        }
        detectHorizontalDragGestures(
            onDragStart = { scrub(it.x) },
            onDragEnd = { pick.value = null },
            onDragCancel = { pick.value = null },
        ) { change, _ ->
            change.consume()
            scrub(change.position.x)
        }
    }
}

/** The picked point's readout at the top of the chart, beside a mark centered on [x] and [half] px wide each side: right of it, or left when it would run off. */
fun DrawScope.callout(tm: TextMeasurer, p: Palette, x: Float, title: String, values: List<Pair<String, Color>>, half: Float = 0f) {
    val rows = listOf(tm.measure(title, Type.caption.tabular().copy(color = p.fg2))) +
        values.map { (text, color) -> tm.measure(text, Type.footnote.tabular().copy(color = color)) }
    val padX = 8.dp.toPx()
    val padY = 4.dp.toPx()
    val gap = 8.dp.toPx()
    val w = rows.maxOf { it.size.width } + padX * 2
    val h = rows.sumOf { it.size.height } + padY * 2
    val left = if (x + half + gap + w <= size.width) x + half + gap else (x - half - gap - w).coerceAtLeast(0f)
    drawRoundRect(p.elevated, Offset(left, 0f), Size(w, h), CornerRadius(Radius.sm.toPx()))
    var y = padY
    rows.forEach {
        drawText(it, topLeft = Offset(left + padX, y))
        y += it.size.height
    }
}
