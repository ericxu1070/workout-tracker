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
import androidx.compose.ui.draw.shadow
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
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(if (pressed) 80 else 200), label = "press")
    val ring = when {
        focused -> p.accent
        else -> border
    }
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
    val fg = if (destructive) p.onRed else if (primary) p.onAccent else p.fg
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
    Pressable(onClick, modifier.size(44.dp).semantics { contentDescription = label }, CircleShape, label = label) { Icon(icon, tint, 22.dp) }

@Composable
fun Hairline(modifier: Modifier = Modifier, start: Dp = 0.dp) =
    Box(modifier.padding(start = start).fillMaxWidth().height(1.dp).background(LocalPalette.current.separator))

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(p.card).cardRim(p, Radius.lg), content = content)
}

/** Row [i] of [count] in a [Group] split across lazy items: the first and last carry its rounded corners. */
@Composable
fun GroupSlice(i: Int, count: Int, content: @Composable ColumnScope.() -> Unit) {
    val top = if (i == 0) Radius.lg else 0.dp
    val end = if (i == count - 1) Radius.lg else 0.dp
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(top, top, end, end)).background(p.card).cardRim(p, Radius.lg, i == 0, i == count - 1),
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
        if (onClick != null) Pressable(onClick, Modifier.fillMaxWidth(), RectangleShape) { inner() } else inner()
    }
}

/** [content] opening and closing in place, the rows below it moving with it. */
@Composable
fun Disclosed(open: Boolean, content: @Composable ColumnScope.() -> Unit) = AnimatedVisibility(
    open, enter = expandVertically(tween(260, easing = EaseOutQuart)) + fadeIn(tween(260)),
    exit = shrinkVertically(tween(180, easing = EaseOutQuart)) + fadeOut(tween(120)),
) { Column(content = content) }

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
        Pressable(onMinus, Modifier.size(64.dp), CircleShape, p.fill, label = "Decrease", repeat = repeat) { Icon(Icons.minus, p.fg, 24.dp) }
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(value, Type.display)
            if (unit != null) Txt(" $unit", Type.title3, Modifier.padding(bottom = 12.dp), p.fg2)
        }
        Pressable(onPlus, Modifier.size(64.dp), CircleShape, p.fill, label = "Increase", repeat = repeat) { Icon(Icons.plus, p.fg, 24.dp) }
    }
}

/** A segmented control: one accent thumb slides under the labels, which cross-fade as it passes ([ThumbRow]). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, pager: PagerState? = null) {
    val p = LocalPalette.current
    // The thumb takes the accent, as a tinted UISegmentedControl does: a neutral pill can't clear 3:1 on the track with fg text on it.
    // In Liquid Glass it stays a solid accent pill until held ([GlassThumb]); over a backdrop (a header floating over its pages)
    // the track is a frosted glass pane, so what scrolls under it blurs.
    val track = when {
        p.glass && LocalBackdrop.current != null -> Modifier.glassPane(CircleShape).background(p.fill)
        p.glass -> Modifier.background(p.fill, CircleShape).glassEdge(CircleShape)
        else -> Modifier.background(p.fill, CircleShape)
    }
    ThumbRow(options.size, selected, onSelect, p.accent, modifier.fillMaxWidth(), pager, track, Modifier.height(36.dp)) { i, thumb ->
        if (thumb) Txt(options[i], Type.subhead.copy(fontWeight = Type.headline.fontWeight), color = p.onAccent, maxLines = 1)
        else Txt(options[i], Type.subhead, color = p.fg, maxLines = 1)
    }
}

/**
 * [count] equal segments on a bar ([frame], drawn behind them) with one [thumb] pill that slides under them: tap a segment or drag the thumb.
 * Given a [pager], the thumb follows a swipe of the pages, and dragging it snaps the pages to the segment under it; otherwise it springs to
 * [selected], and a drag selects the segment it is let go over. Only a finger that lands on the thumb holds or drags it: held, the bar
 * swells a little; held or swiped, the thumb lifts past the bar, springs after the finger, and squashes with its speed ([ThumbMotion]). [cell] sizes a segment; [content] draws segment i on the bar, and
 * again on the thumb when its second argument is true.
 */
