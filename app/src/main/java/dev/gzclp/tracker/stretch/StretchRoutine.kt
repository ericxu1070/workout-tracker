package dev.gzclp.tracker.stretch

import dev.gzclp.tracker.log.AppState
import java.time.LocalDate

/**
 * A guided stretching routine comprising an ordered list of moves. A [custom] routine is the user's own, defined in the log.
 */
data class StretchRoutine(
    val id: String,
    val name: String,
    val description: String,
    val frequency: String,
    val moves: List<StretchMove>,
    val note: String? = null,
    val custom: Boolean = false,
) {
    val totalDurationSec: Int get() = moves.sumOf { it.holdSec }
    val estimatedMinutes: Int get() = (totalDurationSec + 59) / 60

    /** Tells every routine apart: the Flexibility days share an id, and a custom routine keeps its id when renamed. */
    val key: String get() = if (custom) id else name
}

object StretchRoutines {

    const val COACHING_DISCLAIMER = "These are coaching defaults, not validated programs."

    const val ID_AFTER_WORKOUT = "after_workout"
    const val ID_FLEXIBILITY = "flexibility"
    const val ID_DANCE = "dance"
    const val ID_ATHLETICS = "athletics"
    const val ID_ATHLETICS_DYNAMIC = "athletics_dynamic"

    /** Built-in routine ids, which a custom routine never takes. */
    val IDS = setOf(ID_AFTER_WORKOUT, ID_FLEXIBILITY, ID_DANCE, ID_ATHLETICS, ID_ATHLETICS_DYNAMIC)

    /** A custom routine's hold per move, in [HOLD_STEP] steps. */
    val HOLD_SEC = 5..300
    const val HOLD_STEP = 5

    /**
     * After workout (default): 8–10 minutes chosen from today's lifts.
     * E.g. Squat -> hips, quads, hamstrings, ankles.
     * Bench -> chest, shoulders, upper back.
     * Deadlift -> hamstrings, hips, spine, lats.
     * If no lifts today, returns a balanced 8.5-minute post-workout cooldown.
     */
    fun afterWorkout(state: AppState? = null, date: LocalDate = LocalDate.now()): StretchRoutine {
        val targetAreas = detectTargetAreas(state, date)

        // Select 14-16 moves covering detected areas to total 8-10 minutes (480-600s)
        val selectedMoves = mutableListOf<StretchMove>()
        for (area in targetAreas) {
            val movesForArea = StretchMoves.forArea(area)
            movesForArea.firstOrNull { it !in selectedMoves }?.let { selectedMoves.add(it) }
        }

        // Fill remaining duration up to 8-10 minutes (480-600s) with complementary moves
        val candidates = listOf(
            StretchMoves.PIGEON_POSE,
            StretchMoves.STANDING_FORWARD_FOLD,
            StretchMoves.STANDING_QUAD_STRETCH,
            StretchMoves.COUCH_STRETCH,
            StretchMoves.WALL_CALF_STRETCH,
            StretchMoves.KNEELING_ANKLE_DORSI,
            StretchMoves.DOORWAY_PEC_STRETCH,
            StretchMoves.CROSS_BODY_SHOULDER,
            StretchMoves.CHILDS_POSE_LAT,
            StretchMoves.THREAD_THE_NEEDLE,
            StretchMoves.COBRA_POSE,
            StretchMoves.SUPINE_SPINAL_TWIST,
            StretchMoves.BUTTERFLY_STRETCH,
            StretchMoves.OVERHEAD_TRICEPS,
            StretchMoves.CAT_COW_STRETCH,
            StretchMoves.HEEL_DROP_STRETCH,
            StretchMoves.SEATED_SINGLE_LEG,
            StretchMoves.LIZARD_LUNGE,
            StretchMoves.BEHIND_BACK_CLASP,
            StretchMoves.OVERHEAD_SIDE_BEND,
            StretchMoves.SEATED_SPINAL_TWIST,
            StretchMoves.EAGLE_ARMS,
        )

        var idx = 0
        while (selectedMoves.sumOf { it.holdSec } < 480 && idx < candidates.size * 3) {
            val candidate = candidates[idx % candidates.size]
            val nextTotal = selectedMoves.sumOf { it.holdSec } + candidate.holdSec
            if (nextTotal <= 600) {
                selectedMoves.add(candidate)
            } else {
                break
            }
            idx++
        }

        val areaNames = targetAreas.joinToString(", ") { it.displayName }
        return StretchRoutine(
            id = ID_AFTER_WORKOUT,
            name = "After Workout",
            description = "8–10 minute post-training recovery tailored to today's muscle groups ($areaNames).",
            frequency = "After every lifting session",
            moves = selectedMoves,
            note = "Customized from today's lifts",
        )
    }

