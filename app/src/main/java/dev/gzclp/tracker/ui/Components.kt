package dev.gzclp.tracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import dev.gzclp.tracker.ui.Type.tabular
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize

/** The screen's large title and where its bottom edge is on screen, so [ScreenFrame] can show it inline once it scrolls away. */
class LargeTitle {
    var text by androidx.compose.runtime.mutableStateOf("")
    var bottom by androidx.compose.runtime.mutableFloatStateOf(Float.MAX_VALUE)
}
val LocalLargeTitle = androidx.compose.runtime.staticCompositionLocalOf<LargeTitle?> { null }

/** A [Type.largeTitle] text inside a [ScreenFrame] reports itself as the screen's title. */
@Composable
fun Txt(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.fg,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    val title = if (style == Type.largeTitle) LocalLargeTitle.current else null
    BasicText(
        text,
        if (title == null) modifier else modifier.onGloballyPositioned {
            title.text = text
            title.bottom = it.positionInRoot().y + it.size.height
        },
        style = style.copy(color = color, textAlign = align ?: TextAlign.Unspecified),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun Icon(icon: ImageVector, tint: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) =
    Image(icon, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))

/**
 * The one press surface: gives to 0.97 within a frame and steps its fill, eases back slower,
 * shows a 2dp accent ring on keyboard focus. Every tappable thing in the app is one of these.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.md),
    fill: Color = Color.Transparent,
    pressedFill: Color = LocalPalette.current.fillPressed,
    border: Color? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    repeat: Boolean = false,
    role: Role = Role.Button,
    scale: Float = 0.97f,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPalette.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val click by rememberUpdatedState(onClick)
    // [repeat]: holding past the long-press timeout clicks again every 70 ms; the long press itself swallows the release click.
    if (repeat) LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        delay(400)
        while (true) {
            click()
            delay(70)
        }
    }
    // Loaded sinks a control a little deeper and lets it rise back on a spring that never overshoots: crisp, no bounce.
    val sink = loaded(Effect.Sink) && scale < 1f
    val pressScale by animateFloatAsState(
        if (!pressed) 1f else if (sink) scale - SINK else scale,
        if (pressed) tween(80) else if (sink) androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 900f) else tween(200), label = "press",
    )
    val ring = when {
        focused -> p.accent
        else -> border
    }
    Box(
        modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(shape)
            .background(if (pressed) pressedFill.compositeOver(fill) else fill, shape)
            // In Liquid Glass every filled control is a lit pane, but on a glass sheet, where it would be glass on glass.
            .then(if (p.glass && !LocalOnGlass.current && fill.alpha > 0f && shape != RectangleShape) Modifier.glassEdge(shape) else Modifier)
            .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier)
            .alpha(if (enabled) 1f else 0.4f)
            .combinedClickable(
                interactionSource = source, indication = null, enabled = enabled,
                role = role, onClickLabel = label, onLongClick = if (repeat) ({}) else onLongClick, onClick = onClick,
            )
            .focusable(enabled, source),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** How much deeper Loaded's [Effect.Sink] presses a control. */
private const val SINK = 0.025f

private fun Color.compositeOver(under: Color): Color {
    if (under.alpha == 0f) return this
    val a = alpha + under.alpha * (1 - alpha)
    fun ch(c: Float, u: Float) = (c * alpha + u * under.alpha * (1 - alpha)) / a
    return Color(ch(red, under.red), ch(green, under.green), ch(blue, under.blue), a)
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onSurface: Boolean = false,
    destructive: Boolean = false,
    danger: Boolean = false,
) {
    val p = LocalPalette.current
    val primary = primary && enabled // a disabled primary goes neutral so it never reads as live
    // In Liquid Glass the primary and destructive are stained glass, the rest clear glass; on a glass sheet they are solid, as off glass.
    val onGlass = LocalOnGlass.current
    val fill = when {
        destructive -> if (onGlass) p.red else p.redStain
        primary -> if (onGlass) p.accent else p.stain
        onSurface || onGlass -> p.fill
        p.glass -> p.thumb
        else -> p.card
    }
    // [danger]: a neutral pill with a red label, for a Delete that asks before it deletes.
    val fg = if (destructive) p.onRed else if (primary) p.onAccent else if (danger) p.red else p.fg
    Pressable(
        onClick, modifier.heightIn(min = 52.dp), CircleShape, fill,
        pressedFill = if (primary || destructive) Color.Black.copy(alpha = 0.14f) else p.fillPressed, enabled = enabled,
    ) {
        Row(Modifier.padding(horizontal = Space.xl), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, fg, 18.dp)
                Spacer(Modifier.width(Space.sm))
            }
            Txt(text, Type.headline, color = fg, maxLines = 1)
        }
    }
}

