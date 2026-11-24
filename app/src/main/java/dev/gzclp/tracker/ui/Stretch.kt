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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.StretchLogged
import dev.gzclp.tracker.stretch.AnimatedStickFigure
import dev.gzclp.tracker.stretch.BodyArea
import dev.gzclp.tracker.stretch.StickFigureCanvas
import dev.gzclp.tracker.stretch.StretchMove
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchPlayerState
import dev.gzclp.tracker.stretch.StretchRoutine
import dev.gzclp.tracker.stretch.StretchRoutines
import dev.gzclp.tracker.ui.Type.tabular
import kotlinx.coroutines.delay

/**
 * Main Stretching Screen: shows routine picker with coaching defaults disclaimer,
 * routine preview details, and interactive move library browser.
 */
@Composable
fun StretchScreen(
    state: AppState? = null,
    onBack: () -> Unit,
    onStartRoutine: (StretchRoutine) -> Unit,
) {
    val p = LocalPalette.current
    val routines = remember(state) { StretchRoutines.all(state) }
    var selectedRoutineId by remember { mutableStateOf(StretchRoutines.ID_AFTER_WORKOUT) }
    val currentRoutine = routines.firstOrNull { it.id == selectedRoutineId } ?: routines.first()

    var selectedAreaFilter by remember { mutableStateOf<BodyArea?>(null) }
    var previewMove by remember { mutableStateOf<StretchMove?>(null) }

    ScreenFrame {
        NavRow(onBack)
        Txt("Stretching", Type.largeTitle)
        Spacer(Modifier.height(Space.xs))
        Txt("Guided recovery & mobility sessions", Type.subhead, color = p.fg2)

        Spacer(Modifier.height(Space.md))
        GroupHeader("Routines")
        Group {
            routines.forEachIndexed { i, routine ->
                val selected = routine.id == selectedRoutineId
                GroupRow(first = i == 0, onClick = { selectedRoutineId = routine.id }) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Txt(routine.name, if (selected) Type.body.copy(fontWeight = FontWeight.SemiBold) else Type.body)
                            if (routine.id == StretchRoutines.ID_AFTER_WORKOUT) {
                                Spacer(Modifier.width(Space.xs))
                                Box(
                                    Modifier.clip(RoundedCornerShape(4.dp)).background(p.fill).padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Txt("Default", Type.caption, color = p.accent)
                                }
                            }
                        }
                        Txt("${routine.estimatedMinutes} min · ${routine.moves.size} moves · ${routine.frequency}", Type.footnote, color = p.fg2)
                    }
                    if (selected) {
                        Icon(Icons.check, p.accent, size = 18.dp)
                    }
                }
            }
        }
        GroupFooter(StretchRoutines.COACHING_DISCLAIMER)

        // Selected Routine Card
        Spacer(Modifier.height(Space.sm))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(p.elevated)
                .padding(Space.md),
        ) {
            Column {
                Txt(currentRoutine.name, Type.title3)
                Spacer(Modifier.height(Space.xs))
                Txt(currentRoutine.description, Type.subhead, color = p.fg2)
                currentRoutine.note?.let {
                    Spacer(Modifier.height(Space.xs))
                    Txt(it, Type.footnote, color = p.accent)
                }
                Spacer(Modifier.height(Space.md))
                PillButton(
                    text = "Start ${currentRoutine.name} (${currentRoutine.estimatedMinutes}m)",
                    onClick = { onStartRoutine(currentRoutine) },
                    primary = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(Space.lg))
        GroupHeader("Move Library (${StretchMoves.ALL.size} moves)")

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
                        StickFigureCanvas(
                            pose = move.endPose,
                            targetArea = move.targetArea,
                            modifier = Modifier.fillMaxSize().padding(2.dp),
                        )
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
    var secondsLeft by remember { mutableStateOf(player.secondsRemaining) }
    var moveIndex by remember { mutableStateOf(player.moveIndex) }
    var isPaused by remember { mutableStateOf(player.isPaused) }
    var isFinished by remember { mutableStateOf(player.isFinished) }

    // Tick loop
    LaunchedEffect(isPaused, isFinished) {
        while (!isPaused && !isFinished) {
            delay(1000L)
            player.tick()
            secondsLeft = player.secondsRemaining
            moveIndex = player.moveIndex
            isFinished = player.isFinished
        }
    }

    val currentMove = player.currentMove
    val nextMove = player.nextMove

    if (isFinished || currentMove == null) {
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
                            secondsLeft = player.secondsRemaining
                            moveIndex = player.moveIndex
                            isFinished = player.isFinished
                        },
                        tint = if (moveIndex > 0) p.fg else p.fg2.copy(alpha = 0.3f),
                    )

                    // Big Play/Pause Button
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(p.accent)
                            .clickable {
                                player.togglePause()
                                isPaused = player.isPaused
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Txt(
                            if (isPaused) "Resume" else "Pause",
                            Type.subhead.copy(fontWeight = FontWeight.SemiBold),
                            color = p.onAccent,
                        )
                    }

                    IconButton(
                        icon = Icons.chevronRight,
                        label = "Next move",
                        onClick = {
                            player.skipNext()
                            secondsLeft = player.secondsRemaining
                            moveIndex = player.moveIndex
                            isFinished = player.isFinished
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

        // Stick Figure Animation Canvas
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(p.elevated)
                .padding(Space.sm),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedStickFigure(
                move = currentMove,
                isHolding = !isPaused,
                modifier = Modifier.fillMaxSize(),
            )
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
            val holdTotal = currentMove.holdSec.toFloat().coerceAtLeast(1f)
            val ringProgress = (secondsLeft.toFloat() / holdTotal).coerceIn(0f, 1f)

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
                        color = p.accent,
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
                    Txt("seconds", Type.caption, color = p.fg2)
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
                        StickFigureCanvas(
                            pose = nextMove.endPose,
                            targetArea = nextMove.targetArea,
                            modifier = Modifier.fillMaxSize().padding(2.dp),
                        )
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
                    AnimatedStickFigure(
                        move = move,
                        isHolding = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.height(Space.md))
                Txt(move.cue, Type.body, color = p.fg)

                Spacer(Modifier.height(Space.lg))
                PillButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
