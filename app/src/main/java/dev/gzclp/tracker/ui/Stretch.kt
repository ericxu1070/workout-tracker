package dev.gzclp.tracker.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Event
import dev.gzclp.tracker.log.StretchLogged
import dev.gzclp.tracker.stretch.BodyArea
import dev.gzclp.tracker.stretch.Stage
import dev.gzclp.tracker.stretch.StretchCoach
import dev.gzclp.tracker.stretch.StretchMove
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchPlayerState
import dev.gzclp.tracker.stretch.StretchRoutine
import dev.gzclp.tracker.stretch.StretchRoutines
import dev.gzclp.tracker.ui.Type.tabular

/** The Workout tab's Stretching page: every routine to pick and start, each with its own page, then the move library. */
@Composable
fun StretchScreen(
    state: AppState?,
    defaultId: String,
    onMode: (Int) -> Unit,
    onSettings: () -> Unit,
    onStartRoutine: (StretchRoutine) -> Unit,
    onOpenRoutine: (StretchRoutine) -> Unit,
    onNewRoutine: () -> Unit,
    initialAbout: String? = null,
) {
    val p = LocalPalette.current
    val routines = remember(state) { StretchRoutines.all(state) }
    val defaultKey = remember(state, defaultId) { StretchRoutines.of(defaultId, state).key }
    var selectedKey by remember(defaultKey) { mutableStateOf(defaultKey) }
    val currentRoutine = routines.firstOrNull { it.key == selectedKey } ?: routines.first()
    var moreShown by rememberSaveable(defaultKey) { mutableStateOf(StretchRoutines.more.any { it.key == defaultKey }) }
    var aboutKey by rememberSaveable { mutableStateOf(initialAbout) }

    var selectedAreaFilter by remember { mutableStateOf<BodyArea?>(null) }
    var previewMove by remember { mutableStateOf<StretchMove?>(null) }

    val displayedMoves = remember(selectedAreaFilter) {
        selectedAreaFilter?.let { StretchMoves.forArea(it) } ?: StretchMoves.ALL
    }

    // Each move's figure solves its balance once per launch: one a frame now, not mid-scroll on the first fling into the library.
    // Only once Stretching is the page shown: as a neighbor page it stays composed, and the work would land on Lifting's frames.
    val shown = settledHere()
    LaunchedEffect(shown) { if (shown) for (move in StretchMoves.ALL) withFrameNanos { Motions.of(move) } }

    // Lazy, so the move library below the fold, a 3D figure per row, composes only as it scrolls in.
    LazyScreenFrame(bottom = {
        BottomBar { PillButton("Start routine", { onStartRoutine(currentRoutine) }, Modifier.weight(1f), primary = true, enabled = currentRoutine.moves.isNotEmpty()) }
    }) {
        item { Column {
            NavRow { IconButton(Icons.settings, "Settings", onSettings, tint = p.fg2) }
            WorkoutSwitch(2, onMode)
            Spacer(Modifier.height(Space.xxl))
            Txt(currentRoutine.name, Type.largeTitle)
            Spacer(Modifier.height(Space.xs))
            Txt(routineMeta(currentRoutine), Type.subhead, color = p.fg2)

            GroupHeader("Routines")
            Group {
                @Composable
                fun RoutineRow(routine: StretchRoutine, first: Boolean) {
                    val selected = routine.key == currentRoutine.key
                    val about = aboutKey == routine.key
                    GroupRow(first = first, onClick = { selectedKey = routine.key }) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt(routine.name, if (selected) Type.body.copy(fontWeight = FontWeight.SemiBold) else Type.body, Modifier.weight(1f, fill = false), maxLines = 1)
                                if (routine.key == defaultKey) {
                                    Spacer(Modifier.width(Space.xs))
                                    Txt("Default", Type.footnote, color = p.fg2)
                                }
                            }
                            Txt(routineMeta(routine), Type.footnote, color = p.fg2)
                        }
                        if (selected) Icon(Icons.check, p.accent, size = 18.dp)
                        // A built-in routine's eye button opens what it is for in place; a custom one's opens its page to edit, as a Wi-Fi network's does.
                        val label = if (routine.custom) "${routine.name} moves" else "About ${routine.name}"
                        Snug(28.dp, Modifier.padding(start = Space.sm)) { m ->
                            Pressable(
                                { if (routine.custom) onOpenRoutine(routine) else aboutKey = if (about) null else routine.key },
                                m.semantics { contentDescription = label }, CircleShape, label = label,
                            ) { Icon(if (routine.custom) Icons.info else Icons.eye, p.accent, 22.dp) }
                        }
                    }
                    if (!routine.custom) Disclosed(about) { RoutineAbout(routine) { onOpenRoutine(routine) } }
                }
                RoutineRow(routines.first(), first = true)
                StretchRoutines.popular.forEach { RoutineRow(it, first = false) }
                ShowMoreRow(moreShown, { moreShown = !moreShown })
                Disclosed(moreShown) { StretchRoutines.more.forEach { RoutineRow(it, first = false) } }
                state?.customStretches?.values?.forEach { RoutineRow(it, first = false) }
                AddRow("New routine", first = false, onClick = onNewRoutine)
            }

            GroupHeader("Moves")

            // Area Filter Chips
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                val allSelected = selectedAreaFilter == null
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(if (allSelected) p.accent else p.fill)
                        .clickable { selectedAreaFilter = null }
                        .padding(horizontal = Space.sm, vertical = 6.dp),
                ) {
                    Txt("All", Type.subhead, color = if (allSelected) p.onAccent else p.fg)
                }
                BodyArea.entries.forEach { area ->
                    val isSelected = selectedAreaFilter == area
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) p.accent else p.fill)
                            .clickable { selectedAreaFilter = if (isSelected) null else area }
                            .padding(horizontal = Space.sm, vertical = 6.dp),
                    ) {
                        Txt(area.displayName, Type.subhead, color = if (isSelected) p.onAccent else p.fg)
                    }
                }
            }

            Spacer(Modifier.height(Space.xs))
        } }

        itemsIndexed(displayedMoves, key = { _, move -> move.id }) { i, move ->
            GroupSlice(i, displayedMoves.size) {
                GroupRow(first = i == 0, onClick = { previewMove = move }) {
                    MoveThumb(move)
                    Spacer(Modifier.width(Space.sm))
                    Column(Modifier.weight(1f)) {
                        Txt(move.name, Type.body, maxLines = 1)
                        Txt("${move.targetArea.displayName} · ${move.holdSec}s", Type.footnote, color = p.fg2)
                    }
                    Icon(Icons.chevronRight, p.fg2, size = 16.dp)
                }
            }
        }
    }

    // Move Detail Modal Preview
    previewMove?.let { move ->
        MovePreviewDialog(move = move, onDismiss = { previewMove = null })
    }
}