/** A compact pill for secondary in-place actions (Skip rest). */
@Composable
fun SmallPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Pressable(onClick, modifier.heightIn(min = 44.dp), CircleShape, p.fill) {
        Txt(text, Type.subhead.copy(fontWeight = Type.headline.fontWeight), Modifier.padding(horizontal = Space.lg), maxLines = 1)
    }
}

@Composable
fun IconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = LocalPalette.current.fg) =
    Pressable(onClick, modifier.size(44.dp).semantics { contentDescription = label }, CircleShape, label = label, scale = SMALL_PRESS) { Icon(icon, tint, 22.dp) }

/** How far a control under 48 dp gives when pressed: 3 percent of 44 dp is a pixel, too little to see. */
const val SMALL_PRESS = 0.92f

@Composable
fun Hairline(modifier: Modifier = Modifier, start: Dp = 0.dp) =
    Box(modifier.padding(start = start).fillMaxWidth().height(1.dp).background(LocalPalette.current.separator))

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(p.card), content = content)
}

/** Row [i] of [count] in a [Group] split across lazy items: the first and last carry its rounded corners. */
@Composable
fun GroupSlice(i: Int, count: Int, content: @Composable ColumnScope.() -> Unit) {
    val top = if (i == 0) Radius.lg else 0.dp
    val end = if (i == count - 1) Radius.lg else 0.dp
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(top, top, end, end)).background(p.card),
        content = content,
    )
}

@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) =
    Txt(text, Type.footnote, modifier.padding(start = Space.lg, top = Space.xxl, bottom = Space.sm), LocalPalette.current.fg2)

@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.fg2) =
    Txt(text, Type.footnote, modifier.padding(start = Space.lg, end = Space.lg, top = Space.sm), color)

/** A row inside a [Group]; rows below the first get a hairline on the text column. */
@Composable
fun GroupRow(
    first: Boolean,
    onClick: (() -> Unit)? = null,
    inset: Dp = Space.lg,
    content: @Composable RowScope.() -> Unit,
) {
    Column {
        if (!first) Hairline(start = inset)
        val inner: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically, content = content,
            )
        }
        // A full-width row takes the pressed fill only: shrunk, its highlight would stop short of the card.
        if (onClick != null) Pressable(onClick, Modifier.fillMaxWidth(), RectangleShape, scale = 1f) { inner() } else inner()
    }
}

/** [content] opening and closing in place, the rows below it moving with it. */
@Composable
fun Disclosed(open: Boolean, content: @Composable ColumnScope.() -> Unit) {
    // Loaded: the rows rise into place from just under full size, and settle back down as they close.
    val whoosh = loaded(Effect.Whoosh)
    val top = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
    AnimatedVisibility(
        open,
        enter = expandVertically(tween(260, easing = EaseOutQuart)) + fadeIn(tween(260)) +
            (if (whoosh) androidx.compose.animation.scaleIn(riseSpring(), 0.9f, top) else EnterTransition.None),
        exit = shrinkVertically(tween(180, easing = EaseOutQuart)) + fadeOut(tween(120)) +
            (if (whoosh) androidx.compose.animation.scaleOut(tween(180, easing = androidx.compose.animation.core.EaseIn), 0.92f, top) else androidx.compose.animation.ExitTransition.None),
    ) { Column(content = content) }
}

/** A Show more row in a [Group] whose chevron turns down while [open]; [lead] lines its label up with rows that start with a mark. */
@Composable
fun ShowMoreRow(open: Boolean, onClick: () -> Unit, lead: Dp = 0.dp) {
    val p = LocalPalette.current
    GroupRow(first = false, onClick = onClick, inset = Space.lg + lead) {
        Spacer(Modifier.width(lead))
        Txt(if (open) "Show less" else "Show more", Type.body, Modifier.weight(1f), p.accent)
        val turn by animateFloatAsState(if (open) 90f else 0f, tween(220, easing = EaseOutQuart), label = "more")
        Icon(Icons.chevronRight, p.accent, 20.dp, Modifier.rotate(turn))
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, unit: String? = null, repeat: Boolean = false) {
    val p = LocalPalette.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Pressable(onMinus, Modifier.size(64.dp).semantics { contentDescription = "Decrease" }, CircleShape, p.fill, label = "Decrease", repeat = repeat) { Icon(Icons.minus, p.fg, 24.dp) }
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(value, Type.display)
            if (unit != null) Txt(" $unit", Type.title3, Modifier.padding(bottom = 12.dp), p.fg2)
        }
        Pressable(onPlus, Modifier.size(64.dp).semantics { contentDescription = "Increase" }, CircleShape, p.fill, label = "Increase", repeat = repeat) { Icon(Icons.plus, p.fg, 24.dp) }
    }
}