@Composable
fun ThumbRow(
    count: Int, selected: Int, onSelect: (Int) -> Unit, thumb: Color, modifier: Modifier, pager: PagerState? = null,
    frame: Modifier, cell: Modifier, content: @Composable (Int, Boolean) -> Unit,
) {
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    val own = remember { Animatable(selected.toFloat()) }
    // The thumb's own spring ([ThumbMotion]) carries it to a new selection, so a tap moves it as fast and bouncy as a drag.
    LaunchedEffect(selected) { own.snapTo(selected.toFloat()) }
    var held by remember { androidx.compose.runtime.mutableStateOf(false) }
    val swiped by (pager?.interactionSource ?: remember { MutableInteractionSource() }).collectIsDraggedAsState()
    // Where the thumb heads: the finger while it drags the thumb; the pages' position while a finger swipes them; else the page
    // they are headed to, so after a tap or a fling the thumb leads the pages instead of trailing them.
    var drag by remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
    // A tapped segment, aimed at on the tap itself so the thumb moves on the next frame along with the pages.
    var aim by remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val at = { drag ?: aim?.toFloat() ?: pager?.let { if (swiped) it.position else it.targetPage.toFloat() } ?: own.value }
    fun tap(i: Int) {
        aim = i
        scope.launch {
            try {
                pager?.springTo(i)
            } finally {
                if (aim == i) aim = null
            }
        }
    }
    val motion = remember { ThumbMotion(at()) }
    val lifted = held || swiped
    // Waits, without frames, for the target to move, then chases it until the thumb rests there again.
    LaunchedEffect(motion) {
        while (true) {
            androidx.compose.runtime.snapshotFlow { at() }.first { it != motion.x }
            motion.chase(at)
        }
    }
    LaunchedEffect(lifted) { motion.lift.animateTo(if (lifted) 1f else 0f, ThumbMotion.Lift) }
    val swell by animateFloatAsState(if (held) 1.04f else 1f, androidx.compose.animation.core.spring(0.55f, androidx.compose.animation.core.Spring.StiffnessLow), label = "swell")
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
                        if (abs(down.position.x - (motion.x.coerceIn(0f, count - 1f) + 0.5f) * segment) > segment / 2 + grab) return@awaitEachGesture
                        held = true
                        try {
                            val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                            if (slop == null) {
                                // Let go without dragging, or something else took the finger: stay held until it lifts.
                                while (currentEvent.changes.any { it.pressed }) awaitPointerEvent()
                                return@awaitEachGesture
                            }
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
                                    scope.launch { pager.springTo(over) }
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
                                } else if (to != shown) scope.launch { pager.springTo(to) }
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
            Row(Modifier.onSizeChanged { area = it.toSize() }.glassLens({ pill(area) }, clear)) {
                repeat(count) { i ->
                    Pressable({ if (pager != null) tap(i) else select(i) }, Modifier.weight(1f).then(cell).semantics { this.selected = i == on }, CircleShape, role = Role.Tab) {
                        content(i, false)
                    }
                }
            }
            // The thumb carries its own labels, clipped to its pill, so each label turns as the thumb's edge crosses it.
            // Lifted, it grows past the bar, so nothing here clips to the bar.
            Row(
                Modifier.matchParentSize().clearAndSetSemantics {}.drawWithContent {
                    val pill = pill(size)
                    with(p.thumbPainter) { paint(pill, thumb, motion.lift.value.coerceIn(0f, 1f)) }
                    // Its accent-colored labels fade as it clears, leaving the bar's own labels seen through the glass.
                    val a = 1f - clear()
                    if (a > 0f) clipPath(Path().apply { addRoundRect(pill) }) {
                        drawContext.canvas.saveLayer(pill.let { androidx.compose.ui.geometry.Rect(it.left, it.top, it.right, it.bottom) }, androidx.compose.ui.graphics.Paint().apply { alpha = a })
                        this@drawWithContent.drawContent()
                        drawContext.canvas.restore()
                    }
                },
            ) {
                repeat(count) { i ->
                    Box(Modifier.weight(1f).then(cell), contentAlignment = Alignment.Center) { content(i, true) }
                }
            }
        }
    }
}

/**
 * A slider from 0 to [max] that snaps to multiples of [step]: thin track, accent fill up to a white thumb.
 * Touching the track jumps the thumb there, then it follows the finger.
 */
