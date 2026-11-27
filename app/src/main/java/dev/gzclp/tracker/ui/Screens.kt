package dev.gzclp.tracker.ui

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.animation.core.tween

import androidx.compose.animation.core.EaseOutCubic

import androidx.compose.animation.core.Animatable

import dev.gzclp.tracker.log.bestSoFar
import dev.gzclp.tracker.log.pastBest
import dev.gzclp.tracker.log.isRecord
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import dev.gzclp.tracker.log.shouldSuggest
import dev.gzclp.tracker.log.strengthSuggestion
import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Progression
import dev.gzclp.tracker.engine.Cycle
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.Warmup
import dev.gzclp.tracker.log.SetLogged
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.SetKey
import dev.gzclp.tracker.ui.Type.tabular
import java.time.Duration
import java.time.OffsetDateTime

/** A full-height screen: scrolling content below the status bar, optional bottom bar. */
@Composable
fun ScreenFrame(bottom: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) = Frame(bottom) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.lg).padding(top = LocalTopInset.current, bottom = Space.x3 + LocalBottomInset.current),
        content = content,
    )
}

/** A [ScreenFrame] whose items compose only as they scroll into view, for a page with a long list below the fold. */
@Composable
fun LazyScreenFrame(bottom: (@Composable () -> Unit)? = null, content: LazyListScope.() -> Unit) = Frame(bottom) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.lg, top = LocalTopInset.current, end = Space.lg, bottom = Space.x3 + LocalBottomInset.current), content = content)
}

@Composable
private fun Frame(bottom: (@Composable () -> Unit)?, scroller: @Composable () -> Unit) {
    val p = LocalPalette.current
    val title = remember { LargeTitle() }
    var top by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val barPx = with(androidx.compose.ui.platform.LocalDensity.current) { 44.dp.toPx() }
    val inline by remember { androidx.compose.runtime.derivedStateOf { title.bottom < top } }
    // Liquid Glass floats the bottom bar over the page, which scrolls on under it and under the tab bar below.
    val b = rememberBackdrop()
    val below = LocalBottomInset.current
    var bar by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inset = if (b == null) 0.dp else with(density) { bar.toDp() }
    // Status bar inset outside the scroll, so content clips below it instead of scrolling under the clock.
    Column(Modifier.fillMaxSize().page().windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { top = it.positionInRoot().y + barPx }) {
            Box(Modifier.matchParentSize().backdropSource(b)) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalLargeTitle provides title, LocalBottomInset provides inset,
                    content = scroller,
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides b) {
                // Once the large title scrolls under it, the title shows small in a bar, as iOS collapses it;
                // not in a page of [Pages], whose fixed header above already holds the screen's bar.
                androidx.compose.animation.AnimatedVisibility(inline && LocalPages.current == null, enter = androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.fadeOut()) {
                    Column(Modifier.fillMaxWidth().then(if (b != null) Modifier.scrollEdge() else Modifier.background(p.bg))) {
                        Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = Space.x4), contentAlignment = Alignment.Center) {
                            Txt(title.text, Type.headline, maxLines = 1)
                        }
                        // In Liquid Glass a scroll edge fades the page out under the bar instead of a hairline.
                        if (b != null) Spacer(Modifier.height(ScrollEdgeFade)) else Box(Modifier.fillMaxWidth().height(1.dp).background(p.separator))
                    }
                }
                // One scroll edge from above the bar down to the screen's edge: the page fades out above the bar's glass buttons, and the
                // floating tab bar sits on the wallpaper, not on a second pane of glass.
                if (b != null && bottom != null) Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bar = it.height }.scrollEdge(up = true),
                ) {
                    Spacer(Modifier.height(ScrollEdgeFade))
                    bottom()
                    Spacer(Modifier.height(below))
                }
            }
        }
        if (b == null) bottom?.invoke()
    }
}