/** A segmented control: one neutral thumb slides under the labels, which cross-fade as it passes ([ThumbRow]). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, pager: PagerState? = null) {
    val p = LocalPalette.current
    // The thumb is a neutral pill one shade off the track, as UISegmentedControl's; its label's weight marks it too, not color alone.
    // In Liquid Glass it turns to clear glass while held ([GlassThumb]); over a backdrop (a header floating over its pages)
    // the track is a glass pane, so what scrolls under it shows through.
    val track = when {
        p.glass && LocalBackdrop.current != null -> Modifier.glassPane(CircleShape, bar = true).background(p.fill)
        p.glass -> Modifier.background(p.fill, CircleShape).glassEdge(CircleShape)
        else -> Modifier.background(p.fill, CircleShape)
    }
    ThumbRow(options.size, selected, onSelect, p.pill, modifier.fillMaxWidth(), pager, track, Modifier.height(36.dp)) { i, thumb ->
        if (thumb) Txt(options[i], Type.subhead.copy(fontWeight = Type.headline.fontWeight), color = p.fg, maxLines = 1)
        else Txt(options[i], Type.subhead, color = p.fg, maxLines = 1)
    }
}

/**
 * [count] equal segments on a bar ([frame], drawn behind them) with one [thumb] pill that slides under them: tap a segment or drag the thumb.
 * Given a [pager], the thumb follows a swipe of the pages, and a tap or a drag of the thumb turns the pages to the segment at once, while the
 * thumb springs after; otherwise it springs to [selected], and a drag selects the segment it is let go over. With [onDown] (the default
 * given a [pager]) a segment is chosen as the finger lands, not as it lifts; a bar inside a scroller leaves it off, so a scroll can start there. Only a finger that lands on the thumb holds or drags it: held, the bar
 * swells a little; held or swiped, the thumb lifts past the bar, springs after the finger, and squashes with its speed ([ThumbMotion]). [cell] sizes a segment; [content] draws segment i on the bar, and
 * again on the thumb when its second argument is true.
 */