    /**
     * Flexibility: alternate legs/splits days and back-bending days, rest day between.
     */
    fun flexibility(date: LocalDate = LocalDate.now()): StretchRoutine {
        val cycle = ((date.toEpochDay() % 3) + 3) % 3
        return when (cycle) {
            0L -> flexibilityLegs()
            1L -> flexibilityBack()
            else -> flexibilityRest()
        }
    }

    fun flexibilityLegs(): StretchRoutine = StretchRoutine(
        id = ID_FLEXIBILITY,
        name = "Flexibility: Legs & Splits",
        description = "Deep hip opening, front & middle split progression, and hamstring length.",
        frequency = "Alternate with back days (rest day between)",
        moves = listOf(
            StretchMoves.BUTTERFLY_STRETCH,
            StretchMoves.PIGEON_POSE,
            StretchMoves.LIZARD_LUNGE,
            StretchMoves.STANDING_FORWARD_FOLD,
            StretchMoves.SEATED_SINGLE_LEG,
            StretchMoves.DOWNWARD_DOG,
            StretchMoves.STANDING_QUAD_STRETCH,
            StretchMoves.COUCH_STRETCH,
            StretchMoves.WALL_CALF_STRETCH,
            StretchMoves.HEEL_DROP_STRETCH,
            StretchMoves.KNEELING_ANKLE_DORSI,
            StretchMoves.PLANTAR_ANKLE_SIT,
            StretchMoves.PIGEON_POSE,
            StretchMoves.STANDING_FORWARD_FOLD,
            StretchMoves.COUCH_STRETCH,
            StretchMoves.SEATED_SINGLE_LEG,
        ),
        note = "Legs / Splits day",
    )

    fun flexibilityBack(): StretchRoutine = StretchRoutine(
        id = ID_FLEXIBILITY,
        name = "Flexibility: Back-Bending",
        description = "Thoracic spine extension, shoulder flexion, and anterior chain mobility.",
        frequency = "Alternate with legs days (rest day between)",
        moves = listOf(
            StretchMoves.CAT_COW_STRETCH,
            StretchMoves.COBRA_POSE,
            StretchMoves.SUPINE_SPINAL_TWIST,
            StretchMoves.SEATED_SPINAL_TWIST,
            StretchMoves.CHILDS_POSE_LAT,
            StretchMoves.OVERHEAD_SIDE_BEND,
            StretchMoves.DOORWAY_PEC_STRETCH,
            StretchMoves.BEHIND_BACK_CLASP,
            StretchMoves.CROSS_BODY_SHOULDER,
            StretchMoves.OVERHEAD_TRICEPS,
            StretchMoves.THREAD_THE_NEEDLE,
            StretchMoves.EAGLE_ARMS,
            StretchMoves.COBRA_POSE,
            StretchMoves.CHILDS_POSE_LAT,
            StretchMoves.SUPINE_SPINAL_TWIST,
            StretchMoves.CAT_COW_STRETCH,
        ),
        note = "Back-bending day",
    )

    fun flexibilityRest(): StretchRoutine = StretchRoutine(
        id = ID_FLEXIBILITY,
        name = "Flexibility: Rest & Recovery",
        description = "Gentle passive mobility and decompression between deep stretching days.",
        frequency = "Scheduled rest day",
        moves = listOf(
            StretchMoves.CHILDS_POSE_LAT,
            StretchMoves.CAT_COW_STRETCH,
            StretchMoves.BUTTERFLY_STRETCH,
            StretchMoves.SUPINE_SPINAL_TWIST,
        ),
        note = "Rest day between deep flexibility sessions",
    )

