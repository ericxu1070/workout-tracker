package dev.gzclp.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.AppModel
import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.Scheme
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Exercise
import dev.gzclp.tracker.log.SetKey
import dev.gzclp.tracker.log.mondayOf
import dev.gzclp.tracker.ui.Type.tabular
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Muscle groups for weekly volume tracking.
 * Default target: 10 sets per group per week.
 */
enum class MuscleGroup(val label: String, val targetSets: Int = 10) {
    Chest("Chest", 10),
    Pull("Pull", 10),
    Shoulders("Shoulders", 10),
    Legs("Legs", 10),
    Arms("Arms", 10),
    Core("Core", 10);

    companion object {
        fun of(exerciseId: String): MuscleGroup {
            val id = exerciseId.lowercase()
            return when {
                id == "romanian_deadlift" || id in listOf("squat", "leg_press", "leg_curl", "leg_extension", "calf_raise") ||
                    id.contains("squat") || id.contains("leg") || id.contains("calf") || id.contains("lunge") || id.contains("quad") || id.contains("hamstring") -> Legs

                id in listOf("bench", "incline_db_press", "chest_fly", "push_up", "dip", "dips") ||
                    id.contains("chest") || id.contains("bench") || id.contains("push_up") || id.contains("dip") -> Chest

                id in listOf("barbell_curl", "hammer_curl", "tricep_pushdown", "overhead_tricep_extension") ||
                    id.contains("curl") || id.contains("tricep") || id.contains("bicep") || id.contains("arm") -> Arms

                id in listOf("ohp", "lateral_raise") ||
                    id.contains("shoulder") || id.contains("delt") || id.contains("ohp") || id.contains("overhead_press") -> Shoulders

                id.contains("crunch") || id.contains("plank") || id.contains("ab") || id.contains("core") -> Core

                id in listOf("deadlift", "barbell_row", "lat_pulldown", "dumbbell_row", "seated_cable_row", "pull_up", "chin_up", "face_pull", "rear_delt_fly") ||
                    id.contains("row") || id.contains("pulldown") || id.contains("pull_up") || id.contains("chin_up") || id.contains("lat") || id.contains("back") || id.contains("deadlift") || id.contains("pull") -> Pull

                else -> Pull
            }
        }
    }
}

/** Counts completed sets for the Monday-Sunday week containing [date]. */
fun AppState.weeklyMuscleGroupSets(date: LocalDate = LocalDate.now()): Map<MuscleGroup, Int> {
    val monday = mondayOf(date)
    val sunday = monday.plusDays(6)
    val counts = MuscleGroup.entries.associateWith { 0 }.toMutableMap()
    for (s in sessions) {
        val d = s.startedAt.toLocalDate()
        if (d in monday..sunday) {
            for (set in s.sets) {
                if (set.actualReps > 0) {
                    val group = MuscleGroup.of(set.exercise)
                    counts[group] = (counts[group] ?: 0) + 1
                }
            }
        }
    }
    active?.let { a ->
        if (a.startedAt.toLocalDate() in monday..sunday) {
            for (set in a.sets.values) {
                if (set.event.actualReps > 0) {
                    val group = MuscleGroup.of(set.event.exercise)
                    counts[group] = (counts[group] ?: 0) + 1
                }
            }
        }
    }
    return counts
}

/** Exercises logged in past sessions, newest first. */
fun recentExercises(state: AppState, limit: Int = 8): List<String> =
    state.sessions.asReversed()
        .flatMap { s -> s.sets.map { it.exercise } }
        .distinct()
        .take(limit)

/** State of an exercise in an open session. */
data class OpenExercise(
    val exercise: Exercise,
    val targetWeight: Int,
    val targetReps: Int,
    val setsCount: Int,
    val isMatchingProgramSlot: Boolean,
)

