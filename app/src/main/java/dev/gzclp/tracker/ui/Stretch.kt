package dev.gzclp.tracker.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Event
import dev.gzclp.tracker.log.StretchLogged
import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.stretch.BodyArea
import dev.gzclp.tracker.stretch.StretchMove
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchPlayerState
import dev.gzclp.tracker.stretch.StretchRoutine
import dev.gzclp.tracker.stretch.StretchRoutines
import dev.gzclp.tracker.ui.Type.tabular
import kotlinx.coroutines.delay

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

    ScreenFrame(bottom = {
        BottomBar { PillButton("Start routine", { onStartRoutine(currentRoutine) }, Modifier.weight(1f), primary = true, enabled = currentRoutine.moves.isNotEmpty()) }
    }) {
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
                    // A built-in routine's info button opens what it is for in place; a custom one's opens its page to edit, as a Wi-Fi network's does.
                    val label = if (routine.custom) "${routine.name} moves" else "About ${routine.name}"
                    Snug(28.dp, Modifier.padding(start = Space.sm)) { m ->
                        Pressable(
                            { if (routine.custom) onOpenRoutine(routine) else aboutKey = if (about) null else routine.key },
                            m.semantics { contentDescription = label }, CircleShape, label = label,
                        ) { Icon(Icons.info, p.accent, 22.dp) }
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
        val displayedMoves = remember(selectedAreaFilter) {
            selectedAreaFilter?.let { StretchMoves.forArea(it) } ?: StretchMoves.ALL
        }

        Group {
            displayedMoves.forEachIndexed { i, move ->
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
    Column(Modifier.fillMaxWidth().padding(start = Space.lg, end = Space.lg, bottom = Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Txt(routine.description, Type.subhead)
        for ((label, text) in listOf("Goal" to routine.goal, "When" to routine.pick)) Row {
            Txt(label, Type.subhead.copy(fontWeight = FontWeight.SemiBold), Modifier.width(56.dp), p.fg2)
            Txt(text, Type.subhead, Modifier.weight(1f))
        }
        Pressable(onMoves, Modifier.semantics { contentDescription = "${routine.name} moves" }, CircleShape, label = "${routine.name} moves") {
            Row(Modifier.padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
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
) {
    val p = LocalPalette.current
    val player = remember(routine) { StretchPlayerState(routine) }
    val isPaused = player.isPaused
    LaunchedEffect(player, isPaused, player.isFinished) {
        while (!player.isPaused && !player.isFinished) {
            delay(1000L)
            player.tick()
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
                .background(p.fill),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(player.progressFraction)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(p.accent),
            )
        }

        Spacer(Modifier.height(Space.md))

        // 3D form, orbitable
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(p.elevated)
                .padding(Space.sm),
            contentAlignment = Alignment.Center,
        ) {
            FormView(Motions.of(currentMove), Modifier.fillMaxSize(), focus = currentMove.focus, mirror = player.isSecondSide)
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

        // Large Countdown Clock Ring
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Space.sm),
            contentAlignment = Alignment.Center,
        ) {
            val ready = player.isGettingReady
            val secondsLeft = if (ready) player.readyRemaining else player.secondsRemaining
            val total = player.countdownSec
            val ringProgress = (secondsLeft.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)

            Box(
                Modifier.size(130.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    // Background circle track
                    drawCircle(
                        color = p.fill,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                    )
                    // Active countdown arc
                    drawArc(
                        color = if (ready) p.fg2 else p.accent,
                        startAngle = -90f,
                        sweepAngle = 360f * ringProgress,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(
                        text = "$secondsLeft",
                        style = Type.largeTitle.tabular(),
                        color = p.fg,
                    )
                    Txt(if (!ready) "seconds" else if (player.isSecondSide) "Switch" else "Get ready", Type.caption, color = p.fg2)
                }
            }
        }

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
                .background(p.elevated)
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
                .background(p.elevated)
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
