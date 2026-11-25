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

/** The Workout tab's Stretching page: every routine to pick and start, then the move library. */
@Composable
fun StretchScreen(
    state: AppState?,
    defaultId: String,
    onMode: (Int) -> Unit,
    onSettings: () -> Unit,
    onStartRoutine: (StretchRoutine) -> Unit,
) {
    val p = LocalPalette.current
    val routines = remember(state) { StretchRoutines.all(state) }
    // Flexibility rotates by day, so its routines share an id; the name tells them apart.
    val defaultName = remember(state, defaultId) { StretchRoutines.of(defaultId, state).name }
    var selectedName by remember(defaultName) { mutableStateOf(defaultName) }
    val currentRoutine = routines.firstOrNull { it.name == selectedName } ?: routines.first()

    var selectedAreaFilter by remember { mutableStateOf<BodyArea?>(null) }
    var previewMove by remember { mutableStateOf<StretchMove?>(null) }

    ScreenFrame(bottom = { BottomBar { PillButton("Start routine", { onStartRoutine(currentRoutine) }, Modifier.weight(1f), primary = true) } }) {
        NavRow { IconButton(Icons.settings, "Settings", onSettings, tint = p.fg2) }
        WorkoutSwitch(2, onMode)
        Spacer(Modifier.height(Space.xxl))
        Txt(currentRoutine.name, Type.largeTitle)
        Spacer(Modifier.height(Space.xs))
        Txt(routineMeta(currentRoutine), Type.subhead, color = p.fg2)

        GroupHeader("Routines")
        Group {
            routines.forEachIndexed { i, routine ->
                val selected = routine.name == currentRoutine.name
                GroupRow(first = i == 0, onClick = { selectedName = routine.name }) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Txt(routine.name, if (selected) Type.body.copy(fontWeight = FontWeight.SemiBold) else Type.body)
                            if (routine.name == defaultName) {
                                Spacer(Modifier.width(Space.xs))
                                Txt("Default", Type.footnote, color = p.fg2)
                            }
                        }
                        Txt(routineMeta(routine), Type.footnote, color = p.fg2)
                    }
                    if (selected) Icon(Icons.check, p.accent, size = 18.dp)
                }
            }
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
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(p.fill)) {
                        FormView(Motions.of(move), Modifier.fillMaxSize(), area = move.targetArea, still = 1f)
                    }
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

private fun routineMeta(r: StretchRoutine) = "${r.estimatedMinutes} min · ${r.moves.size} moves"

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
            FormView(Motions.of(currentMove), Modifier.fillMaxSize(), area = currentMove.targetArea)
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
            if (currentMove.isBilateral) {
                Spacer(Modifier.width(Space.xs))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(p.fill)
                        .padding(horizontal = Space.xs, vertical = 4.dp),
                ) {
                    Txt("Bilateral", Type.caption, color = p.fg2)
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
            val total = if (ready) StretchPlayerState.READY_SEC else currentMove.holdSec
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
                    Txt(if (ready) "Get ready" else "seconds", Type.caption, color = p.fg2)
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
                        FormView(Motions.of(nextMove), Modifier.fillMaxSize(), area = nextMove.targetArea, still = 1f)
                    }
                }
            } else {
                Txt("Last move of the routine!", Type.subhead, color = p.accent, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                    FormView(Motions.of(move), Modifier.fillMaxSize(), area = move.targetArea)
                }

                Spacer(Modifier.height(Space.md))
                Txt(move.cue, Type.body, color = p.fg)

                Spacer(Modifier.height(Space.lg))
                PillButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
