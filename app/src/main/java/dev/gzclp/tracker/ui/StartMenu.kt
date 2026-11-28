package dev.gzclp.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** One of a [StartMenu]'s ways to log, and the line that tells it from the others. */
class StartOption(val title: String, val hint: String, val go: () -> Unit)

/**
 * One button that opens into the ways to start or log a workout. The button sits in a screen's bottom bar ([StartMenuButton]);
 * its options ([StartMenuOptions]) are drawn over the screen, in a pane that grows out of the button's own bounds.
 */
class StartMenu {
    var open by mutableStateOf(false)

    /** How far open: 0 closed, 1 open, just past 1 at the height of a Loaded rise. */
    internal val t = Animatable(0f)

    /** On screen: open, or still closing. */
    internal val shown by derivedStateOf { open || t.value > 0f }

    /** The closed button in the window, which the pane grows out of, and the backdrop its bar floats over in Liquid Glass. */
    internal var anchor by mutableStateOf(Rect.Zero)
    internal var backdrop: Backdrop? = null
    internal val button = FocusRequester()

    /** Closes with nothing picked: keyboard focus goes back to the button. */
    fun close() {
        if (!open) return
        open = false
        button.requestFocus()
    }
}

/** The closed button's label, and the open menu's name to TalkBack. */
const val START_OR_LOG = "Start or log"

/** The closed [menu]: a primary pill whose chevron points the way it opens. */
@Composable
fun StartMenuButton(menu: StartMenu, modifier: Modifier = Modifier) {
    val backdrop = LocalBackdrop.current
    SideEffect { menu.backdrop = backdrop }
    Pill(
        { menu.open = true }, LocalPalette.current.stain,
        modifier.onGloballyPositioned { menu.anchor = Rect(it.positionInRoot(), it.size.toSize()) }
            .focusRequester(menu.button).focusProperties { canFocus = !menu.open }
            // Open, the pane is drawn over it, its first option in this very place.
            .then(if (menu.shown) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier.semantics { stateDescription = "Collapsed" }),
    ) { ClosedLabel() }
}

/**
 * [menu]'s options, over the screen they are declared after: [options] top to bottom, then [primary] as a pill where the closed
 * button was, under the thumb that opened it. The pane's sides and bottom edge are the button's and its top edge rises, on
 * [Disclosed]'s clock; in Loaded it rises on [riseSpring] and, closed with nothing picked, lands back in the bar with a ripple.
 * The page dims and holds still under it, as under a [Sheet]; a tap on the page or Back closes it.
 */
@Composable
fun StartMenuOptions(menu: StartMenu, primary: String, onPrimary: () -> Unit, options: List<StartOption>) {
    val p = LocalPalette.current
    val motion = LocalMotion.current
    val whoosh = Effect.Whoosh in motion
    // A kept tab stays composed: only the shown one owns Back, and leaving the tab closes the menu.
    val tabShown = LocalTabShown.current
    BackHandler(menu.open && tabShown.value, menu::close)
    LaunchedEffect(tabShown.value) { if (!tabShown.value) menu.open = false }
    HoldPages(menu.open)
    var picked by remember { mutableStateOf(false) }
    LaunchedEffect(menu.open) {
        if (menu.open) {
            picked = false
            if (whoosh) motion.feel?.lift()
            menu.t.animateTo(1f, if (whoosh) riseSpring() else tween(260, easing = EaseOutQuart))
        } else if (menu.t.value > 0f) {
            menu.t.animateTo(0f, tween(180, easing = EaseOutQuart))
            // What was picked brings its own landing: a sheet, or the workout.
            if (whoosh && !picked) {
                motion.ripples?.drop(menu.anchor.center, 0.35f)
                motion.feel?.land(0.3f)
            }
        }
    }
    // [Pages] dims its header on this same clock while the pages are held, so the page and the header above it go dark as one.
    val dim = remember { Animatable(0f) }
    LaunchedEffect(menu.open) { dim.animateTo(if (menu.open) 1f else 0f, tween(if (menu.open || picked) 240 else 160)) }
    val dimmed by remember { derivedStateOf { dim.value > 0f } }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        // In Liquid Glass the page runs on up under that header, which dims itself: the scrim stops short of it, or it would be dimmed twice.
        if (dimmed) Box(
            Modifier.fillMaxSize().padding(top = LocalTopInset.current).graphicsLayer {
                // A picked option's sheet brings its own scrim up over this one, on the same 240 ms ([Sheet]): this one goes by as much
                // as that one has come, so the page under the two stays as dark. Each fading its own way, the page flashed half again as bright.
                val s = p.scrim.alpha
                alpha = if (picked) (1f - (1f - s) / (1f - s * (1f - dim.value))) / s else dim.value
            }.background(p.scrim).pointerInput(menu) { detectTapGestures { menu.close() } },
        )
        if (menu.shown) Pane(menu, { origin }, primary, onPrimary, options) { go ->
            // Still closing, it takes no second pick.
            if (menu.open) {
                picked = true
                menu.open = false
                go()
            }
        }
    }
}

