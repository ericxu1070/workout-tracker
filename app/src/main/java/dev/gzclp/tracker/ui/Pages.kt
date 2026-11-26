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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** The spring a fling's pages land on, and a pager-less switch's selection: the same as [springTo]'s. */
val SegmentSpring: AnimationSpec<Float> = spring(dampingRatio = PAGE_DAMPING, stiffness = PAGE_STIFFNESS)

/** The pager whose pages hold the composition. */
val LocalPages = compositionLocalOf<PagerState?> { null }

/** Which of the [LocalPages] holds the composition. */
private val LocalPage = compositionLocalOf { 0 }

/** Whether the caller's page is the one the pages came to rest on; always true outside [Pages]. Offscreen pages stay composed, so work only the shown page needs waits on this. */
@Composable
fun settledHere(): Boolean = LocalPages.current?.let { it.settledPage == LocalPage.current } ?: true

/** Where the pager stands, in pages: 1.5 is halfway from the second page to the third. */
val PagerState.position: Float get() = currentPage + currentPageOffsetFraction

/**
 * Turns to [page] on a stiff spring stepped each frame from the first one, so the pages move on the frame after a tap;
 * an animation started the usual way spends its first frame standing still. Settles in about 180 ms.
 */
suspend fun PagerState.springTo(page: Int) = scroll {
    val span = (layoutInfo.pageSize + layoutInfo.pageSpacing).toFloat()
    var x = position
    var v = 0f
    var last = 0L
    while (true) {
        val now = withFrameNanos { it }
        val dt = if (last == 0L) 1 / 60f else ((now - last) / 1e9f).coerceAtMost(1 / 20f)
        last = now
        val (nx, nv) = springStep(x, v, page.toFloat(), dt, PAGE_STIFFNESS, PAGE_DAMPING)
        x = nx
        v = nv
        val done = abs(x - page) < 1e-3f && abs(v) < 1e-2f
        scrollBy(((if (done) page.toFloat() else x) - position) * span)
        if (done) break
    }
}

const val PAGE_STIFFNESS = 1500f
const val PAGE_DAMPING = 0.9f

/** A pager kept in step with [selected]: when [selected] changes the pages follow ([springTo]); a swipe reports the page it settles on to [onSelect]. */
@Composable
fun rememberPages(selected: Int, count: Int, onSelect: (Int) -> Unit): PagerState {
    val pager = rememberPagerState(selected) { count }
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { if (it != current) select(it) } }
    // Never fights a scroll already under way: its settled page is reported when it lands.
    LaunchedEffect(selected) { if (!pager.isScrollInProgress && pager.currentPage != selected) pager.springTo(selected) }
    return pager
}

/**
 * A screen of swipeable pages under a fixed [header] (which holds a [Segmented] given the same [pager]); the pages settle on a spring.
 * The header stays put while the pages slide or scroll beneath it. [beyond] pages on each side stay composed,
 * added one a frame once the pages have opened, so opening costs only the page shown.
 */
@Composable
fun Pages(pager: PagerState, beyond: Int = 0, header: @Composable ColumnScope.() -> Unit, page: @Composable (Int) -> Unit) {
    val p = LocalPalette.current
    val holds = remember { mutableIntStateOf(0) }
    var near by remember { mutableIntStateOf(0) }
    // 400 ms clears the 300 ms screen transition, so the neighbors' first frames don't land on it; a swipe or drag before then can't wait.
    if (beyond > 0) LaunchedEffect(beyond) {
        withTimeoutOrNull(400) { snapshotFlow { pager.isScrollInProgress }.first { it } }
        for (n in 1..beyond) withFrameNanos { near = n }
    }
    // Drawn over the pages, so a lifted thumb can grow past the header's bottom edge.
    val head: @Composable (Modifier) -> Unit = { m ->
        Box(m.fillMaxWidth().zIndex(1f)) {
            Column(Modifier.padding(horizontal = Space.lg), content = header)
            // An overlay held open over a page dims and holds the header too, as it did when the header sat in the page.
            val dim by animateFloatAsState(if (holds.intValue > 0) 1f else 0f, tween(if (holds.intValue > 0) 240 else 160), label = "dim")
            if (dim > 0f) Box(Modifier.matchParentSize().graphicsLayer { alpha = dim }.background(p.scrim).clickable(remember { MutableInteractionSource() }, null) {})
        }
    }
    val pages: @Composable (Modifier) -> Unit = { m ->
        CompositionLocalProvider(LocalPages provides pager, LocalPageHolds provides holds) {
            HorizontalPager(
                pager, m, verticalAlignment = Alignment.Top, userScrollEnabled = holds.intValue == 0, beyondViewportPageCount = near,
                flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = SegmentSpring),
            ) { i ->
                // Only the page past halfway blurs: the one being left stays sharp until the middle, then the arriving one is.
                // Clipped: a blur spreads past its layer, and would paint the page's background over the header.
                // A page wholly out of view, kept composed for a tap, isn't drawn: kept pages would otherwise cost every frame of the screen.
                Box(
                    Modifier.drawWithContent { if (abs(pager.position - i) < 1f) drawContent() }
                        .graphicsLayer { renderEffect = pageBlur(abs(pager.position - i), PageBlur.toPx()); clip = true },
                ) {
                    CompositionLocalProvider(LocalPage provides i) { page(i) }
                }
            }
        }
    }
    // The header takes the status bar inset, so the pages' frames start right under it.
    val frame = Modifier.fillMaxSize().page().windowInsetsPadding(WindowInsets.statusBars)
    val b = rememberBackdrop()
    if (b == null) Column(frame) {
        head(Modifier)
        pages(Modifier.weight(1f))
    } else SubcomposeLayout(frame) { c ->
        // In Liquid Glass the pages run on up under the header, which floats over them: what scrolls under its switch shows through the
        // glass, and above that the wallpaper hides it. Measured first, the header's height pads the pages' start from the first frame.
        val heads = subcompose(0) { CompositionLocalProvider(LocalBackdrop provides b) { head(Modifier.scrollEdge()) } }.map { it.measure(c.copy(minHeight = 0)) }
        val top = heads.maxOfOrNull { it.height } ?: 0
        val body = subcompose(1) { CompositionLocalProvider(LocalTopInset provides top.toDp()) { pages(Modifier.fillMaxSize().backdropSource(b)) } }.map { it.measure(c) }
        layout(c.maxWidth, c.maxHeight) {
            body.forEach { it.place(0, 0) }
            heads.forEach { it.place(0, 0) }
        }
    }
}

/** The most a page blurs, a whole page away from resting. */
val PageBlur = 24.dp

/**
 * The blur for a page [away] pages from resting, at most [max] px: none up to halfway, so of two pages at most one is blurred and the
 * middle of a swipe stays readable, then easing in (smoothstep) to [max] by three quarters of the way.
 */
fun pageBlur(away: Float, max: Float): RenderEffect? {
    val t = ((away - 0.5f) / 0.25f).coerceIn(0f, 1f)
    val r = max * t * t * (3 - 2 * t)
    return if (r < 0.5f) null else BlurEffect(r, r)
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