/** In-memory coordinator for the active open session. */
object OpenSessionManager {
    var isActive by mutableStateOf(false)
    var isBlank by mutableStateOf(false)
    val exercises = mutableStateListOf<OpenExercise>()
    var showCountSheet by mutableStateOf(false)

    fun start(fromToday: Boolean, state: AppState, onStartWorkout: () -> Unit) {
        isActive = true
        isBlank = !fromToday
        exercises.clear()
        showCountSheet = false
        if (fromToday) {
            val dayExercises = state.exercises(state.nextDay)
            for (ex in dayExercises) {
                val reps = ex.scheme.reps
                val weight = ex.weight ?: Commands.suggestWeight(state, ex.id, reps)
                exercises.add(OpenExercise(ex, weight, reps, ex.scheme.sets, isMatchingProgramSlot = true))
            }
        }
        onStartWorkout()
    }

    fun addExercise(state: AppState, exerciseId: String) {
        if (exercises.any { it.exercise.id == exerciseId }) return
        val matchingSlot = state.exercises(state.nextDay).firstOrNull { it.id == exerciseId }
        val reps = matchingSlot?.scheme?.reps ?: 10
        val weight = matchingSlot?.weight ?: Commands.suggestWeight(state, exerciseId, reps)
        val ex = matchingSlot?.copy(weight = weight) ?: Exercise(
            id = exerciseId,
            name = state.name(exerciseId),
            tier = Tier.CUSTOM,
            scheme = Scheme(3, reps, false),
            weight = weight,
        )
        exercises.add(OpenExercise(ex, weight, reps, matchingSlot?.scheme?.sets ?: 3, isMatchingProgramSlot = matchingSlot != null))
    }

    fun removeExercise(exerciseId: String) {
        exercises.removeAll { it.exercise.id == exerciseId }
    }

    fun updateReps(state: AppState, exerciseId: String, newReps: Int) {
        val idx = exercises.indexOfFirst { it.exercise.id == exerciseId }
        if (idx < 0) return
        val current = exercises[idx]
        val clampedReps = newReps.coerceIn(1, 50)
        // Changing target reps recalculates the weight immediately
        val newWeight = Commands.suggestWeight(state, exerciseId, clampedReps)
        val updatedEx = current.exercise.copy(
            scheme = current.exercise.scheme.copy(reps = clampedReps),
            weight = newWeight,
        )
        exercises[idx] = current.copy(
            exercise = updatedEx,
            targetReps = clampedReps,
            targetWeight = newWeight,
        )
    }

    fun updateWeight(exerciseId: String, newWeight: Int) {
        val idx = exercises.indexOfFirst { it.exercise.id == exerciseId }
        if (idx < 0) return
        val current = exercises[idx]
        val clampedWeight = newWeight.coerceAtLeast(0)
        val updatedEx = current.exercise.copy(weight = clampedWeight)
        exercises[idx] = current.copy(
            exercise = updatedEx,
            targetWeight = clampedWeight,
        )
    }

    fun addSet(exerciseId: String) {
        val idx = exercises.indexOfFirst { it.exercise.id == exerciseId }
        if (idx < 0) return
        val current = exercises[idx]
        exercises[idx] = current.copy(setsCount = current.setsCount + 1)
    }

    fun removeSet(exerciseId: String) {
        val idx = exercises.indexOfFirst { it.exercise.id == exerciseId }
        if (idx < 0) return
        val current = exercises[idx]
        if (current.setsCount > 1) {
            exercises[idx] = current.copy(setsCount = current.setsCount - 1)
        }
    }

    fun reset() {
        isActive = false
        isBlank = false
        exercises.clear()
        showCountSheet = false
    }
}