@Composable
fun ThumbRow(
    count: Int, selected: Int, onSelect: (Int) -> Unit, thumb: Color, modifier: Modifier, pager: PagerState? = null,
    frame: Modifier, cell: Modifier, onDown: Boolean = pager != null, content: @Composable (Int, Boolean) -> Unit,
) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    val own = remember { Animatable(selected.toFloat()) }
    // The thumb's own spring ([ThumbMotion]) carries it to a new selection: a tap glides it there, a drag or a swipe lands with a small bounce.
    LaunchedEffect(selected) { own.snapTo(selected.toFloat()) }
    var held by remember { androidx.compose.runtime.mutableStateOf(false) }
    val swiped by (pager?.interactionSource ?: remember { MutableInteractionSource() }).collectIsDraggedAsState()
    // Where the thumb heads: the finger while it drags the thumb; the pages' position while a finger swipes them; else the page
    // they are headed to, so after a tap or a fling the thumb leads the pages instead of trailing them.
    var drag by remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    // A tapped segment, aimed at on the tap itself so the thumb moves on the next frame.
    var aim by remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val at = { drag ?: aim?.toFloat() ?: pager?.let { if (swiped) it.position else it.targetPage.toFloat() } ?: own.value }
    fun tap(i: Int) {
        aim = i
        scope.launch {
            try {
                // The page shows on the next frame; only the thumb animates, as a UISegmentedControl's content changes at once.
                pager?.scrollToPage(i)
            } finally {
                if (aim == i) aim = null
            }
        }
    }
    val motion = remember { ThumbMotion(at()) }
    fun pick(i: Int) {
        motion.momentum = false
        if (pager != null) tap(i) else select(i)
    }
    LaunchedEffect(swiped) { if (swiped) motion.momentum = true }
    val lifted = held || swiped
    // Waits, without frames, for the target to move, then chases it until the thumb rests there again.
    LaunchedEffect(motion) {
        while (true) {
            androidx.compose.runtime.snapshotFlow { at() }.first { it != motion.x }
            motion.chase(at)
        }
    }
    LaunchedEffect(lifted) { motion.lift.animateTo(if (lifted) 1f else 0f, ThumbMotion.Lift) }
    val swell by animateFloatAsState(if (held) 1.04f else 1f, androidx.compose.animation.core.spring(1f, androidx.compose.animation.core.Spring.StiffnessLow), label = "swell")
    Box(
        modifier.graphicsLayer { scaleX = swell; scaleY = swell },
    ) {
        // The bar is a layer of its own behind the segments, so a bar that clips (a glass pane) leaves the lifted thumb whole.
        Box(Modifier.matchParentSize().then(frame))
        Box(
            Modifier.fillMaxWidth().padding(Space.xs)
                .pointerInput(pager, count) {
                    val segment = size.width / count.toFloat()
                    val grab = 8.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Only a finger that lands on the thumb holds or drags it; elsewhere on the bar a touch is a tap on that segment.
                        if (abs(down.position.x - (motion.x.coerceIn(0f, count - 1f) + 0.5f) * segment) > segment / 2 + grab) {
                            // Chosen as the finger lands, so a tab or page shows a whole tap sooner; the lift's click then picks it again, a no-op.
                            if (onDown) pick((down.position.x / segment).toInt().coerceIn(0, count - 1))
                            return@awaitEachGesture
                        }
                        held = true
                        try {
                            val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                            if (slop == null) {
                                // Let go without dragging, or something else took the finger: stay held until it lifts.
                                while (currentEvent.changes.any { it.pressed }) awaitPointerEvent()
                                return@awaitEachGesture
                            }
                            motion.momentum = true
                            // The thumb stays under the finger; the pages snap to the segment it is over, so a page held under the finger stays clear.
                            val from = at()
                            var spot = from
                            var shown = pager?.currentPage
                            fun follow(x: Float) {
                                spot = (from + (x - down.position.x) / segment).coerceIn(0f, count - 1f)
                                drag = spot
                                val over = spot.roundToInt()
                                if (pager != null && over != shown) {
                                    shown = over
                                    scope.launch { pager.scrollToPage(over) }
                                }
                            }
                            try {
                                follow(slop.position.x)
                                horizontalDrag(slop.id) { follow(it.position.x); it.consume() }
                            } finally {
                                drag = null
                                val to = spot.roundToInt()
                                if (pager == null) {
                                    scope.launch { own.snapTo(to.toFloat()) }
                                    select(to)
                                } else if (to != shown) scope.launch { pager.scrollToPage(to) }
                            }
                        } finally {
                            held = false
                        }
                    }
                },
        ) {
            // Selected is the segment under the thumb.
            val on by remember(pager) { derivedStateOf { motion.x.roundToInt() } }
            var area by remember { androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
            // The thumb's spring overshoots its target; at an end segment it stops at the bar's end instead of running off it.
            fun pill(s: androidx.compose.ui.geometry.Size) = (s.width / count).let { w -> thumbPill((motion.x.coerceIn(0f, count - 1f) + 0.5f) * w, s.height / 2, w, s.height, motion.lift.value, motion.stretch) }
            // In Liquid Glass a lifted thumb is clear glass: the labels under it show through it, bent at its rim.
            val clear = { if (p.glass) motion.lift.value.coerceIn(0f, 1f) else 0f }
            // The bar's own labels give way under the resting pill, which carries its own, and show through it again as it clears.
            Row(
                Modifier.onSizeChanged { area = it.toSize() }.glassLens({ pill(area) }, clear).drawWithContent {
                    val under = Path().apply { addRoundRect(pill(size)) }
                    clipPath(under, androidx.compose.ui.graphics.ClipOp.Difference) { this@drawWithContent.drawContent() }
                    val a = clear()
                    if (a > 0f) clipPath(under) {
                        drawContext.canvas.saveLayer(under.getBounds(), androidx.compose.ui.graphics.Paint().apply { alpha = a })
                        this@drawWithContent.drawContent()
                        drawContext.canvas.restore()
                    }
                },
            ) {
                repeat(count) { i ->
                    Pressable({ pick(i) }, Modifier.weight(1f).then(cell).semantics { this.selected = i == on }, CircleShape, role = Role.Tab) {
                        Box(Modifier.halo()) { content(i, false) }
                    }
                }
            }
            // The thumb carries its own labels, clipped to its pill, so each label turns as the thumb's edge crosses it.
            // Lifted, it grows past the bar, so nothing here clips to the bar.
            Row(
                Modifier.matchParentSize().clearAndSetSemantics {}.drawWithContent {
                    val pill = pill(size)
                    with(p.thumbPainter) { paint(pill, thumb, p.accent, motion.lift.value.coerceIn(0f, 1f)) }
                    // Its labels fade as it clears, leaving the bar's own labels seen through the glass.
                    val a = 1f - clear()
                    if (a > 0f) clipPath(Path().apply { addRoundRect(pill) }) {
                        drawContext.canvas.saveLayer(pill.let { androidx.compose.ui.geometry.Rect(it.left, it.top, it.right, it.bottom) }, androidx.compose.ui.graphics.Paint().apply { alpha = a })
                        this@drawWithContent.drawContent()
                        drawContext.canvas.restore()
                    }
                },
            ) {
                repeat(count) { i ->
                    Box(Modifier.weight(1f).then(cell), contentAlignment = Alignment.Center) { Box(Modifier.halo()) { content(i, true) } }
                }
            }
        }
    }
}