@Composable
fun NavRow(onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(52.dp).padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(Icons.chevronLeft, "Back", onBack, Modifier.padding(start = 0.dp))
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SetupScreen(onStart: (Map<Lift, Int>, String) -> Unit, onRestore: () -> Unit, message: String?, initial: Map<Lift, String> = emptyMap(), initialProgram: String = Programs.GZCLP) {
    val p = LocalPalette.current
    val values = remember { mutableStateMapOf<Lift, String>().apply { putAll(initial) } }
    var program by rememberSaveable { mutableStateOf(initialProgram) }
    val parsed = Lift.entries.associateWith { values[it]?.toIntOrNull()?.takeIf { v -> v in 20..1500 } }
    val ready = parsed.values.all { it != null }
    val gzclp = Programs.of(program).gzclp
    ScreenFrame(bottom = {
        BottomBar {
            PillButton("Restore log", onRestore, Modifier.weight(1f))
            PillButton("Start program", { onStart(parsed.mapValues { it.value!! }, program) }, Modifier.weight(1f), primary = true, enabled = ready)
        }
    }) {
        Spacer(Modifier.height(Space.x4))
        Txt("Starting weights", Type.largeTitle)
        Spacer(Modifier.height(Space.sm))
        Txt(
            "Enter a recent 5-rep max for each lift.",
            Type.body, color = p.fg2,
        )
        Spacer(Modifier.height(Space.xxl))
        Group {
            Lift.entries.forEachIndexed { i, lift ->
                GroupRow(first = i == 0) {
                    // Two lines tall even before a 5RM is typed, so the row keeps its height and the name stays centered.
                    val twoLines = with(androidx.compose.ui.platform.LocalDensity.current) { Type.headline.lineHeight.toDp() + Type.footnote.lineHeight.toDp() }
                    Column(Modifier.weight(1f).heightIn(min = twoLines), verticalArrangement = Arrangement.Center) {
                        Txt(lift.label, Type.headline)
                        val rm = parsed[lift]
                        if (rm != null) Txt(
                            if (gzclp) "T1 ${Fmt.lb(Progression.t1FromFiveRm(rm))} · T2 ${Fmt.lb(Progression.t2FromFiveRm(rm))}"
                            else "Week 1 at ${Fmt.lb(Cycle.baseFromFiveRm(rm))}",
                            Type.footnote, color = p.fg2,
                        )
                    }
                    NumberField(values[lift].orEmpty(), { v -> values[lift] = v.filter(Char::isDigit).take(4) }, last = i == Lift.entries.lastIndex, placeholder = "5RM")
                    Txt(" lb", Type.body, color = p.fg2)
                }
            }
        }
        if (message != null) GroupFooter(message, color = p.red)
        GroupHeader("Program")
        ProgramPicker(program) { program = it }
    }
}

@Composable
fun ProgramPicker(selected: String, onSelect: (String) -> Unit) {
    val p = LocalPalette.current
    Group {
        Programs.all.forEachIndexed { i, prog ->
            GroupRow(first = i == 0, onClick = { onSelect(prog.id) }) {
                Column(Modifier.weight(1f)) {
                    Txt(prog.name, Type.body)
                    Txt("${prog.days.size} days · " + if (prog.gzclp) "T1, T2, T3 progression" else "3-week cycle", Type.footnote, color = p.fg2)
                }
                if (prog.id == selected) Icon(Icons.check, p.accent, 20.dp)
            }
        }
    }
}

@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    last: Boolean = true,
    modifier: Modifier = Modifier,
    placeholder: String = "0",
    width: Dp = 88.dp,
    color: Color = LocalPalette.current.fg,
    onCommit: () -> Unit = {},
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
    fill: Color = LocalPalette.current.fill,
) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // Re-asks as the keyboard slides up, so the field ends in the shrunken viewport, not behind a bottom bar.
    val bring = remember { BringIntoViewRequester() }
    val ime = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(focused, ime) { if (focused) bring.bringIntoView() }
    Box(modifier.bringIntoViewRequester(bring).width(width).height(44.dp).clip(RoundedCornerShape(Radius.md)).background(fill).padding(horizontal = Space.md), contentAlignment = Alignment.CenterEnd) {
        if (value.isEmpty()) Txt(placeholder, Type.body.tabular(), color = p.fg3, align = TextAlign.End)
        BasicTextField(
            value, onChange,
            Modifier.fillMaxWidth()
                .onFocusChanged { if (focused && !it.isFocused) onCommit(); focused = it.isFocused }
                .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
            textStyle = Type.body.tabular().copy(color = color, textAlign = TextAlign.End),
            singleLine = true, cursorBrush = SolidColor(p.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (last) ImeAction.Done else ImeAction.Next),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )
    }
}