    /**
     * Dance: 4–6 days/week, hips, hamstrings, spine, feet/ankles.
     */
    fun dance(): StretchRoutine = StretchRoutine(
        id = ID_DANCE,
        name = "Dance Mobility",
        description = "Turnout, arabesque extension, hamstring lines, and foot/ankle articulation.",
        frequency = "4–6 days / week",
        moves = listOf(
            StretchMoves.BUTTERFLY_STRETCH,
            StretchMoves.PIGEON_POSE,
            StretchMoves.LIZARD_LUNGE,
            StretchMoves.STANDING_FORWARD_FOLD,
            StretchMoves.SEATED_SINGLE_LEG,
            StretchMoves.DOWNWARD_DOG,
            StretchMoves.PLANTAR_ANKLE_SIT,
            StretchMoves.KNEELING_ANKLE_DORSI,
            StretchMoves.HEEL_DROP_STRETCH,
            StretchMoves.COBRA_POSE,
            StretchMoves.SUPINE_SPINAL_TWIST,
            StretchMoves.OVERHEAD_SIDE_BEND,
            StretchMoves.CHILDS_POSE_LAT,
            StretchMoves.BUTTERFLY_STRETCH,
            StretchMoves.STANDING_FORWARD_FOLD,
            StretchMoves.PIGEON_POSE,
        ),
    )

    /**
     * Athletics: 3–5 days/week, shoulders, hips, ankles, upper back.
     * Static 20–30 s holds after training.
     */
    fun athleticsStatic(): StretchRoutine = StretchRoutine(
        id = ID_ATHLETICS,
        name = "Athletics: Post-Training Static",
        description = "Targeted recovery for shoulders, hips, ankles, and thoracic spine with 20–30s holds.",
        frequency = "3–5 days / week (after training)",
        moves = listOf(
            StretchMoves.CROSS_BODY_SHOULDER,
            StretchMoves.OVERHEAD_TRICEPS,
            StretchMoves.SLEEPER_STRETCH,
            StretchMoves.THREAD_THE_NEEDLE,
            StretchMoves.EAGLE_ARMS,
            StretchMoves.PIGEON_POSE,
            StretchMoves.LIZARD_LUNGE,
            StretchMoves.STANDING_QUAD_STRETCH,
            StretchMoves.KNEELING_ANKLE_DORSI,
            StretchMoves.WALL_CALF_STRETCH,
            StretchMoves.CHILDS_POSE_LAT,
            StretchMoves.CAT_COW_STRETCH,
            StretchMoves.COBRA_POSE,
            StretchMoves.CROSS_BODY_SHOULDER,
            StretchMoves.LIZARD_LUNGE,
            StretchMoves.KNEELING_ANKLE_DORSI,
        ),
    )

    /**
     * Athletics: dynamic before training. Shorter 15–20s active prep holds.
     */
    fun athleticsDynamic(): StretchRoutine = StretchRoutine(
        id = ID_ATHLETICS_DYNAMIC,
        name = "Athletics: Pre-Training Dynamic",
        description = "Dynamic movement prep for shoulders, hips, ankles, and upper back before training.",
        frequency = "3–5 days / week (before training)",
        moves = listOf(
            StretchMoves.CAT_COW_STRETCH.copy(holdSec = 20, isDynamic = true),
            StretchMoves.THREAD_THE_NEEDLE.copy(holdSec = 20, isDynamic = true),
            StretchMoves.LIZARD_LUNGE.copy(holdSec = 20, isDynamic = true),
            StretchMoves.KNEELING_ANKLE_DORSI.copy(holdSec = 20, isDynamic = true),
            StretchMoves.CROSS_BODY_SHOULDER.copy(holdSec = 20, isDynamic = true),
            StretchMoves.OVERHEAD_SIDE_BEND.copy(holdSec = 20, isDynamic = true),
            StretchMoves.DOWNWARD_DOG.copy(holdSec = 20, isDynamic = true),
            StretchMoves.BEHIND_BACK_CLASP.copy(holdSec = 20, isDynamic = true),
        ),
    )

