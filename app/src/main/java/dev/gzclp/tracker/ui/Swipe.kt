package dev.gzclp.tracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The red action's width when the row rests open. */
private val ACTION = 76.dp

/** Past this share of the row's width, letting go deletes. */
private const val FULL = 0.5f

/** Critically damped, so the row settles without a bounce. */
private val SwipeSpring = spring<Float>(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)

/**
 * A row that slides left to show a red trash action on its trailing edge, as in iOS lists. Tapping the action, or
 * dragging past half the row and letting go, calls [onDelete], which asks first. While [confirming] the row stays
 * open; when the question goes away it springs closed. A rightward drag on a closed row is left to the pages around it.
 */
@Composable
fun SwipeToDelete(
    label: String, confirming: Boolean, onDelete: () -> Unit, modifier: Modifier = Modifier, shape: Shape = RectangleShape,
    content: @Composable () -> Unit,
) {
    val p = LocalPalette.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val reveal = with(LocalDensity.current) { ACTION.toPx() }
    val delete by rememberUpdatedState(onDelete)
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(confirming) {
        if (confirming) asked = true
        else if (asked) {
            asked = false
            x.animateTo(0f, SwipeSpring)
        }
    }
    val open = x.value < 0f
    // An open row holds the pages still, so closing it never turns the page.
    HoldPages(open || confirming)
    fun ask() {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        delete()
    }
    fun settle(velocity: Float) = scope.launch {
        // Where a flick would carry the row, as a scroll decelerates, decides open or closed.
        val projected = x.value + velocity * 0.499f
        when {
            -x.value > width * FULL -> {
                x.animateTo(-width.toFloat(), SwipeSpring, velocity)
                ask()
            }
            -projected > reveal / 2 -> x.animateTo(-reveal, SwipeSpring, velocity)
            else -> x.animateTo(0f, SwipeSpring, velocity)
        }
    }
    Box(
        modifier.clip(shape).onSizeChanged { width = it.width }
            .semantics { customActions = listOf(CustomAccessibilityAction(label) { delete(); true }) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val wasOpen = x.value < 0f
                    var over = 0f
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, o ->
                        // Only an opening drag, or any drag on an open row, is ours; the rest goes to the pager.
                        if (wasOpen || o < 0f) {
                            over = o
                            change.consume()
                        }
                    } ?: return@awaitEachGesture
                    scope.launch { x.stop() }
                    val tracker = VelocityTracker().apply { addPointerInputChange(drag) }
                    var raw = x.value + over
                    var past = -raw > width * FULL
                    horizontalDrag(drag.id) { c ->
                        tracker.addPointerInputChange(c)
                        raw = (raw + c.positionChange().x).coerceAtLeast(-width.toFloat())
                        // Past closed the row resists, giving a third of the finger's travel.
                        val shown = if (raw > 0f) raw / 3f else raw
                        scope.launch { x.snapTo(shown) }
                        val now = -raw > width * FULL
                        if (now != past) {
                            past = now
                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                        c.consume()
                    }
                    settle(tracker.calculateVelocity().x)
                }
            },
    ) {
        val shown = -x.value
        // The row's own height: a row in a scrolling column has no height to fill.
        if (shown > 0f) Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterEnd) {
            Box(
                Modifier.fillMaxHeight().width(with(LocalDensity.current) { shown.toDp() }).background(p.red)
                    .pointerInput(Unit) { detectTapGestures { ask() } },
                contentAlignment = Alignment.CenterStart,
            ) {
                // Centered in the resting action, then riding the row's edge as the swipe runs past it.
                val icon = with(LocalDensity.current) { 22.dp.toPx() }
                Icon(Icons.trash, p.onRed, 22.dp, Modifier.offset { IntOffset(((minOf(shown, reveal) - icon) / 2).roundToInt(), 0) })
            }
        }
        Box(Modifier.offset { IntOffset(x.value.roundToInt(), 0) }) {
            content()
            // Tapping an open row closes it rather than opening what it shows.
            if (open && !confirming) Box(
                Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { scope.launch { x.animateTo(0f, SwipeSpring) } } },
            )
        }
    }
}