/** "T1 · 5×3+" or "Week 2 of 3 · 3×10". */
fun Exercise.detail(): String =
    if (tier.cyclic) "Week ${(week ?: 0) + 1} of ${weeks.size} · ${scheme.display}" else "${tier.label} · ${scheme.display}"

/** Warm-ups go before a GZCLP T1 lift and before a split's barbell lift on a week of 6 reps or fewer. */
fun Exercise.warmsUp(): Boolean = tier == Tier.T1 || (tier == Tier.CUSTOM && id in Catalog.barbell && scheme.reps <= 6)

@Composable
fun HomeScreen(
    state: AppState,
    onStart: () -> Unit,
    onEditWeight: (Exercise) -> Unit,
    onUseSuggestion: (Exercise, Int) -> Unit = { e, _ -> onEditWeight(e) },
    onStartOpen: () -> Unit = {},
    onStartTop: () -> Unit = {},
) {
    val p = LocalPalette.current
    val day = state.nextDay
    val exercises = state.exercises(day).map(LocalPlates.current::prescribe)
    var showOpenSessionSheet by remember { mutableStateOf(false) }
    ScreenFrame(bottom = {
        BottomBar {
            // Three ways into the day: the workout itself under the two lighter ones.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    PillButton("Open session", { showOpenSessionSheet = true }, Modifier.weight(1f))
                    PillButton("Log top sets", onStartTop, Modifier.weight(1f))
                }
                PillButton("Start workout", onStart, Modifier.fillMaxWidth(), primary = true)
            }
        }
    }) {
        Spacer(Modifier.height(Space.xxl))
        Txt(day.title, Type.largeTitle)
        Spacer(Modifier.height(Space.xxl))
        Group {
            exercises.forEachIndexed { i, ex ->
                val plan = ex.weight
                val suggested = if (plan != null) state.strengthSuggestion(ex) else null
                GroupRow(first = i == 0, onClick = { onEditWeight(ex) }) {
                    Column(Modifier.weight(1f)) {
                        Txt(ex.name, Type.headline, maxLines = 1)
                        Txt(ex.detail(), Type.footnote, color = p.fg2)
                        if (plan != null && suggested != null && state.shouldSuggest(ex, suggested)) {
                            Spacer(Modifier.height(Space.xs))
                            StrengthSuggestion(suggested, { onUseSuggestion(ex, suggested) })
                        }
                    }
                    Txt(ex.weight?.let(Fmt::lb) ?: "Set weight", Type.headline.tabular(), color = p.accent)
                }
            }
        }
    }
    Sheet(showOpenSessionSheet, { showOpenSessionSheet = false }) {
        OpenSessionStartSheet(
            state = state,
            onStartFromToday = {
                showOpenSessionSheet = false
                OpenSessionManager.start(fromToday = true, state, onStartOpen)
            },
            onStartBlank = {
                showOpenSessionSheet = false
                OpenSessionManager.start(fromToday = false, state, onStartOpen)
            },
            onDismiss = { showOpenSessionSheet = false },
        )
    }
}
/** What the rest card needs; null when not resting. */
data class RestUi(val remainingSec: Long, val totalSec: Int, val label: String)

