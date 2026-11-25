package dev.gzclp.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
) {
    val p = LocalPalette.current
    val primary = primary && enabled // a disabled primary goes neutral so it never reads as live
    val fill = when {
        primary -> p.accent
        onSurface -> p.fill
        else -> p.elevated
    }
    val fg = if (primary) p.onAccent else p.fg
    Pressable(
        onClick, modifier.heightIn(min = 52.dp), CircleShape, fill,
        pressedFill = if (primary) Color.Black.copy(alpha = 0.14f) else p.fillPressed, enabled = enabled,
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
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(LocalPalette.current.elevated), content = content)

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
    content: @Composable RowScope.() -> Unit,
) {
    Column {
        if (!first) Hairline(start = Space.lg)
        val inner: @Composable () -> Unit = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = Space.lg, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically, content = content,
            )
        }
        if (onClick != null) Pressable(onClick, Modifier.fillMaxWidth(), RectangleShape) { inner() } else inner()
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

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    // The selected pill takes the accent, as a tinted UISegmentedControl does: a neutral pill can't clear 3:1 on the track with fg text on it.
    Row(modifier.fillMaxWidth().clip(CircleShape).background(p.fill).padding(Space.xs)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Pressable(
                { onSelect(i) },
                Modifier.weight(1f).height(36.dp).semantics { this.selected = on },
                CircleShape, if (on) p.accent else Color.Transparent, role = Role.Tab,
            ) {
                Txt(o, Type.subhead.copy(fontWeight = if (on) Type.headline.fontWeight else Type.subhead.fontWeight), color = if (on) p.onAccent else p.fg, maxLines = 1)
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
fun Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    // Back closes the sheet, never the screen or the app beneath it.
    androidx.activity.compose.BackHandler(visible, onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(240)), exit = fadeOut(tween(160))) {
            Box(Modifier.fillMaxSize().background(p.scrim).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
        }
        AnimatedVisibility(
            visible, Modifier.align(Alignment.BottomCenter),
            // Apple's sheet curve: fast out of the gate, long settle; leaving takes about two thirds as long.
            enter = slideInVertically(tween(300, easing = SheetEase)) { it }, exit = slideOutVertically(tween(200, easing = SheetEase)) { it },
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl))
                    .background(p.elevated)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(Space.xl),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
                content = content,
            )
        }
    }
}

/** A floating "[text] · Undo" bar that rises while [text] is set; the caller clears it to let it go. */
@Composable
fun BoxScope.UndoBar(text: String?, onUndo: () -> Unit, modifier: Modifier = Modifier) {
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
            Modifier.padding(horizontal = Space.lg).fillMaxWidth().shadow(8.dp, CircleShape).clip(CircleShape).background(p.elevated)
                // In a dark theme the bar steps above the cards it floats over, or it melts into them.
                .then(if (p.dark) Modifier.background(p.fillPressed) else Modifier)
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

/** Two equal pills on a hairline, pinned to the bottom of a screen. */
@Composable
fun BottomBar(content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(LocalPalette.current.bg)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars.exclude(WindowInsets.ime)).padding(Space.lg),
            horizontalArrangement = Arrangement.spacedBy(Space.md), content = content,
        )
    }
}

enum class Tab(val label: String) { Workout("Workout"), Upcoming("Upcoming"), Exercises("Exercises"), History("History"), Food("Food") }

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Workout -> Icons.dumbbell
    Tab.Upcoming -> Icons.calendar
    Tab.Exercises -> Icons.chartLine
    Tab.History -> Icons.history
    Tab.Food -> Icons.utensils
}

/** The app's bottom tab bar: five destinations, the current one in the accent. */
@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.bg)) {
        Hairline()
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(56.dp)) {
            Tab.entries.forEach { t ->
                val c = if (t == selected) p.accent else p.fg2
                Pressable({ onSelect(t) }, Modifier.weight(1f).fillMaxHeight().semantics { this.selected = t == selected }, RectangleShape, role = Role.Tab) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(t.icon(), c, 24.dp)
                        Txt(t.label, Type.caption.copy(fontWeight = if (t == selected) FontWeight.SemiBold else FontWeight.Normal), color = c, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A tab's screen above the tab bar; the bar steps aside while the keyboard is up. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabFrame(tab: Tab, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) { content() }
        if (!WindowInsets.isImeVisible) TabBar(tab, onSelect)
    }
}