/** The 28dp white slider knob; where white can't clear 3:1 on the sheet (Light), a fg3 ring outlines it. */
@Composable
fun SliderThumb(modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier.size(28.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape)
            .then(if (thumbRinged(p)) Modifier.border(1.dp, p.fg3, CircleShape) else Modifier),
    )
}

fun thumbRinged(p: Palette) = contrast(Color.White, p.elevated) < 3f || contrast(Color.White, p.bg) < 3f

/** A small round step button, for the − and + beside a value in a row. */
@Composable
fun MiniStep(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Pressable(onClick, Modifier.size(44.dp).semantics { contentDescription = label }, CircleShape, p.fill, enabled = enabled, label = label, scale = SMALL_PRESS) { Icon(icon, p.fg, 18.dp) }
}

/** An on/off switch: accent track when on, the knob slides across. */
@Composable
fun Toggle(on: Boolean, label: String, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val x by androidx.compose.animation.core.animateDpAsState(if (on) 22.dp else 2.dp, tween(200), label = "knob")
    Pressable(
        { onChange(!on) },
        Modifier.size(width = 51.dp, height = 31.dp).semantics { contentDescription = label; toggleableState = ToggleableState(on) },
        CircleShape, if (on) p.accent else p.fillPressed, role = Role.Switch,
    ) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = x).size(27.dp).clip(CircleShape).background(Color.White))
    }
}

private val SheetEase = androidx.compose.animation.core.CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/** A let-go sheet settles at the release's speed: critically damped, so it never bounces past where it lands. */
private val SheetSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)

/** Whether a sheet let go [down] sheet heights below open, moving [speed] heights a second, closes: where its momentum would carry it (UIScrollView's projection) is past halfway. */
internal fun sheetCloses(down: Float, speed: Float): Boolean = down + speed * 0.499f > 0.5f

/** Where a sheet pulled [pulled] heights below open sits: 1:1 downward; pulled up past open, it gives a third as much, as there is nowhere higher to go. */
internal fun sheetBand(pulled: Float): Float = if (pulled < 0f) pulled / 3 else pulled