@Composable
fun WorkoutScreen(
    state: AppState,
    now: OffsetDateTime,
    rest: RestUi?,
    onLog: (Exercise, Int, Int, Int, Double?) -> Unit,
    onClear: (Exercise, Int) -> Unit,
    onEditWeight: (Exercise) -> Unit,
    onSwap: (Exercise) -> Unit,
    onSkipRest: () -> Unit,
    onEnd: () -> Unit,
    warmups: Boolean = true,
    onUseSuggestion: (Exercise, Int) -> Unit = { e, _ -> onEditWeight(e) },
    onFinishOpen: (counts: Boolean) -> Unit = { onEnd() },
    onLogTop: (Exercise, weight: Int, reps: Int, sets: Int) -> Unit = { _, _, _, _ -> },
    onFinishTop: () -> Unit = { onEnd() },
) {
    val p = LocalPalette.current
    val a = state.active ?: return
    if (a.top) {
        TopSetsScreen(state, onLogTop, { onClear(it, 1) }, onFinishTop)
        return
    }
    if (a.open) {
        LaunchedEffect(a.id, OpenSessionManager.isActive) { OpenSessionManager.attach(state) }
        OpenSessionScreen(
            state = state,
            now = now,
            onLog = onLog,
            onClear = onClear,
            onFinish = { counts ->
                OpenSessionManager.reset()
                onFinishOpen(counts)
            },
        )
        return
    }
    // The rest timer lives in the fixed bar, never in the list, so logging a set moves no row under the thumb.
    ScreenFrame(bottom = {
        BottomBar {
            if (rest != null && rest.remainingSec > 0) RestBar(rest, onSkipRest, Modifier.weight(1f))
            else PillButton("End workout", onEnd, Modifier.weight(1f))
        }
    }) {
        Spacer(Modifier.height(Space.xxl))
        Txt(a.day.title, Type.largeTitle, maxLines = 1)
        Spacer(Modifier.height(Space.xs))
        Txt(Fmt.minutes(Duration.between(a.startedAt, now).seconds), Type.subhead.tabular(), color = p.fg2)
        val exercises = state.sessionExercises().map(LocalPlates.current::prescribe)
        val next = exercises.firstNotNullOfOrNull { ex -> (1..ex.scheme.sets).map { SetKey(ex.id, ex.tier, it) }.firstOrNull { it !in a.sets } }
        // Loaded: a workout just started rises into place, the set pill with its rows. Drawn only, so every row takes a tap from
        // the first frame.
        val motion = LocalMotion.current
        val rise = remember(a.id) { Animatable(if (Effect.Bookends in motion && Duration.between(a.startedAt, now).seconds < 3) 0f else 1f) }
        LaunchedEffect(a.id) {
            if (rise.value < 1f) {
                motion.feel?.lift()
                rise.animateTo(1f, tween(640, easing = EaseOutCubic))
            }
        }
        // One pill for the whole session, so it springs from the last set of one exercise to the first of the next.
        StepPillHost(
            p.bg,
            Modifier.fillMaxWidth().then(
                if (Effect.Bookends !in motion) Modifier else Modifier.graphicsLayer {
                    translationY = (1f - rise.value) * 56.dp.toPx()
                    alpha = rise.value
                },
            ),
        ) {
            Column {
                exercises.forEach { ex ->
                    Spacer(Modifier.height(Space.x3))
                    ExerciseBlock(state, ex, next, onLog, onClear, onEditWeight, onSwap, warmups, onUseSuggestion)
                }
            }
        }
    }
}

/** The running rest in the bottom bar: time left over a draining bar, and Skip rest. Same height as End workout. */
@Composable
fun RestBar(rest: RestUi, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(Fmt.clock(rest.remainingSec), Type.title2.tabular(), Modifier.alignByBaseline())
                Spacer(Modifier.width(Space.sm))
                Txt(rest.label, Type.footnote, Modifier.weight(1f).alignByBaseline(), p.fg2, maxLines = 2)
            }
            Spacer(Modifier.height(Space.sm))
            val frac = (rest.remainingSec.toFloat() / rest.totalSec.coerceAtLeast(1)).coerceIn(0f, 1f)
            if (loaded(Effect.RestLiquid)) LiquidChannel(frac, rest.remainingSec, Modifier.fillMaxWidth().height(8.dp))
            else Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(p.fillPressed)) {
                Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(p.accent))
            }
        }
        PillButton("Skip rest", onSkip)
    }
}