    /**
     * Returns every preset routine, each Flexibility day included, then the user's own, so any of them can be started on any day.
     */
    fun all(state: AppState? = null, date: LocalDate = LocalDate.now()): List<StretchRoutine> = listOf(
        afterWorkout(state, date),
        flexibilityLegs(),
        flexibilityBack(),
        flexibilityRest(),
        dance(),
        athleticsStatic(),
        athleticsDynamic(),
    ) + state?.customStretches?.values.orEmpty()

    /** The routine with [id]: a custom one, else a built-in, else After workout. */
    fun of(id: String, state: AppState? = null, date: LocalDate = LocalDate.now()): StretchRoutine {
        state?.customStretches?.get(id)?.let { return it }
        return when (id) {
            ID_AFTER_WORKOUT -> afterWorkout(state, date)
            ID_FLEXIBILITY -> flexibility(date)
            ID_DANCE -> dance()
            ID_ATHLETICS -> athleticsStatic()
            ID_ATHLETICS_DYNAMIC -> athleticsDynamic()
            else -> afterWorkout(state, date)
        }
    }

    private fun detectTargetAreas(state: AppState?, date: LocalDate): Set<BodyArea> {
        if (state == null) {
            return setOf(BodyArea.HIPS, BodyArea.QUADS, BodyArea.HAMSTRINGS, BodyArea.CHEST, BodyArea.SHOULDERS, BodyArea.SPINE)
        }

        val areas = mutableSetOf<BodyArea>()

        // Check active session or today's session exercises
        val activeEx = state.active?.sets?.keys?.map { it.exercise } ?: emptyList()
        val todaySessions = state.sessions.filter {
            it.startedAt.toLocalDate() == date
        }
        val pastEx = todaySessions.flatMap { sess ->
            sess.sets.map { it.exercise }
        }

        // Or exercises in today's program slot
        val dayExercises = state.program.days.flatMap { day ->
            state.slots(state.program, day).map { state.name(it.ex).lowercase() }
        }

        val allExNames = (activeEx + pastEx).map { state.name(it).lowercase() } + dayExercises

        for (name in allExNames) {
            when {
                name.contains("squat") || name.contains("leg press") || name.contains("lunge") -> {
                    areas.addAll(listOf(BodyArea.HIPS, BodyArea.QUADS, BodyArea.HAMSTRINGS, BodyArea.CALVES, BodyArea.ANKLES))
                }
                name.contains("bench") || name.contains("push") || name.contains("dip") -> {
                    areas.addAll(listOf(BodyArea.CHEST, BodyArea.SHOULDERS, BodyArea.UPPER_BACK))
                }
                name.contains("deadlift") || name.contains("rdl") || name.contains("good morning") -> {
                    areas.addAll(listOf(BodyArea.HAMSTRINGS, BodyArea.HIPS, BodyArea.SPINE, BodyArea.LATS, BodyArea.UPPER_BACK))
                }
                name.contains("press") || name.contains("ohp") || name.contains("lateral") -> {
                    areas.addAll(listOf(BodyArea.SHOULDERS, BodyArea.UPPER_BACK, BodyArea.LATS, BodyArea.CHEST))
                }
                name.contains("row") || name.contains("pull") || name.contains("chin") || name.contains("lat") -> {
                    areas.addAll(listOf(BodyArea.LATS, BodyArea.UPPER_BACK, BodyArea.SHOULDERS, BodyArea.SPINE))
                }
                name.contains("calf") -> areas.addAll(listOf(BodyArea.CALVES, BodyArea.ANKLES))
            }
        }

        if (areas.isEmpty()) {
            areas.addAll(listOf(BodyArea.HIPS, BodyArea.QUADS, BodyArea.HAMSTRINGS, BodyArea.CHEST, BodyArea.SHOULDERS, BodyArea.SPINE, BodyArea.LATS))
        }

        return areas
    }
}
