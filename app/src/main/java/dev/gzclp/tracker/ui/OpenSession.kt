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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
        private val catalog: Map<String, MuscleGroup> = mapOf(
            Legs to "squat leg_press leg_curl leg_extension calf_raise romanian_deadlift pistol_squat",
            Chest to "bench incline_db_press chest_fly push_up dip",
            Arms to "barbell_curl hammer_curl tricep_pushdown overhead_tricep_extension",
            Shoulders to "ohp lateral_raise",
            Pull to "deadlift barbell_row lat_pulldown dumbbell_row seated_cable_row face_pull rear_delt_fly pull_up chin_up muscle_up inverted_row",
        ).flatMap { (g, ids) -> ids.split(" ").map { it to g } }.toMap()

        /** Catalog moves by table; a custom exercise by whole words in its id, so "seated_cable_row" never reads as "ab". */
        fun of(exerciseId: String): MuscleGroup {
            val id = exerciseId.lowercase().removePrefix("weighted_").removePrefix("assisted_")
            catalog[id]?.let { return it }
            val words = id.split('_', '-', ' ').toSet()
            fun has(vararg w: String) = w.any { it in words }
            return when {
                has("squat", "leg", "legs", "calf", "lunge", "quad", "quads", "hamstring", "glute") -> Legs
                has("chest", "bench", "fly", "dip") -> Chest
                has("curl", "tricep", "triceps", "bicep", "biceps") -> Arms
                has("crunch", "plank", "ab", "abs", "core", "situp") -> Core
                has("shoulder", "delt", "ohp", "raise") -> Shoulders
                else -> Pull
            }
        }
    }
}