/** What a built-in routine is, its goal, and when to pick it, then a way to its moves. */
@Composable
private fun RoutineAbout(routine: StretchRoutine, onMoves: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, bottom = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Txt(routine.description, Type.subhead)
        for ((label, text) in listOf("Goal" to routine.goal, "When" to routine.pick)) Row {
            Txt(label, Type.subhead.copy(fontWeight = FontWeight.SemiBold), Modifier.width(56.dp), p.fg2)
            Txt(text, Type.subhead, Modifier.weight(1f))
        }
        Pressable(onMoves, Modifier.heightIn(min = 44.dp).semantics { contentDescription = "${routine.name} moves" }, CircleShape, label = "${routine.name} moves") {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
                Txt("See moves", Type.subhead, color = p.accent)
                Icon(Icons.chevronRight, p.accent, 16.dp)
            }
        }
    }
}

private fun routineMeta(r: StretchRoutine) = "${r.estimatedMinutes} min · ${r.moves.size} ${if (r.moves.size == 1) "move" else "moves"}"

/** A move's form, still, in a small rounded well. */
@Composable
fun MoveThumb(move: StretchMove) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(LocalPalette.current.fill)) {
        FormView(Motions.of(move), Modifier.fillMaxSize(), focus = move.focus, still = 1f)
    }
}