@Composable
private fun Pane(menu: StartMenu, origin: () -> Offset, primary: String, onPrimary: () -> Unit, options: List<StartOption>, pick: (() -> Unit) -> Unit) {
    val p = LocalPalette.current
    val t = { menu.t.value.coerceAtLeast(0f) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    // It picks up the press the closed button was let go from, and eases back to full size as that button would have ([Pressable]).
    // With the system's Remove animations on there is no press to pick up: it is drawn closed, then open, and nothing in between.
    val sink = loaded(Effect.Sink)
    val scope = rememberCoroutineScope()
    val settle = remember { Animatable(if (scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f) 1f else if (sink) 0.945f else 0.97f) }
    LaunchedEffect(Unit) { settle.animateTo(1f, if (sink) spring(dampingRatio = 1f, stiffness = 900f) else tween(200)) }
    // No taller than the button, the pane is the button's own pill; grown, its corners sit a pad's width outside the pill's.
    val shape = RoundedCornerShape(26.dp + PanePad)
    val top = LocalTopInset.current
    Layout(
        {
            CompositionLocalProvider(LocalOnGlass provides p.glass) {
                Column {
                    // On a screen too short for them all (a phone on its side), the options scroll above the pill.
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = Space.sm).graphicsLayer { alpha = ((t() - 0.3f) / 0.5f).coerceIn(0f, 1f) }) {
                        options.forEachIndexed { i, o ->
                            GroupRow(first = i == 0, onClick = { pick(o.go) }) {
                                Column(Modifier.weight(1f)) {
                                    Txt(o.title, Type.headline)
                                    Txt(o.hint, Type.footnote, color = p.fg2)
                                }
                            }
                        }
                    }
                    Pill(
                        { pick(onPrimary) },
                        // On the pane's glass the pill is solid, as on a glass sheet: the closed button's stain deepens into it on the way open.
                        if (p.glass) lerp(p.stain, p.accent, t().coerceAtMost(1f)) else p.accent,
                        // The pill draws in from the pane's edges as the pane grows around it.
                        Modifier.layout { m, c ->
                            val pad = (PanePad.toPx() * t()).roundToInt()
                            val pl = m.measure(c.offset(-2 * pad, 0))
                            layout(c.maxWidth, pl.height + 2 * pad) { pl.place(pad, pad) }
                        }.fillMaxWidth().focusRequester(first).semantics { traversalIndex = -1f },
                    ) {
                        // The closed label gives way to the option's name: out over the first half of the way open, in over the second.
                        ClosedLabel(Modifier.graphicsLayer { alpha = (1f - 2f * t()).coerceIn(0f, 1f) }.clearAndSetSemantics {})
                        Txt(primary, Type.headline, Modifier.padding(horizontal = Space.xl).graphicsLayer { alpha = (2f * t() - 1f).coerceIn(0f, 1f) }, p.onAccent, maxLines = 1)
                    }
                }
            }
        },
        Modifier
            // On the closed button: its sides and bottom edge, and as much above as the pane has grown, short of the header over the page.
            .layout { m, c ->
                val a = menu.anchor.translate(-origin())
                val room = (a.bottom - top.toPx() - Space.lg.toPx()).coerceAtLeast(a.height).roundToInt()
                val pl = m.measure(Constraints(a.width.roundToInt(), a.width.roundToInt(), 0, room))
                layout(c.maxWidth, c.maxHeight) { pl.place(a.left.roundToInt(), a.bottom.roundToInt() - pl.height) }
            }
            .graphicsLayer {
                scaleX = settle.value
                scaleY = settle.value
                transformOrigin = TransformOrigin(0.5f, 1f - menu.anchor.height / 2f / size.height.coerceAtLeast(1f))
            }
            .then(if (p.glass) Modifier.glassPane(shape, menu.backdrop) else Modifier.clip(shape).background(p.elevated))
            // A tap between its options stays on it.
            .pointerInput(Unit) { detectTapGestures() }
            .semantics {
                paneTitle = START_OR_LOG
                isTraversalGroup = true
                dismiss { menu.close(); true }
            },
    ) { ms, c ->
        val pl = ms.single().measure(c.copy(minHeight = 0))
        val h = lerp(menu.anchor.height.roundToInt(), pl.height, t()).coerceAtMost(c.maxHeight)
        layout(pl.width, h) { pl.place(0, h - pl.height) }
    }
}

/** The pane's padding round its pill. */
private val PanePad = Space.sm

/** A primary [PillButton]'s surface, around [content] that changes as the menu opens. */
@Composable
private fun Pill(onClick: () -> Unit, fill: Color, modifier: Modifier, content: @Composable BoxScope.() -> Unit) =
    Pressable(onClick, modifier.heightIn(min = 52.dp), CircleShape, fill, pressedFill = Color.Black.copy(alpha = 0.14f), content = content)

@Composable
private fun ClosedLabel(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier.padding(horizontal = Space.xl), verticalAlignment = Alignment.CenterVertically) {
        Txt(START_OR_LOG, Type.headline, color = p.onAccent, maxLines = 1)
        Spacer(Modifier.width(Space.sm))
        // Points up, the way the menu opens.
        Icon(Icons.chevronRight, p.onAccent, 18.dp, Modifier.rotate(-90f))
    }
}