/** Weekly volume per muscle group bar on the Workout tab ("Pull 8 / 12 this week"). */
@Composable
fun WeeklyVolumeSection(
    state: AppState,
    modifier: Modifier = Modifier,
    targets: Map<MuscleGroup, Int> = emptyMap(),
) {
    val p = LocalPalette.current
    val counts = state.weeklyMuscleGroupSets()
    Group(modifier) {
        GroupHeader("Weekly volume")
        MuscleGroup.entries.forEachIndexed { i, group ->
            val done = counts[group] ?: 0
            val target = targets[group] ?: group.targetSets
            val frac = (done.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
            GroupRow(first = i == 0) {
                Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Txt(group.label, Type.headline, Modifier.weight(1f))
                        Txt("${group.label} $done / $target this week", Type.footnote, color = if (done >= target) p.accent else p.fg2)
                    }
                    Spacer(Modifier.height(Space.xs))
                    Box(
                        Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(p.separator),
                    ) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(frac)
                                .clip(RoundedCornerShape(3.dp))
                                .background(p.accent),
                        )
                    }
                }
            }
        }
    }
}

/** Sheet asking to start blank or from today's scheduled day. */
@Composable
fun OpenSessionStartSheet(
    state: AppState,
    onStartFromToday: () -> Unit,
    onStartBlank: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    val day = state.nextDay
    Column(
        Modifier.fillMaxWidth().background(p.bg).padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt("Open session", Type.title2)
        Spacer(Modifier.height(Space.sm))
        Txt("Choose how to begin your session:", Type.subhead, color = p.fg2)
        Spacer(Modifier.height(Space.xl))

        PillButton(
            "Start from today's day",
            onClick = onStartFromToday,
            modifier = Modifier.fillMaxWidth(),
            primary = true,
        )
        Spacer(Modifier.height(Space.xs))
        Txt("Prefills ${day.title}'s lifts", Type.caption, color = p.fg2)

        Spacer(Modifier.height(Space.lg))

        PillButton(
            "Start blank",
            onClick = onStartBlank,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.xs))
        Txt("Empty session, add exercises as you go", Type.caption, color = p.fg2)

        Spacer(Modifier.height(Space.xl))
        SmallPill("Cancel", onDismiss)
    }
}

/** Sheet shown at finish: "Count toward your program?" */
@Composable
fun OpenSessionCountSheet(
    onYes: () -> Unit,
    onNo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxWidth().background(p.bg).padding(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt("Count toward your program?", Type.title2)
        Spacer(Modifier.height(Space.md))
        Txt(
            "Yes advances matching program slots as if done in a program session.\n\nNo leaves your program state unchanged, but your strength model still learns from every set.",
            Type.body,
            color = p.fg2,
            align = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.xxl))

        PillButton(
            "Yes, count toward program",
            onClick = onYes,
            modifier = Modifier.fillMaxWidth(),
            primary = true,
        )
        Spacer(Modifier.height(Space.md))

        PillButton(
            "No, off-plan session",
            onClick = onNo,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.lg))

        SmallPill("Cancel", onDismiss)
    }
}

