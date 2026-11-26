package dev.gzclp.tracker.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Pushed screens over the tabs. A push slides in from the trailing edge and a pop slides back out, as on iOS, the screen
 * underneath staying put. The system Back swipe drives the pop: the screen follows the finger onto [backTo], letting go
 * finishes it with [onBack], and a cancelled swipe puts it back. [canPop] says whether Back pops a screen at all.
 */
@Composable
fun <T> BackStack(
    route: T,
    depth: (T) -> Int,
    canPop: () -> Boolean,
    backTo: () -> T,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val routes = remember { SeekableTransitionState(route) }
    // Set before a swipe's first frame, so its pop is linear in the finger's progress rather than eased in time.
    var swiping by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    val whoosh = Effect.Whoosh in motion
    LaunchedEffect(route) {
        // Loaded: a screen popped by a tap or Back lands on the one under it, and the landing ripples out from the middle.
        if (whoosh && !swiping && depth(route) < depth(routes.currentState)) launch {
            delay(DROP_LAND_MS)
            val w = motion.window
            motion.ripples?.drop(Offset(w.width / 2f, w.height / 2f), 0.5f)
            motion.feel?.land(0.4f)
        } else if (whoosh && depth(route) > depth(routes.currentState)) motion.feel?.lift()
        routes.animateTo(route)
        swiping = false
    }
    PopHandler(canPop, { swiping = true }, { swiping = false }, routes, backTo, onBack)
    rememberTransition(routes, label = "route").AnimatedContent(
        modifier,
        transitionSpec = {
            val push = depth(targetState) > depth(initialState)
            val ease = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
            when {
                swiping -> EnterTransition.None togetherWith slideOutHorizontally(tween(300, easing = LinearEasing)) { it }
                // Loaded: the pushed screen whooshes up from the tap toward the viewer, and drops back onto the screen under it.
                whoosh && push -> scaleIn(riseSpring(), 0.4f, Motion.pivot(motion.tap, Offset.Zero, motion.window.toSize())) + fadeIn(tween(150)) togetherWith fadeOut(tween(200))
                whoosh -> fadeIn(tween(200)) togetherWith scaleOut(DropScale, 0.86f) + fadeOut(tween(220, easing = EaseIn))
                push -> slideInHorizontally(tween(300, easing = ease)) { it } togetherWith fadeOut(tween(200))
                else -> fadeIn(tween(200)) togetherWith slideOutHorizontally(tween(200, easing = ease)) { it }
            }.apply { targetContentZIndex = if (push) 1f else -1f }
        },
    ) { content(it) }
}

/** The swipe's handler, in its own scope: [canPop] reads the app's state, and a change to it should recompose only this. */
@Composable
private fun <T> PopHandler(
    canPop: () -> Boolean, onSwipe: () -> Unit, onCancel: () -> Unit,
    routes: SeekableTransitionState<T>, backTo: () -> T, onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    PredictiveBackHandler(canPop()) { progress ->
        val from = routes.currentState
        val to = backTo()
        try {
            progress.collect {
                onSwipe()
                routes.seekTo(it.progress, to)
            }
        } catch (e: CancellationException) {
            // The system has already eased the swipe's progress back to 0, so this only settles it. Animating to [from] here
            // would replay the pair as a push. This coroutine is cancelled, so it runs in the screen's own scope.
            scope.launch {
                routes.snapTo(from)
                onCancel()
            }
            throw e
        }
        onBack()
    }
}