/**
 * A routine's page: its moves, then Start. A custom routine is edited here: a move's hold from its row, Add move, and in
 * edit mode its name, order, removals and deletion. A built-in routine is read-only and offers Duplicate.
 */
@Composable
fun StretchRoutineScreen(
    routine: StretchRoutine, onBack: () -> Unit, onStart: () -> Unit, onDuplicate: () -> Unit,
    open: (SheetState) -> Unit, edit: ((AppState) -> List<Event>) -> Unit, initialEditing: Boolean = false,
) {
    val p = LocalPalette.current
    var editing by rememberSaveable { mutableStateOf(initialEditing) }
    var preview by remember { mutableStateOf<StretchMove?>(null) }
    val moves = routine.moves
    ScreenFrame(bottom = if (editing) null else ({
        BottomBar { PillButton("Start routine", onStart, Modifier.weight(1f), primary = true, enabled = moves.isNotEmpty()) }
    })) {
        NavRow(onBack) { if (routine.custom) EditButton(editing) { editing = !editing } }
        Txt(routine.name, Type.largeTitle, maxLines = 2)
        Spacer(Modifier.height(Space.xs))
        Txt(routineMeta(routine), Type.subhead, color = p.fg2)
        Spacer(Modifier.height(Space.xxl))
        if (editing) {
            Group {
                GroupRow(first = true, onClick = { open(SheetState.StretchName(routine.id, routine.name)) }) {
                    Txt("Name", Type.body)
                    Txt(routine.name, Type.body, Modifier.weight(1f).padding(start = Space.lg), p.fg2, maxLines = 1, align = TextAlign.End)
                }
            }
            if (moves.isNotEmpty()) {
                Spacer(Modifier.height(Space.xxl))
                Group {
                    // A move can appear twice, so each key counts its earlier copies.
                    val keys = moves.mapIndexed { i, m -> "${m.id}#${moves.take(i).count { it.id == m.id }}" }
                    EditRows(
                        moves.map { it.name }, keys, { "${moves[it].holdSec}s" }, removable = true,
                        onMove = { f, t -> edit { Commands.moveStretchMove(it, routine.id, f, t) } },
                        onRemove = { i -> edit { Commands.removeStretchMove(it, routine.id, i) } },
                    )
                }
            }
            Spacer(Modifier.height(Space.xxl))
            Group {
                GroupRow(first = true, onClick = { open(SheetState.DeleteStretchRoutine(routine.id)) }) { Txt("Delete routine", Type.body, color = p.red) }
            }
        } else {
            Group {
                moves.forEachIndexed { i, move ->
                    GroupRow(first = i == 0, onClick = { if (routine.custom) open(SheetState.StretchHold(routine.id, i, move.holdSec)) else preview = move }) {
                        MoveThumb(move)
                        Spacer(Modifier.width(Space.sm))
                        Column(Modifier.weight(1f)) {
                            Txt(move.name, Type.body, maxLines = 1)
                            Txt(move.targetArea.displayName, Type.footnote, color = p.fg2)
                        }
                        Txt("${move.holdSec}s", Type.body.tabular(), Modifier.padding(start = Space.sm), if (routine.custom) p.accent else p.fg2)
                    }
                }
                if (routine.custom) AddRow("Add move", first = moves.isEmpty()) { open(SheetState.AddStretchMove(routine.id)) }
            }
            if (!routine.custom) {
                Spacer(Modifier.height(Space.xxl))
                Group { GroupRow(first = true, onClick = onDuplicate) { Txt("Duplicate", Type.body, color = p.accent) } }
            }
        }
    }
    preview?.let { MovePreviewDialog(it) { preview = null } }
}

/**
 * Fullscreen guided stretch player with Canvas stick figure animation,
 * hold countdown timer, next-move preview, controls, and completion logging.
 */
