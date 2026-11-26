package dev.gzclp.tracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Where the current row of a [StepPillHost] sits, in the host's own coordinates; null while no row is current. */
class StepPillState(internal val surface: Color) {
    internal var host: LayoutCoordinates? = null
    var target by mutableStateOf<Rect?>(null)
        internal set
    internal var owner: Any? = null
}

private val LocalStepPill = staticCompositionLocalOf<StepPillState?> { null }

/** The spring the pill rides from one row to the next: underdamped, so it overshoots by about a tenth of the move and settles back. */
private val PillSpring = spring<Rect>(dampingRatio = 0.6f, stiffness = 300f)

/**
 * Draws one accent-tinted pill behind whichever row inside it carries [stepPill] with `on = true`.
 * As the current row changes, the pill springs to it; with no current row it fades out where it was.
 * [surface] is what the host sits on, so controls on the pill can be filled opaque: see [pillFill].
 */
@Composable
fun StepPillHost(surface: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    val state = remember(surface) { StepPillState(surface) }
    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val shown = remember { Animatable(0f) }
    val inflate = with(LocalDensity.current) { Space.xs.toPx() }
    // 12dp past the row each side: on a card's 16dp padding, a 10dp corner nests inside the card's 14dp one.
    val bleed = with(LocalDensity.current) { Space.md.toPx() }
    val corner = with(LocalDensity.current) { Radius.md.toPx() }
    val maxStretch = with(LocalDensity.current) { Space.sm.toPx() }
    LaunchedEffect(state) {
        snapshotFlow { state.target }.collect { t ->
            if (t == null) { launch { shown.animateTo(0f) }; return@collect }
            // From hidden, the pill appears on its row; only a pill already showing travels.
            if (shown.value == 0f) rect.snapTo(t) else launch { rect.animateTo(t, PillSpring) }
            launch { shown.animateTo(1f) }
        }
    }
    Box(
        modifier.onPlaced { state.host = it }.drawBehind {
            val a = shown.value
            if (a == 0f) return@drawBehind
            val r = rect.value
            // In flight it stretches along the move with its speed, up to 8dp, and is back to its row's height at rest.
            val stretch = (abs(rect.velocity.top) * 0.01f).coerceAtMost(maxStretch)
            val h = r.height + 2 * inflate + stretch
            drawRoundRect(
                p.stepPill, Offset(r.left - bleed, r.top - inflate - stretch / 2), Size(r.width + 2 * bleed, h),
                CornerRadius(corner), alpha = a,
            )
        },
    ) { CompositionLocalProvider(LocalStepPill provides state, content = content) }
}

/** A control's fill on the current row: [Palette.fill] made opaque over the host's surface, so the pill tint never shows through it. */
@Composable
fun pillFill(on: Boolean): Color {
    val p = LocalPalette.current
    val surface = LocalStepPill.current?.surface
    return if (on && surface != null) p.fill.compositeOver(surface) else p.fill
}

/** Marks this row as the one the enclosing [StepPillHost] highlights while [on]; outside a host it does nothing. */
fun Modifier.stepPill(on: Boolean): Modifier = composed {
    val state = LocalStepPill.current ?: return@composed this
    val token = remember { Any() }
    DisposableEffect(on) {
        onDispose { if (state.owner === token) { state.owner = null; state.target = null } }
    }
    if (!on) this else semantics { selected = true }.onPlaced { c ->
        val host = state.host ?: return@onPlaced
        state.owner = token
        state.target = host.localBoundingBoxOf(c, clipBounds = false)
    }
}