/** Counts completed sets for the Monday-Sunday week containing [date]; a top-sets line counts every set of its exercise. */
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
                    counts[group] = (counts[group] ?: 0) + (set.sets ?: 1)
                }
            }
        }
    }
    active?.let { a ->
        if (a.startedAt.toLocalDate() in monday..sunday) {
            for (set in a.sets.values) {
                if (set.event.actualReps > 0) {
                    val group = MuscleGroup.of(set.event.exercise)
                    counts[group] = (counts[group] ?: 0) + (set.event.sets ?: 1)
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
    /** The logged session these exercises belong to; null between [start] and the session_start landing. */
    private var sessionId: String? = null

    fun start(fromToday: Boolean, state: AppState, onStartWorkout: () -> Unit) {
        isActive = true
        sessionId = null
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

    /** Binds to the open session in the log, rebuilding the cards from its logged sets after a restart or a stale start. */
    fun attach(state: AppState) {
        val a = state.active?.takeIf { it.open } ?: return
        if (isActive && sessionId == a.id) return
        if (!isActive || sessionId != null) {
            exercises.clear()
            isBlank = true
            showCountSheet = false
        }
        isActive = true
        sessionId = a.id
        a.sets.keys.groupBy { it.exercise }.forEach { (id, keys) ->
            addExercise(state, id)
            val i = exercises.indexOfFirst { it.exercise.id == id }
            if (i >= 0) exercises[i] = exercises[i].copy(setsCount = maxOf(exercises[i].setsCount, keys.maxOf { it.setIndex }))
        }
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
        sessionId = null
        isBlank = false
        exercises.clear()
        showCountSheet = false
    }
}

/** Sets per muscle group in the week of [date] against each group's target ("8 of 10"); groups at 0 are left out, and nothing shows at all 0. */
@Composable
fun WeeklyVolumeSection(
    state: AppState,
    modifier: Modifier = Modifier,
    targets: Map<MuscleGroup, Int> = emptyMap(),
    date: java.time.LocalDate = java.time.LocalDate.now(),
) {
    val p = LocalPalette.current
    val counts = state.weeklyMuscleGroupSets(date)
    val groups = MuscleGroup.entries.filter { (counts[it] ?: 0) > 0 }
    if (groups.isEmpty()) return
    GroupHeader("Sets by muscle group")
    Group(modifier) {
        groups.forEachIndexed { i, group ->
            val done = counts[group] ?: 0
            val target = targets[group] ?: group.targetSets
            val frac = (done.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
            GroupRow(first = i == 0) {
                Column(Modifier.fillMaxWidth().padding(vertical = Space.xs)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Txt(group.label, Type.body, Modifier.weight(1f))
                        Txt("$done of $target", Type.subhead.tabular(), color = p.fg2)
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
                                .background(p.fg2),
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
    SheetTitle("Open session", null)
    PillButton("Start ${state.nextDay.title}", onStartFromToday, Modifier.fillMaxWidth(), primary = true, icon = Icons.dumbbell)
    PillButton("Start blank", onStartBlank, Modifier.fillMaxWidth(), onSurface = true, icon = Icons.plus)
    PillButton("Cancel", onDismiss, Modifier.fillMaxWidth(), onSurface = true)
}

/** Sheet shown when a session with logged sets finishes: "Count toward your program?" */
@Composable
fun OpenSessionCountSheet(
    onYes: () -> Unit,
    onNo: () -> Unit,
) {
    SheetTitle("Count toward your program?", null)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        PillButton("Keep it off plan", onNo, Modifier.weight(1f), onSurface = true)
        PillButton("Count it", onYes, Modifier.weight(1f), primary = true)
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
        Modifier.fillMaxWidth().heightIn(max = 500.dp),
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
            if (query.isEmpty()) Txt("Search exercises", Type.body, color = p.fg3)
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
) {
    val p = LocalPalette.current
    val a = state.active
    var showAddExercise by remember { mutableStateOf(false) }
    // Finish stays quiet until a set is logged; a session with nothing logged ends without asking whether it counts.
    val anyLogged = a?.sets.orEmpty().isNotEmpty()

    ScreenFrame(bottom = {
        BottomBar {
            PillButton(
                "Finish session",
                onClick = { if (anyLogged) OpenSessionManager.showCountSheet = true else onFinish(false) },
                modifier = Modifier.weight(1f),
                primary = anyLogged,
            )
        }
    }) {
        Spacer(Modifier.height(Space.xxl))
        Txt("Open session", Type.largeTitle)
        val elapsed = if (a != null) Duration.between(a.startedAt, now).seconds else 0L
        Txt(Fmt.clock(elapsed) + " elapsed", Type.subhead.tabular(), color = p.fg2)

        if (OpenSessionManager.exercises.isEmpty()) {
            Spacer(Modifier.height(Space.xxl))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(p.card).padding(Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Txt("No exercises added yet", Type.headline)
                Spacer(Modifier.height(Space.lg))
                PillButton("Add exercise", { showAddExercise = true }, primary = true)
            }
        } else {
            val logged = a?.sets.orEmpty()
            val next = OpenSessionManager.exercises.firstNotNullOfOrNull { o ->
                (1..o.setsCount).map { SetKey(o.exercise.id, o.exercise.tier, it) }.firstOrNull { it !in logged }
            }
            OpenSessionManager.exercises.forEach { openEx ->
                Spacer(Modifier.height(Space.x3))
                OpenExerciseCard(
                    state = state,
                    openEx = openEx,
                    next = next,
                    onLog = onLog,
                    onClear = onClear,
                    onUpdateReps = { newReps -> OpenSessionManager.updateReps(state, openEx.exercise.id, newReps) },
                    onUpdateWeight = { newWeight -> OpenSessionManager.updateWeight(openEx.exercise.id, newWeight) },
                    onAddSet = { OpenSessionManager.addSet(openEx.exercise.id) },
                    onRemoveSet = { OpenSessionManager.removeSet(openEx.exercise.id) },
                    onRemoveExercise = { OpenSessionManager.removeExercise(openEx.exercise.id) },
                )
            }
            Spacer(Modifier.height(Space.x3))
            PillButton("Add exercise", { showAddExercise = true }, Modifier.fillMaxWidth(), icon = Icons.plus)
        }

        Spacer(Modifier.height(Space.xxl))
        WeeklyVolumeSection(state)
    }

    Sheet(showAddExercise, { showAddExercise = false }) {
        AddExerciseSheet(
            state = state,
            onSelect = { id ->
                OpenSessionManager.addExercise(state, id)
                showAddExercise = false
            },
            onDismiss = { showAddExercise = false },
        )
    }

    Sheet(OpenSessionManager.showCountSheet, { OpenSessionManager.showCountSheet = false }) {
        OpenSessionCountSheet(
            onYes = {
                OpenSessionManager.showCountSheet = false
                onFinish(true)
            },
            onNo = {
                OpenSessionManager.showCountSheet = false
                onFinish(false)
            },
        )
    }
}

/** Card for one exercise inside the open session with +/- steppers and set rows. */
@Composable
fun OpenExerciseCard(
    state: AppState,
    openEx: OpenExercise,
    next: SetKey?,
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.lg)).background(p.card).padding(Space.lg),
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
        FormToggle(ex.id)

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
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "Decrease weight" },
                        shape = CircleShape,
                        scale = SMALL_PRESS,
                        fill = p.card,
                        label = "Decrease weight",
                        repeat = true,
                    ) {
                        Icon(Icons.minus, p.fg, 18.dp)
                    }
                    Pressable(
                        onClick = { onUpdateWeight(openEx.targetWeight + 5) },
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "Increase weight" },
                        shape = CircleShape,
                        scale = SMALL_PRESS,
                        fill = p.card,
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
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "Decrease reps" },
                        shape = CircleShape,
                        scale = SMALL_PRESS,
                        fill = p.card,
                        label = "Decrease reps",
                        repeat = true,
                    ) {
                        Icon(Icons.minus, p.fg, 18.dp)
                    }
                    Pressable(
                        onClick = { onUpdateReps(openEx.targetReps + 1) },
                        modifier = Modifier.size(36.dp).semantics { contentDescription = "Increase reps" },
                        shape = CircleShape,
                        scale = SMALL_PRESS,
                        fill = p.card,
                        label = "Increase reps",
                        repeat = true,
                    ) {
                        Icon(Icons.plus, p.fg, 18.dp)
                    }
                }
            }
        }

        if (ex.id in Catalog.barbell) PlateLine(plates, openEx.targetWeight, Modifier.padding(top = Space.sm))
        Spacer(Modifier.height(Space.md))

        // Sets column; each card has its own pill, since the card's surface would hide one drawn under the whole list.
        StepPillHost(p.card.compositeOver(p.bg), Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            for (i in 1..openEx.setsCount) {
                val key = SetKey(ex.id, ex.tier, i)
                val logged = sets[key]?.event
                SetRow(
                    index = i,
                    amrap = false,
                    targetReps = openEx.targetReps,
                    defaultWeight = openEx.targetWeight,
                    logged = logged,
                    current = key == next,
                    exName = ex.name,
                    plates = plates.takeIf { ex.id in Catalog.barbell },
                    plateWeight = openEx.targetWeight,
                    onLog = { w, r, rpe -> onLog(ex, i, w, r, rpe) },
                    onClear = { onClear(ex, i) },
                )
            }
        } }

        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallPill("Add set", onAddSet)
            if (openEx.setsCount > 1) {
                SmallPill("- Remove set", onRemoveSet)
            }
        }
    }
}