/** Sheet for adding an exercise from recents or searching the catalog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExerciseSheet(
    state: AppState,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val recents = remember(state) { recentExercises(state) }
    val allMoves = state.moves.values.toList()
    val filtered = remember(query, allMoves) {
        if (query.isBlank()) allMoves
        else allMoves.filter { it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true) }
    }

    Column(
        Modifier.fillMaxWidth().heightIn(max = 500.dp).background(p.bg).padding(Space.lg),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Txt("Add exercise", Type.title2, Modifier.weight(1f))
            SmallPill("Close", onDismiss)
        }
        Spacer(Modifier.height(Space.md))

        // Search input
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(Radius.md))
                .background(p.fill).padding(horizontal = Space.md),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) Txt("Search exercises...", Type.body, color = p.fg3)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = Type.body.copy(color = p.fg),
                singleLine = true,
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Space.md))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (recents.isNotEmpty() && query.isBlank()) {
                Txt("Recent exercises", Type.footnote, color = p.fg2)
                Spacer(Modifier.height(Space.sm))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    recents.forEach { id ->
                        val name = state.name(id)
                        Pressable(
                            onClick = { onSelect(id) },
                            modifier = Modifier.clip(RoundedCornerShape(Radius.md)).background(p.fill).padding(horizontal = Space.md, vertical = Space.sm),
                        ) {
                            Txt(name, Type.subhead, color = p.accent)
                        }
                    }
                }
                Spacer(Modifier.height(Space.lg))
            }

            Txt("All exercises", Type.footnote, color = p.fg2)
            Spacer(Modifier.height(Space.sm))
            Group {
                filtered.forEachIndexed { i, move ->
                    GroupRow(first = i == 0, onClick = { onSelect(move.id) }) {
                        Column(Modifier.weight(1f)) {
                            Txt(move.name, Type.headline)
                            Txt(MuscleGroup.of(move.id).label, Type.footnote, color = p.fg2)
                        }
                        Icon(Icons.chevronRight, p.fg3, 16.dp)
                    }
                }
            }
        }
    }
}

/** The active Open Session screen. */
@Composable
fun OpenSessionScreen(
    state: AppState,
    now: OffsetDateTime,
    onLog: (Exercise, Int, Int, Int, Double?) -> Unit,
    onClear: (Exercise, Int) -> Unit,
    onFinish: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val p = LocalPalette.current
    val a = state.active
    var showAddExercise by remember { mutableStateOf(false) }

    ScreenFrame(bottom = {
        BottomBar {
            PillButton(
                "Finish session",
                onClick = { OpenSessionManager.showCountSheet = true },
                modifier = Modifier.weight(1f),
                primary = true,
            )
        }
    }) {
        NavRow(onBack = onCancel) {
            SmallPill("Add exercise", { showAddExercise = true })
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Open session", Type.largeTitle)
                val elapsed = if (a != null) Duration.between(a.startedAt, now).seconds else 0L
                Txt(Fmt.clock(elapsed) + " elapsed", Type.subhead.tabular(), color = p.fg2)
            }
        }

        Spacer(Modifier.height(Space.lg))
        WeeklyVolumeSection(state)

        if (OpenSessionManager.exercises.isEmpty()) {
            Spacer(Modifier.height(Space.xxl))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(p.elevated).padding(Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt("No exercises added yet", Type.headline)
                Spacer(Modifier.height(Space.sm))
                Txt("Add exercises from recents or search to get started.", Type.body, color = p.fg2, align = TextAlign.Center)
                Spacer(Modifier.height(Space.lg))
                PillButton("Add exercise", { showAddExercise = true }, primary = true)
            }
        } else {
            OpenSessionManager.exercises.forEach { openEx ->
                Spacer(Modifier.height(Space.x3))
                OpenExerciseCard(
                    state = state,
                    openEx = openEx,
                    onLog = onLog,
                    onClear = onClear,
                    onUpdateReps = { newReps -> OpenSessionManager.updateReps(state, openEx.exercise.id, newReps) },
                    onUpdateWeight = { newWeight -> OpenSessionManager.updateWeight(openEx.exercise.id, newWeight) },
                    onAddSet = { OpenSessionManager.addSet(openEx.exercise.id) },
                    onRemoveSet = { OpenSessionManager.removeSet(openEx.exercise.id) },
                    onRemoveExercise = { OpenSessionManager.removeExercise(openEx.exercise.id) },
                )
            }
        }

        Spacer(Modifier.height(Space.xxl))
        SmallPill("+ Add another exercise", { showAddExercise = true }, Modifier.align(Alignment.CenterHorizontally))
    }

    if (showAddExercise) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AddExerciseSheet(
                state = state,
                onSelect = { id ->
                    OpenSessionManager.addExercise(state, id)
                    showAddExercise = false
                },
                onDismiss = { showAddExercise = false },
            )
        }
    }

    if (OpenSessionManager.showCountSheet) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            OpenSessionCountSheet(
                onYes = {
                    OpenSessionManager.showCountSheet = false
                    onFinish(true)
                },
                onNo = {
                    OpenSessionManager.showCountSheet = false
                    onFinish(false)
                },
                onDismiss = { OpenSessionManager.showCountSheet = false },
            )
        }
    }
}

