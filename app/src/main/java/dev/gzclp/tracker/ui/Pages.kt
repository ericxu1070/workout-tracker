package dev.gzclp.tracker.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/** The spring every segment change rides, tapped, swiped or dragged: damped so it settles with at most a slight overshoot. */
val SegmentSpring: AnimationSpec<Float> = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/** The pager whose pages hold the composition; a [WorkoutSwitch] in a page follows it. */
val LocalPages = compositionLocalOf<PagerState?> { null }

/** Which of the [LocalPages] holds the composition. */
private val LocalPage = compositionLocalOf { 0 }

/** Whether the caller's page is the one the pages came to rest on; always true outside [Pages]. Offscreen pages stay composed, so work only the shown page needs waits on this. */
@Composable
fun settledHere(): Boolean = LocalPages.current?.let { it.settledPage == LocalPage.current } ?: true

/** Where the pager stands, in pages: 1.5 is halfway from the second page to the third. */
val PagerState.position: Float get() = currentPage + currentPageOffsetFraction

/** A pager kept in step with [selected]: when [selected] changes the pages follow on [SegmentSpring]; a swipe reports the page it settles on to [onSelect]. */
@Composable
fun rememberPages(selected: Int, count: Int, onSelect: (Int) -> Unit): PagerState {
    val pager = rememberPagerState(selected) { count }
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { if (it != current) select(it) } }
    LaunchedEffect(selected) { if (pager.targetPage != selected) pager.animateScrollToPage(selected, animationSpec = SegmentSpring) }
    return pager
}

/** Swipeable pages that settle on [SegmentSpring]; a [Segmented] given the same [pager] follows the swipe. [beyond] pages on each side stay composed. */
@Composable
fun Pages(pager: PagerState, modifier: Modifier = Modifier, beyond: Int = 0, page: @Composable (Int) -> Unit) =
    remember { mutableIntStateOf(0) }.let { holds ->
        CompositionLocalProvider(LocalPages provides pager, LocalPageHolds provides holds) {
            HorizontalPager(
                pager, modifier, verticalAlignment = Alignment.Top, userScrollEnabled = holds.intValue == 0, beyondViewportPageCount = beyond,
                flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = SegmentSpring),
            ) { CompositionLocalProvider(LocalPage provides it) { page(it) } }
        }
    }

/**
 * Holds [page]'s copy of a control still while the page slides. Each page draws only the part over itself,
 * so the copies on two neighboring pages meet as one control. The control must be centered in its page.
 */
fun Modifier.pinned(pager: PagerState, page: Int): Modifier {
    fun shift() = (pager.position - page) * (pager.layoutInfo.pageSize + pager.layoutInfo.pageSpacing)
    return graphicsLayer { translationX = shift() }.drawWithContent {
        val w = pager.layoutInfo.pageSize.toFloat()
        val left = -(w - size.width) / 2 - shift()
        clipRect(left, 0f, left + w, size.height) { this@drawWithContent.drawContent() }
    }
}

/** Widens a pager past its column's side padding [x] so pages slide edge to edge; each page pads itself back in. */
fun Modifier.bleed(x: Dp) = layout { m, c ->
    val extra = (x * 2).roundToPx()
    val placeable = m.measure(c.copy(minWidth = c.minWidth + extra, maxWidth = c.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** How many overlays are open over the [Pages] around them; while any is, the pages don't swipe. */
val LocalPageHolds = compositionLocalOf { mutableIntStateOf(0) }

/** An overlay open over a page ([on]) holds the pages still, so a drag on it never slides the page away beneath it. */
@Composable
fun HoldPages(on: Boolean) {
    val holds = LocalPageHolds.current
    DisposableEffect(on, holds) {
        if (on) holds.intValue++
        onDispose { if (on) holds.intValue-- }
    }
}