@Composable
fun GuidedStretchPlayerScreen(
    routine: StretchRoutine,
    onBack: () -> Unit,
    onFinish: (StretchLogged) -> Unit,
    sound: Boolean = false,
    haptics: Boolean = true,
) {
    val p = LocalPalette.current
    val player = remember(routine) { StretchPlayerState(routine) }
    val isPaused = player.isPaused
    val context = LocalContext.current
    val coach = remember(sound, haptics) { StretchCoach(context, sound, haptics) }
    DisposableEffect(coach) { onDispose { coach.close() } }
    // The clock runs on frames, which stop when the screen sleeps: a phone on the mat stays awake while the player is open.
    val view = LocalView.current
    DisposableEffect(view, player.isFinished) {
        view.keepScreenOn = !player.isFinished
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(player, isPaused, player.isFinished) {
        if (isPaused) coach.hush()
        var last = withFrameNanos { it }
        var carry = 0L
        while (!player.isPaused && !player.isFinished) withFrameNanos { now ->
            carry += now - last
            last = now
            val ms = carry / 1_000_000
            carry -= ms * 1_000_000
            // A stalled frame (the app in the background) resumes where it left off rather than skipping ahead.
            player.advance(ms.coerceAtMost(250))
            player.drainCues().forEach(coach::play)
        }
    }

    val moveIndex = player.moveIndex
    val currentMove = player.currentMove
    val nextMove = player.nextMove

    if (player.isFinished || currentMove == null) {
        // Routine Complete View
        RoutineCompleteView(
            player = player,
            onBack = onBack,
            onFinish = {
                val logged = player.createLoggedEvent()
                onFinish(logged)
            },
        )
        return
    }

    ScreenFrame(
        bottom = {
            // Player Bottom Control Bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(p.bg)
                    .padding(horizontal = Space.lg, vertical = Space.md),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        icon = Icons.chevronLeft,
                        label = "Previous move",
                        onClick = {
                            coach.hush()
                            player.skipPrev()
                        },
                        tint = if (moveIndex > 0) p.fg else p.fg2.copy(alpha = 0.3f),
                    )

                    val pauseLabel = if (isPaused) "Resume" else "Pause"
                    Pressable(
                        player::togglePause, Modifier.size(56.dp).semantics { contentDescription = pauseLabel }, CircleShape, p.accent,
                        pressedFill = Color.Black.copy(alpha = 0.14f), label = pauseLabel,
                    ) {
                        Icon(if (isPaused) Icons.play else Icons.pause, p.onAccent, 24.dp)
                    }

                    IconButton(
                        icon = Icons.chevronRight,
                        label = "Next move",
                        onClick = {
                            coach.hush()
                            player.skipNext()
                        },
                        tint = p.fg,
                    )
                }
            }
        },
    ) {
        NavRow(onBack)

        // Routine header & progress
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(routine.name, Type.footnote, color = p.fg2)
            Txt("Move ${moveIndex + 1} of ${routine.moves.size}", Type.footnote.tabular(), color = p.fg2)
        }

        // Progress bar
        Spacer(Modifier.height(Space.xs))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(p.fill)
                // Read while drawing, so the bar creeps on every frame without recomposing the screen.
                .drawBehind { drawRoundRect(p.accent, size = Size(size.width * player.progressFraction, size.height), cornerRadius = CornerRadius(size.height / 2)) },
        )

        Spacer(Modifier.height(Space.md))

        // 3D form, orbitable
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(p.card).cardRim(p, 16.dp)
                .padding(Space.sm),
            contentAlignment = Alignment.Center,
        ) {
            // A dynamic move's figure keeps time with its reps; a still hold's loops at its own tempo.
            val reps = remember(player) { { player.motionSec } }
            FormView(Motions.of(currentMove), Modifier.fillMaxSize(), focus = currentMove.focus, mirror = player.isSecondSide, time = reps.takeIf { player.schedule?.pacing != null })
        }

        Spacer(Modifier.height(Space.md))

        // Target Area Chip + Move Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(p.fill)
                    .padding(horizontal = Space.xs, vertical = 4.dp),
            ) {
                Txt(currentMove.targetArea.displayName, Type.caption.copy(fontWeight = FontWeight.SemiBold), color = p.accent)
            }
            if (currentMove.isUnilateral) {
                Spacer(Modifier.width(Space.xs))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.fill)
                        .padding(horizontal = Space.xs, vertical = 4.dp),
                ) {
                    Txt(if (player.isSecondSide) "Side 2" else "Side 1", Type.caption, color = p.fg2)
                }
            }
        }

        Spacer(Modifier.height(Space.xs))
        Txt(currentMove.name, Type.title1)

        // Cue
        Spacer(Modifier.height(Space.xs))
        Txt(currentMove.cue, Type.body, color = p.fg)

        Spacer(Modifier.height(Space.lg))

        Box(Modifier.fillMaxWidth().padding(vertical = Space.sm), contentAlignment = Alignment.Center) { CountdownRing(player) }

        Spacer(Modifier.height(Space.md))

        // Next Move Preview Banner
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.fill)
                .padding(Space.sm),
        ) {
            if (nextMove != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Txt("UP NEXT", Type.caption.copy(fontWeight = FontWeight.Bold), color = p.accent)
                        Txt("${nextMove.name} (${nextMove.holdSec}s)", Type.subhead.copy(fontWeight = FontWeight.SemiBold))
                        Txt(nextMove.targetArea.displayName, Type.footnote, color = p.fg2)
                    }
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(p.elevated)) {
                        FormView(Motions.of(nextMove), Modifier.fillMaxSize(), focus = nextMove.focus, still = 1f)
                    }
                }
            } else {
                Txt("Last move", Type.subhead, color = p.accent, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The countdown under way, draining every frame: getting ready or switching sides in gray, the hold in the accent. A dynamic
 * hold adds an inner ring for the rep under way, a segment per phase, and names the phase and the rep.
 */
@Composable
private fun CountdownRing(player: StretchPlayerState) {
    val p = LocalPalette.current
    val ready = player.isGettingReady
    val pacing = player.schedule?.pacing
    val repPhase = player.repPhase
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 8.dp.toPx()
                fun ring(inset: Float, stroke: Float, color: Color, start: Float, sweep: Float, cap: StrokeCap) = drawArc(
                    color, start, sweep, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = Stroke(stroke, cap = cap),
                )
                ring(w / 2, w, p.fill, 0f, 360f, StrokeCap.Butt)
                val left = player.countdownMsLeft.toFloat() / (player.span?.lengthMs ?: 1L).coerceAtLeast(1L)
                ring(w / 2, w, if (ready) p.fg2 else p.accent, -90f, 360f * left, StrokeCap.Round)
                val rep = player.rep ?: return@Canvas
                if (pacing == null) return@Canvas
                val thin = 4.dp.toPx()
                val inset = w + 6.dp.toPx() + thin / 2
                val gap = if (pacing.phases.size > 1) 4f else 0f
                var start = -90f
                var done = 0.0
                for ((phase, sec) in pacing.phases) {
                    val sweep = (360 * sec / pacing.period).toFloat()
                    val filled = when {
                        phase == rep.phase -> rep.phaseFraction
                        done + sec <= rep.repFraction * pacing.period + 1e-9 -> 1f
                        else -> 0f
                    }
                    ring(inset, thin, p.fill, start + gap / 2, sweep - gap, StrokeCap.Butt)
                    ring(inset, thin, p.fg, start + gap / 2, (sweep - gap) * filled, StrokeCap.Butt)
                    start += sweep
                    done += sec
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RollingNumber(if (ready) player.readyRemaining else player.secondsRemaining, Type.largeTitle.tabular())
                // The phase is what to do now, so it reads at a glance; the unit under a still hold stays quiet.
                val phase = repPhase?.second?.label?.takeIf { pacing != null && pacing.phases.size > 1 }
                when {
                    ready -> Txt(if (player.isSecondSide) "Switch" else "Get ready", Type.caption, color = p.fg2)
                    phase != null -> Txt(phase, Type.headline)
                    else -> Txt("seconds", Type.caption, color = p.fg2)
                }
            }
        }
        // Shown from the start, so the ring does not jump when the reps begin.
        if (pacing != null) {
            Spacer(Modifier.height(Space.sm))
            val reps = player.schedule?.let { s -> s.reps(s.spans.first { it.stage == Stage.HOLD && it.side == player.span?.side }.lengthMs) } ?: 0
            Txt("Rep ${(repPhase?.first ?: 0) + 1} of $reps", Type.headline.tabular())
        }
    }
}