/** Card for one exercise inside the open session with +/- steppers and set rows. */
@Composable
fun OpenExerciseCard(
    state: AppState,
    openEx: OpenExercise,
    onLog: (Exercise, Int, Int, Int, Double?) -> Unit,
    onClear: (Exercise, Int) -> Unit,
    onUpdateReps: (Int) -> Unit,
    onUpdateWeight: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onRemoveExercise: () -> Unit,
) {
    val p = LocalPalette.current
    val ex = openEx.exercise
    val sets = state.active?.sets.orEmpty()
    val plates = LocalPlates.current

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(p.elevated).padding(Space.lg),
    ) {
        // Exercise header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(ex.name, Type.title3, maxLines = 1)
                if (openEx.isMatchingProgramSlot) {
                    Txt("Matches program slot (${ex.tier.label})", Type.footnote, color = p.accent)
                } else {
                    Txt(MuscleGroup.of(ex.id).label, Type.footnote, color = p.fg2)
                }
            }
            SmallPill("Remove", onRemoveExercise)
        }

        Spacer(Modifier.height(Space.md))

        // +/- steppers for weight and reps
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            // Weight stepper
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.md)).background(p.fill).padding(Space.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt("Target weight", Type.caption, color = p.fg2)
                Spacer(Modifier.height(Space.xs))
                Txt("${openEx.targetWeight} lb", Type.headline.tabular(), color = p.accent)
                Spacer(Modifier.height(Space.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Pressable(
                        onClick = { onUpdateWeight(openEx.targetWeight - 5) },
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        fill = p.elevated,
                        label = "Decrease weight",
                        repeat = true,
                    ) {
                        Icon(Icons.minus, p.fg, 18.dp)
                    }
                    Pressable(
                        onClick = { onUpdateWeight(openEx.targetWeight + 5) },
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        fill = p.elevated,
                        label = "Increase weight",
                        repeat = true,
                    ) {
                        Icon(Icons.plus, p.fg, 18.dp)
                    }
                }
            }

            // Reps stepper (changing target reps recalculates weight immediately)
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(Radius.md)).background(p.fill).padding(Space.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt("Target reps", Type.caption, color = p.fg2)
                Spacer(Modifier.height(Space.xs))
                Txt("${openEx.targetReps} reps", Type.headline.tabular())
                Spacer(Modifier.height(Space.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Pressable(
                        onClick = { onUpdateReps(openEx.targetReps - 1) },
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        fill = p.elevated,
                        label = "Decrease reps",
                        repeat = true,
                    ) {
                        Icon(Icons.minus, p.fg, 18.dp)
                    }
                    Pressable(
                        onClick = { onUpdateReps(openEx.targetReps + 1) },
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        fill = p.elevated,
                        label = "Increase reps",
                        repeat = true,
                    ) {
                        Icon(Icons.plus, p.fg, 18.dp)
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.md))

        // Sets column
        Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            for (i in 1..openEx.setsCount) {
                val key = SetKey(ex.id, ex.tier, i)
                val logged = sets[key]?.event
                SetRow(
                    index = i,
                    amrap = false,
                    targetReps = openEx.targetReps,
                    defaultWeight = openEx.targetWeight,
                    logged = logged,
                    current = logged == null,
                    exName = ex.name,
                    plates = plates.takeIf { ex.id in Catalog.barbell },
                    onLog = { w, r, rpe -> onLog(ex, i, w, r, rpe) },
                    onClear = { onClear(ex, i) },
                )
            }
        }

        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallPill("+ Add set", onAddSet)
            if (openEx.setsCount > 1) {
                SmallPill("- Remove set", onRemoveSet)
            }
        }
    }
}