/** Bottom sheet over a scrim; tapping the scrim dismisses. */
@Composable
fun Sheet(visible: Boolean, onDismiss: () -> Unit, backdrop: Backdrop? = LocalBackdrop.current, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    // Back closes the sheet, never the screen or the app beneath it.
    androidx.activity.compose.BackHandler(visible, onDismiss)
    HoldPages(visible)
    val motion = LocalMotion.current
    // Loaded: the sheet whooshes up toward the viewer from the tap, and a tap or Back drops it back onto the screen with a ripple.
    // One flung away keeps the slide its finger started.
    val whoosh = Effect.Whoosh in motion
    var flung by remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(240)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(p.scrim).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
        }
        AnimatedVisibility(
            visible, Modifier.align(Alignment.BottomCenter),
            // Coming in, the sheet's own [down] carries it, so a finger can catch it on the way; leaving takes about two thirds as long.
            enter = EnterTransition.None,
            exit = if (whoosh && !flung) androidx.compose.animation.scaleOut(DropScale, 0.86f) + fadeOut(tween(220, easing = androidx.compose.animation.core.EaseIn))
            else slideOutVertically(tween(200, easing = SheetEase)) { it },
        ) {
            // How far down the sheet sits, in sheet heights: 1 is out of sight, 0 open. One value the finger, the entrance and the
            // settle all drive, so each picks up from where and how fast the last left it.
            val down = remember { Animatable(1f) }
            val height = remember { androidx.compose.runtime.mutableIntStateOf(1) }
            // While a finger holds it, where the finger has pulled it (null otherwise), drawn on the same frame it moves.
            var pulled by remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
            // How far a whoosh has risen, 0 to 1 (just past 1 at its height); 1 without one. The tap it rises from, and where it lands.
            val rise = remember { Animatable(1f) }
            val from = remember { arrayOf(Offset.Unspecified) }
            val center = remember { arrayOf(Offset.Zero) }
            // Apple's sheet curve: fast out of the gate, long settle. Keyed on [visible] so a sheet reopened as it leaves comes back.
            LaunchedEffect(visible) {
                if (visible && whoosh) {
                    flung = false
                    from[0] = motion.tap
                    down.snapTo(0f)
                    if (rise.value >= 1f) rise.snapTo(0f)
                    motion.feel?.lift()
                    rise.animateTo(1f, riseSpring())
                } else if (visible) down.animateTo(0f, tween(300, easing = SheetEase))
                else if (whoosh && !flung) {
                    delay(DROP_LAND_MS)
                    motion.ripples?.drop(center[0], 0.7f)
                    motion.feel?.land(0.5f)
                }
            }
            Box(
                Modifier.fillMaxWidth()
                    .onGloballyPositioned { center[0] = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) }
                    .then(
                        if (!whoosh) Modifier else Modifier.graphicsLayer {
                            val r = rise.value
                            val s = 0.4f + 0.6f * r
                            scaleX = s
                            scaleY = s
                            alpha = (r * 3f).coerceAtMost(1f)
                            transformOrigin = Motion.pivot(from[0], Offset(0f, motion.window.height - size.height), size)
                            // Lifted, it throws a shadow, gone again once it settles.
                            shadowElevation = 28.dp.toPx() * kotlin.math.sin(Math.PI.toFloat() * r.coerceIn(0f, 1f))
                            shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)
                        },
                    )
                    .layout { m, c ->
                        val pl = m.measure(c)
                        height.intValue = pl.height.coerceAtLeast(1)
                        layout(pl.width, pl.height) { pl.place(0, ((pulled?.let(::sheetBand) ?: down.value) * pl.height).roundToInt()) }
                    }
                    .draggable(
                        androidx.compose.foundation.gestures.rememberDraggableState { d -> pulled = (pulled ?: down.value) + d / height.intValue },
                        androidx.compose.foundation.gestures.Orientation.Vertical,
                        onDragStarted = { down.stop(); pulled = down.value },
                        onDragStopped = { v: Float ->
                            down.snapTo(sheetBand(pulled ?: down.value))
                            pulled = null
                            // Where the release would carry it, as a flick's momentum projects; past halfway it closes.
                            val speed = v / height.intValue
                            if (sheetCloses(down.value, speed)) {
                                flung = true
                                down.animateTo(1f, SheetSpring, speed)
                                onDismiss()
                            } else down.animateTo(0f, SheetSpring, speed)
                        },
                    )
                    // Above open, the sheet's color runs on below its bottom edge, so no gap opens under it.
                    .drawBehind {
                        val gap = -(pulled?.let(::sheetBand) ?: down.value) * size.height
                        if (gap > 0f) drawRect(p.elevated, Offset(0f, size.height - 1f), Size(size.width, gap + 1f))
                    }
                    .then(
                        RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl).let { if (p.glass) Modifier.glassPane(it, backdrop) else Modifier.clip(it).background(p.elevated) },
                    )
                    .clickable(remember { MutableInteractionSource() }, null) {},
            ) {
                androidx.compose.runtime.CompositionLocalProvider(LocalOnGlass provides p.glass) {
                    Column(
                        Modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .imePadding()
                            .padding(Space.xl),
                        verticalArrangement = Arrangement.spacedBy(Space.lg),
                        content = content,
                    )
                }
                Box(Modifier.align(Alignment.TopCenter).padding(top = Space.sm).size(36.dp, 5.dp).clip(CircleShape).background(p.fg3))
            }
        }
    }
}