@Composable
private fun ExerciseBlock(
    state: AppState,
    ex: Exercise,
    next: SetKey?,
    onLog: (Exercise, Int, Int, Int, Double?) -> Unit,
    onClear: (Exercise, Int) -> Unit,
    onEditWeight: (Exercise) -> Unit,
    onSwap: (Exercise) -> Unit,
    warmups: Boolean,
    onUseSuggestion: (Exercise, Int) -> Unit = { e, _ -> onEditWeight(e) },
) {
    val p = LocalPalette.current
    val sets = state.active?.sets.orEmpty()
    val plates = LocalPlates.current.takeIf { ex.id in Catalog.barbell }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Txt(ex.name, Type.title3, maxLines = 1)
            Txt(ex.detail(), Type.footnote, color = p.fg2)
            ex.swappedFrom?.let { Txt("In place of ${state.name(it)}", Type.footnote, color = p.fg2, maxLines = 1) }
            if (plates != null && ex.weight != null) PlateLine(plates, ex.weight)
        }
        if ((1..ex.scheme.sets).none { SetKey(ex.id, ex.tier, it) in sets }) SmallPill("Swap", { onSwap(ex) }, Modifier.padding(start = Space.sm))
        // A substitute has no prescription to edit: its weight is only a suggestion from last time.
        if (ex.swappedFrom != null) ex.weight?.let { Txt(Fmt.lb(it), Type.title3.tabular(), Modifier.padding(start = Space.lg), p.fg2) }
        else Pressable({ onEditWeight(ex) }, Modifier.height(44.dp), label = "Change ${ex.name} weight") {
            Txt(Fmt.lb(ex.weight ?: 0), Type.title3.tabular(), Modifier.padding(start = Space.lg), p.accent)
        }
    }
    FormToggle(ex.id)
    val plan = ex.weight
    val suggested = if (plan != null) state.strengthSuggestion(ex) else null
    if (plan != null && suggested != null && state.shouldSuggest(ex, suggested)) {
        Spacer(Modifier.height(Space.xs))
        StrengthSuggestion(suggested, { onUseSuggestion(ex, suggested) })
    }
    Spacer(Modifier.height(Space.sm))
    Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Txt("Set", Type.caption, Modifier.width(SET_COL), p.fg2)
        // Header labels end where the field digits end, inside the fields' horizontal padding.
        Txt("lb", Type.caption, Modifier.width(WEIGHT_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
        Txt("Reps", Type.caption, Modifier.width(REPS_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
        Txt("RPE", Type.caption, Modifier.width(RPE_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        val allPlates = LocalPlates.current
        // A substitute never logged before has no weight to warm up to.
        if (warmups && ex.warmsUp() && ex.weight != null) Warmup.sets(ex.weight).map { (w, r) -> allPlates.round(w) to r }.distinctBy { it.first }.forEachIndexed { i, (w, r) ->
            WarmupRow(state.active?.id.orEmpty(), "${ex.name} warm-up ${i + 1}", w, r, allPlates)
        }
        // Only Loaded asks for the lift's best, so Performance never scans the log on the session's quarter-second tick.
        val past = if (loaded(Effect.PrSplash)) remember(state.sessions, ex.id) { state.pastBest(ex.id) } else null
        for (i in 1..ex.scheme.sets) {
            val key = SetKey(ex.id, ex.tier, i)
            SetRow(
                index = i,
                amrap = ex.scheme.amrap && i == ex.scheme.sets,
                targetReps = ex.scheme.reps,
                defaultWeight = Commands.setWeight(state, ex, i),
                logged = sets[key]?.event,
                current = next == key,
                exName = ex.name,
                plates = plates,
                plateWeight = ex.weight,
                onLog = { w, r, rpe -> onLog(ex, i, w, r, rpe) },
                onClear = { onClear(ex, i) },
                last = i == ex.scheme.sets,
                best = past?.let { bestSoFar(it, sets, ex.id, key) },
            )
        }
    }
}

/** A heavier weight the recent sets support than the plan: a sentence and a button that takes it. */
@Composable
fun StrengthSuggestion(suggested: Int, onUse: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Txt("Your recent sets say you can lift more.", Type.footnote, Modifier.weight(1f), p.fg2)
        SmallPill("Use ${Fmt.lb(suggested)}", onUse, Modifier.padding(start = Space.sm))
    }
}

private val SET_COL = 32.dp
// Sized so the row fits a 360dp-wide phone with the RPE column.
private val WEIGHT_COL = 76.dp
private val REPS_COL = 60.dp
private val RPE_COL = 56.dp

/** What the RPE field accepts while typing: empty, 1 to 9 with an optional .5, or 10. */
private val RPE_INPUT = Regex("(10|[1-9](\\.5?)?)?")

/** Plates per side for [weight], or that the plates can't make it. */
@Composable
fun PlateLine(plates: Plates, weight: Int, modifier: Modifier = Modifier) {
    if (LocalShowPlates.current) Txt(plates.perSideLabel(weight) ?: "Not loadable with your plates", Type.footnote.tabular(), modifier, LocalPalette.current.fg2, maxLines = 1)
}

/**
 * One set: its own weight and reps, prefilled from the prescription, an optional RPE, and a check that logs or clears it.
 * Its plate line shows only when its weight differs from [plateWeight], the one the exercise header already loads.
 */
@Composable
fun SetRow(
    index: Int,
    amrap: Boolean,
    targetReps: Int,
    defaultWeight: Int?,
    logged: SetLogged?,
    current: Boolean,
    exName: String,
    plates: Plates?,
    plateWeight: Int?,
    onLog: (weight: Int, reps: Int, rpe: Double?) -> Unit,
    onClear: () -> Unit,
    last: Boolean = false,
    best: Int? = null,
) {
    val p = LocalPalette.current
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    val landing = remember { Landing() }
    var w by remember(logged?.weight, defaultWeight) { mutableStateOf((logged?.weight ?: defaultWeight)?.toString() ?: "") }
    val weightFocus = remember { FocusRequester() }
    var r by remember(logged?.actualReps) { mutableStateOf(logged?.actualReps?.toString() ?: if (amrap) "" else targetReps.toString()) }
    fun weight() = w.toIntOrNull() ?: logged?.weight ?: defaultWeight
    var e by remember(logged?.rpe) { mutableStateOf(logged?.rpe?.let(Fmt::rpe) ?: "") }
    fun reps() = r.toIntOrNull() ?: logged?.actualReps ?: targetReps
    val commit = { if (logged != null) onLog(weight() ?: logged.weight, reps(), e.toDoubleOrNull()) }
    val short = logged != null && logged.actualReps < targetReps
    val name = "$exName set $index"
    val fieldFill = pillFill(current)
    Row(Modifier.fillMaxWidth().stepPill(current).then(if (motion.effects.isEmpty()) Modifier else Modifier.landingRing(landing, p.accent, 44.dp)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Txt(if (amrap) "$index+" else "$index", Type.headline.tabular(), Modifier.width(SET_COL), if (current) p.fg else p.fg2)
        // No weight known (a first-time substitute) leaves the field empty; the check then asks for one instead of logging 0.
        NumberField(
            w, { w = it.filter(Char::isDigit).take(4) }, last = false, Modifier.focusRequester(weightFocus), placeholder = "",
            width = WEIGHT_COL, onCommit = commit, label = "$name weight", fill = fieldFill,
        )
        NumberField(
            r, { r = it.filter(Char::isDigit).take(3) }, width = REPS_COL, placeholder = if (amrap) "$targetReps+" else "$targetReps",
            color = if (short) p.red else p.fg, onCommit = commit, label = "$name reps", fill = fieldFill,
        )
        NumberField(
            e, { if (RPE_INPUT.matches(it)) e = it }, width = RPE_COL, placeholder = "",
            onCommit = commit, label = "$name RPE", keyboardType = KeyboardType.Decimal, fill = fieldFill,
        )
        Spacer(Modifier.weight(1f))
        val hit = logged != null && !short
        Pressable(
            {
                focus.clearFocus()
                val wt = weight()
                when {
                    logged != null -> {
                        haptic.performHapticFeedback(HapticFeedbackType.Reject)
                        onClear()
                    }
                    wt == null -> weightFocus.requestFocus()
                    else -> {
                        val record = Effect.PrSplash in motion && isRecord(best, wt, reps())
                        if (record || Effect.PlateDrop in motion) scope.launch { landing.play(record, last) }
                        val feel = motion.feel
                        when {
                            feel == null -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            record -> feel.double()
                            else -> feel.land(if (last) 0.45f else 0.3f)
                        }
                        onLog(wt, reps(), e.toDoubleOrNull())
                    }
                }
            },
            Modifier.landingSquash(landing).size(44.dp).semantics { contentDescription = if (logged != null) "Clear $name" else "Log $name" }, CircleShape,
            fill = when {
                hit -> p.accent
                short -> p.red.copy(alpha = 0.18f)
                else -> fieldFill
            },
            pressedFill = if (hit) Color.Black.copy(alpha = 0.14f) else p.fillPressed,
            border = if (current) p.accent else null,
            label = if (logged != null) "Clear $name" else "Log $name",
            scale = SMALL_PRESS,
        ) {
            Icon(Icons.check, if (hit) p.onAccent else if (short) p.red else p.fg3, 20.dp)
        }
    }
    val wt = weight()
    if (plates != null && wt != null && wt != plateWeight) PlateLine(plates, wt, Modifier.padding(start = SET_COL + Space.md))
}

/** A warm-up: fixed weight and reps, and a check that only lives on screen (never logged). */
@Composable
private fun WarmupRow(sessionId: String, name: String, weight: Int, reps: Int, plates: Plates) {
    val p = LocalPalette.current
    var done by rememberSaveable(sessionId) { mutableStateOf(false) }
    // The plate line sits inside its own row, under the numbers, so it never reads as the next row's.
    val plateLine = if (LocalShowPlates.current) plates.perSideLabel(weight) else null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Txt("W", Type.headline, Modifier.width(SET_COL), p.fg2)
                Txt("$weight", Type.body.tabular(), Modifier.width(WEIGHT_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
                Txt("$reps", Type.body.tabular(), Modifier.width(REPS_COL).padding(end = Space.md), p.fg2, align = TextAlign.End)
            }
            plateLine?.let { Txt(it, Type.footnote.tabular(), Modifier.padding(start = SET_COL + Space.md), p.fg2, maxLines = 1) }
        }
        Pressable(
            { done = !done }, Modifier.size(44.dp).semantics { contentDescription = if (done) "Undo $name" else "Done $name" }, CircleShape,
            fill = if (done) p.accent.copy(alpha = 0.18f) else p.fill,
            label = if (done) "Undo $name" else "Done $name",
            scale = SMALL_PRESS,
        ) {
            Icon(Icons.check, if (done) p.accent else p.fg3, 20.dp)
        }
    }
}

fun restLabel(ex: Exercise, setIndex: Int): String =
    "Rest after ${ex.name.lowercase()} " + // A no-break space keeps "set 3" whole when the label wraps.
        if (setIndex == ex.scheme.sets) "last\u00A0set" else "set\u00A0$setIndex"

fun unloggedSets(state: AppState): Int {
    if (state.active == null) return 0
    return state.sessionExercises().sumOf { ex -> Commands.reps(state, ex).count { it == null } }
}