@Composable
fun Slider(value: Float, onChange: (Float) -> Unit, max: Float, step: Float, label: String, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val change by rememberUpdatedState(onChange)
    fun snap(v: Float) = (Math.round(v.coerceIn(0f, max) / step) * step).coerceAtMost(max)
    val frac = if (max > 0f) (value / max).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        modifier.fillMaxWidth().height(44.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..max)
                setProgress { change(snap(it)); true }
            }
            .pointerInput(max, step) {
                val thumb = 28.dp.toPx()
                fun at(x: Float) = change(snap((x - thumb / 2) / (size.width - thumb) * max))
                awaitEachGesture {
                    val down = awaitFirstDown()
                    at(down.position.x)
                    horizontalDrag(down.id) { at(it.position.x); it.consume() }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(p.fillPressed))
        Box(Modifier.width(14.dp + (maxWidth - 28.dp) * frac).height(4.dp).clip(CircleShape).background(p.accent))
        SliderThumb(Modifier.padding(start = (maxWidth - 28.dp) * frac))
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
    Pressable(onClick, Modifier.size(44.dp).semantics { contentDescription = label }, CircleShape, p.fill, enabled = enabled, label = label) { Icon(icon, p.fg, 18.dp) }
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

/** Bottom sheet over a scrim; tapping the scrim dismisses. */
@Composable
fun Sheet(visible: Boolean, onDismiss: () -> Unit, backdrop: Backdrop? = LocalBackdrop.current, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    // Back closes the sheet, never the screen or the app beneath it.
    androidx.activity.compose.BackHandler(visible, onDismiss)
    HoldPages(visible)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(240)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(p.scrim).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
        }
        AnimatedVisibility(
            visible, Modifier.align(Alignment.BottomCenter),
            // Apple's sheet curve: fast out of the gate, long settle; leaving takes about two thirds as long.
            enter = slideInVertically(tween(300, easing = SheetEase)) { it }, exit = slideOutVertically(tween(200, easing = SheetEase)) { it },
        ) {
            // Dragged down, the sheet follows the finger 1:1; past a quarter of its height or on a downward flick it closes, else it springs back.
            val drag = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            val height = remember { androidx.compose.runtime.mutableIntStateOf(1) }
            LaunchedEffect(visible) { if (visible) drag.floatValue = 0f }
            Box(
                Modifier.fillMaxWidth()
                    .onSizeChanged { height.intValue = it.height }
                    .offset { androidx.compose.ui.unit.IntOffset(0, drag.floatValue.toInt()) }
                    .draggable(
                        androidx.compose.foundation.gestures.rememberDraggableState { d -> drag.floatValue = (drag.floatValue + d).coerceAtLeast(0f) },
                        androidx.compose.foundation.gestures.Orientation.Vertical,
                        onDragStopped = { v: Float ->
                            if (drag.floatValue > height.intValue / 4f || v > 1500f) onDismiss()
                            else androidx.compose.animation.core.animate(drag.floatValue, 0f, animationSpec = tween(200, easing = SheetEase)) { x, _ -> drag.floatValue = x }
                        },
                    )
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

/** A floating "[text] · Undo" bar that rises while [text] is set; the caller clears it to let it go. */
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
 * The app's bottom tab bar: five destinations on a [ThumbRow], the current one on a raised pill in the accent; tap a tab, or drag the pill
 * along the bar and let go over one. In Liquid Glass it floats as a frosted capsule on a solid accent pill, and the held pill turns to clear glass.
 */
@Composable
fun TabBar(selected: Tab, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val p = LocalPalette.current
    val bar: @Composable (Modifier, Modifier) -> Unit = { m, frame ->
        // In Liquid Glass the pill is the accent, as the header's switch is, until held; one label weight keeps the bar's label hidden under the pill's.
        ThumbRow(Tab.entries.size, selected.ordinal, { onSelect(Tab.entries[it]) }, if (p.glass) p.accent else p.elevated, m, frame = frame, cell = Modifier.fillMaxHeight()) { i, thumb ->
            val t = Tab.entries[i]
            val c = if (!thumb) p.fg2 else if (p.glass) p.onAccent else p.accent
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(t.icon(), c, 24.dp)
                Txt(t.label, TabLabel, color = c, maxLines = 1)
            }
        }
    }
    if (p.glass) bar(
        modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = Space.lg, end = Space.lg, bottom = Space.xs).fillMaxWidth().height(GlassTabBar),
        Modifier.shadow(8.dp, CircleShape).glassPane(CircleShape),
    ) else Column(modifier.fillMaxWidth().background(p.bg)) {
        Hairline()
        // Inset so the end tabs' pills keep the screen's 16 dp gutter.
        bar(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = Space.md).height(56.dp), Modifier)
    }
}

/** A tab's label: iOS's 10 pt medium, small enough that the longest ("Upcoming") clears the rounded ends of its pill at 115% text size. */
private val TabLabel = Type.caption.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)

/** The floating glass tab bar's height, above its [Space.xs] margin and the navigation bar. */
val GlassTabBar = 60.dp

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