/**
 * A floating "[text] · Undo" bar that rises while [text] is set; the caller clears it to let it go. It lands on a screen's [BottomBar]
 * pill and covers it, and a tap anywhere on it stays on it, so a tap meant for Undo never reaches the button underneath.
 */
@Composable
fun BoxScope.UndoBar(text: String?, onUndo: () -> Unit, modifier: Modifier = Modifier, backdrop: Backdrop? = LocalBackdrop.current) {
    val p = LocalPalette.current
    // Keeps the last text on screen while the bar slides away.
    val last = remember { arrayOf("") }
    if (text != null) last[0] = text
    AnimatedVisibility(
        text != null, modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(300, easing = SheetEase)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200, easing = SheetEase)) { it } + fadeOut(tween(160)),
    ) {
        Row(
            Modifier.padding(horizontal = Space.lg).fillMaxWidth().shadow(8.dp, CircleShape)
                .then(if (p.glass) Modifier.glassPane(CircleShape, backdrop) else Modifier.clip(CircleShape).background(p.elevated))
                // In a dark theme the bar steps above the cards it floats over, or it melts into them.
                .then(if (p.dark && !p.glass) Modifier.background(p.fillPressed) else Modifier)
                .pointerInput(Unit) { detectTapGestures() }
                .padding(start = Space.xl, end = Space.xs).height(52.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(last[0], Type.body.tabular(), Modifier.weight(1f))
            Pressable(onUndo, Modifier.heightIn(min = 44.dp), shape = CircleShape, label = "Undo") {
                Txt("Undo", Type.headline, Modifier.padding(horizontal = Space.lg), p.accent)
            }
        }
    }
}

/** Two equal pills on a hairline, pinned to the bottom of a screen; in Liquid Glass the screen's frame draws a scroll edge under them instead. */
@Composable
fun BottomBar(content: @Composable RowScope.() -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().then(if (p.glass) Modifier else Modifier.background(p.bg))) {
        // In Liquid Glass the frame's scroll edge stands in for the hairline.
        if (!p.glass) Hairline()
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)).padding(Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.md), content = content,
        )
    }
}

enum class Tab(val label: String) { Workout("Workout"), Upcoming("Upcoming"), Exercises("Exercises"), History("History"), Food("Food") }

/** The tab the app opens on: Exercises, or Workout while a workout or cardio session is running. */
fun homeTab(state: dev.gzclp.tracker.log.AppState): Tab = if (state.active != null || state.cardioActive != null) Tab.Workout else Tab.Exercises

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Workout -> Icons.dumbbell
    Tab.Upcoming -> Icons.calendar
    Tab.Exercises -> Icons.chartLine
    Tab.History -> Icons.history
    Tab.Food -> Icons.utensils
}

/**
 * The app's bottom tab bar: five destinations on a [ThumbRow], the current one on a neutral pill one shade off the bar, its icon and label
 * in the accent (in Liquid Glass in fg, since glass keeps color off its labels); tap a tab, or drag the pill along the bar and let go over one.
 * In Liquid Glass it floats as a see-through capsule, and the held pill turns to clear glass.
 */
@Composable
fun TabBar(selected: Tab, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val p = LocalPalette.current
    val bar: @Composable (Modifier, Modifier) -> Unit = { m, frame ->
        // The current tab's label is heavier as well as brighter, so it reads as selected by more than color.
        ThumbRow(Tab.entries.size, selected.ordinal, { onSelect(Tab.entries[it]) }, if (p.glass) p.pill else p.elevated, m, onDown = true, frame = frame, cell = Modifier.fillMaxHeight()) { i, thumb ->
            val t = Tab.entries[i]
            val c = if (!thumb) p.fg2 else if (p.glass) p.fg else p.accent
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(t.icon(), c, 24.dp)
                Txt(t.label, if (thumb) TabLabel.copy(fontWeight = FontWeight.SemiBold) else TabLabel, color = c, maxLines = 1)
            }
        }
    }
    if (p.glass) bar(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = Space.lg, end = Space.lg, bottom = Space.xs).fillMaxWidth().height(GlassTabBar),
        Modifier.shadow(8.dp, CircleShape).glassPane(CircleShape, bar = true),
    ) else Column(modifier.fillMaxWidth().background(p.bg)) {
        Hairline()
        // Inset so the end tabs' pills keep the screen's 16 dp gutter.
        bar(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Space.md).height(TabBarRow), Modifier)
    }
}