/** [n] in [style], each digit rolling down to its next value as it changes, like a clock's. */
@Composable
private fun RollingNumber(n: Int, style: TextStyle) {
    val text = n.toString()
    Row(Modifier.clearAndSetSemantics { this.text = AnnotatedString(text) }) {
        text.forEachIndexed { i, c ->
            // Keyed from the right, so the units stay the units when 10 becomes 9.
            key(text.length - i) {
                AnimatedContent(
                    c, label = "digit",
                    transitionSpec = {
                        val ease = tween<IntOffset>(260, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f))
                        (slideInVertically(ease) { -it } + fadeIn(tween(180))) togetherWith (slideOutVertically(ease) { it } + fadeOut(tween(120))) using SizeTransform(clip = true)
                    },
                ) { Txt("$it", style, color = LocalPalette.current.fg) }
            }
        }
    }
}

/**
 * Summary screen displayed when the routine completes.
 */
@Composable
private fun RoutineCompleteView(
    player: StretchPlayerState,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    val p = LocalPalette.current
    ScreenFrame {
        NavRow(onBack)
        Spacer(Modifier.height(Space.md))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(p.card).cardRim(p, 16.dp)
                .padding(Space.lg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(p.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.check, p.onAccent, size = 32.dp)
                }

                Spacer(Modifier.height(Space.md))
                Txt("Routine Complete!", Type.title1)
                Spacer(Modifier.height(Space.xs))
                Txt(player.routine.name, Type.subhead, color = p.fg2)

                Spacer(Modifier.height(Space.lg))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(Fmt.clock(player.elapsedTotalSec), Type.title2.tabular())
                        Txt("Duration", Type.footnote, color = p.fg2)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt("${player.completedMoves.size}", Type.title2.tabular())
                        Txt("Moves", Type.footnote, color = p.fg2)
                    }
                }

                Spacer(Modifier.height(Space.xxl))
                PillButton(
                    text = "Log & Finish",
                    onClick = onFinish,
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Dialog/modal previewing a single move's animation, cue, and details.
 */
@Composable
fun MovePreviewDialog(
    move: StretchMove,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    HoldPages(true)
    Box(
        Modifier
            .fillMaxSize()
            .background(p.scrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(p.card).cardRim(p, 16.dp)
                .clickable(enabled = false) {}
                .padding(Space.lg),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(p.fill)
                            .padding(horizontal = Space.xs, vertical = 4.dp),
                    ) {
                        Txt(move.targetArea.displayName, Type.caption.copy(fontWeight = FontWeight.SemiBold), color = p.accent)
                    }
                    IconButton(Icons.x, "Close", onDismiss, tint = p.fg2)
                }

                Spacer(Modifier.height(Space.xs))
                Txt(move.name, Type.title2)
                Txt("Hold: ${move.holdSec} seconds", Type.footnote, color = p.fg2)

                Spacer(Modifier.height(Space.md))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.bg)
                        .padding(Space.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    FormView(Motions.of(move), Modifier.fillMaxSize(), focus = move.focus)
                }

                Spacer(Modifier.height(Space.md))
                Txt(move.cue, Type.body, color = p.fg)

                Spacer(Modifier.height(Space.lg))
                PillButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