/** A tab's label: iOS's 10 pt medium, small enough that the longest ("Upcoming") clears the rounded ends of its pill at 115% text size. */
private val TabLabel = Type.caption.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)

/** The floating glass tab bar's height, above its [Space.xs] margin and the navigation bar. */
val GlassTabBar = 60.dp

/** The flat tab bar's row, under its hairline. */
private val TabBarRow = 56.dp

/** The tab bar's height above the navigation bar, as the screens above it see it; 0 when there is none. */
@Composable
fun tabBarHeight(shown: Boolean): Dp = if (!shown) 0.dp else if (LocalPalette.current.glass) GlassTabBar + Space.xs else TabBarRow + 1.dp

/** A tab's screen above the tab bar; the bar steps aside while the keyboard is up. In Liquid Glass the screen runs under the floating bar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabFrame(tab: Tab, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    val bar = !WindowInsets.isImeVisible
    if (!LocalPalette.current.glass) return Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) { content() }
        if (bar) TabBar(tab, onSelect = onSelect)
    }
    val b = rememberBackdrop()
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.navigationBars).backdropSource(b)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalBottomInset provides if (bar) GlassTabBar + Space.xs + nav else 0.dp, content = content)
        }
        if (bar) androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides b) { TabBar(tab, Modifier.align(Alignment.BottomCenter), onSelect) }
    }
}

/**
 * Whether the caller's tab is the one showing; always true outside [TabScreens]. Kept tabs stay composed, so a handler only the shown tab
 * should own waits on this. A state, not a value: a changed local recomposes everything under it, which a tab switch must not.
 */
val LocalTabShown = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.runtime.State<Boolean>> { androidx.compose.runtime.mutableStateOf(true) }

/**
 * Every tab's screen, kept composed as a UITabBarController keeps its tabs: switching to one costs no composition and finds it as it was
 * left. The other tabs are composed one a frame once the first has opened, so a first visit is as quick. Only [tab]'s screen is placed and drawn.
 */
@Composable
fun TabScreens(tab: Tab, content: @Composable (Tab) -> Unit) {
    val kept = remember { androidx.compose.runtime.mutableStateListOf(tab) }
    if (tab !in kept) kept += tab
    // Read only where it matters (placing, and each tab's shown state), so a switch recomposes nothing in the screens.
    val current = androidx.compose.runtime.rememberUpdatedState(tab)
    // 400 ms clears the first screen's opening frames, as [Pages] waits for its neighbors.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        for (t in Tab.entries) if (t !in kept) androidx.compose.runtime.withFrameNanos { kept += t }
    }
    androidx.compose.ui.layout.Layout({
        for (t in kept) androidx.compose.runtime.key(t) {
            val shown = remember { androidx.compose.runtime.derivedStateOf { current.value == t } }
            KeptTab(t, shown, content)
        }
    }) { all, c ->
        // Each kept screen is measured, so a switch finds it laid out too; unchanged ones cost nothing to measure again.
        val screens = all.map { it.layoutId to it.measure(c) }
        layout(c.maxWidth, c.maxHeight) { screens.first { it.first == current.value }.second.place(0, 0) }
    }
}

/**
 * The tab bar over every tab's screen ([TabFrame] around [TabScreens]). The tab is read here alone, so a switch recomposes this and the bar:
 * the caller, and the [content] it built, are left as they were, and no kept screen runs again.
 */
@Composable
fun TabHost(tab: () -> Tab, onSelect: (Tab) -> Unit, content: @Composable (Tab) -> Unit) {
    val t = tab()
    TabFrame(t, onSelect) { TabScreens(t, content) }
}

/** One kept tab: skipped when [TabScreens] recomposes, as its arguments stay the same. Out of sight, it is out of the accessibility tree too. */
@Composable
private fun KeptTab(t: Tab, shown: androidx.compose.runtime.State<Boolean>, content: @Composable (Tab) -> Unit) {
    val hidden = if (shown.value) Modifier else Modifier.clearAndSetSemantics {}
    Box(Modifier.layoutId(t).fillMaxSize().then(hidden), propagateMinConstraints = true) {
        androidx.compose.runtime.CompositionLocalProvider(LocalTabShown provides shown) { content(t) }
    }
}
