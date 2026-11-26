package dev.gzclp.tracker.stretch

import dev.gzclp.tracker.log.AppState
import java.time.LocalDate

/**
 * A guided stretching routine comprising an ordered list of moves. A [custom] routine is the user's own, defined in the log.
 * A built-in one also says what it is for ([goal]) and when to [pick] it over the others.
 */
data class StretchRoutine(
    val id: String,
    val name: String,
    val description: String,
    val frequency: String,
    val moves: List<StretchMove>,
    val note: String? = null,
    val custom: Boolean = false,
    val goal: String = "",
    val pick: String = "",
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

    /** Built-in routine ids, which a custom routine never takes. Library ids carry a hyphen, which a custom id never has. */
    val IDS: Set<String> by lazy { setOf(ID_AFTER_WORKOUT, ID_FLEXIBILITY, ID_DANCE, ID_ATHLETICS, ID_ATHLETICS_DYNAMIC) + library.map { it.id } }

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
            name = "After workout",
            description = "8–10 minute post-training recovery tailored to today's muscle groups ($areaNames).",
            frequency = "After every lifting session",
            moves = selectedMoves,
            note = "Customized from today's lifts",
            goal = "Bring the muscles you trained back to resting length while you cool down.",
            pick = "Right after lifting: one routine that matches the day.",
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
        name = "Flexibility: Legs & splits",
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
        goal = "Build toward front and middle splits.",
        pick = "On a flexibility day, warm. Not right before heavy legs.",
    )

    fun flexibilityBack(): StretchRoutine = StretchRoutine(
        id = ID_FLEXIBILITY,
        name = "Flexibility: Back-bending",
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
        goal = "Open the spine and shoulders for backbends and overhead reach.",
        pick = "Alternate with the legs day, a rest day between.",
    )

    fun flexibilityRest(): StretchRoutine = StretchRoutine(
        id = ID_FLEXIBILITY,
        name = "Flexibility: Rest & recovery",
        description = "Gentle passive mobility and decompression between deep stretching days.",
        frequency = "Scheduled rest day",
        moves = listOf(
            StretchMoves.CHILDS_POSE_LAT,
            StretchMoves.CAT_COW_STRETCH,
            StretchMoves.BUTTERFLY_STRETCH,
            StretchMoves.SUPINE_SPINAL_TWIST,
        ),
        note = "Rest day between deep flexibility sessions",
        goal = "Keep your range between deep sessions without straining.",
        pick = "The day after a deep session, or when sore.",
    )

    /**
     * Dance: 4–6 days/week, hips, hamstrings, spine, feet/ankles.
     */
    fun dance(): StretchRoutine = StretchRoutine(
        id = ID_DANCE,
        name = "Dance mobility",
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
        goal = "Turnout, extension and pointed-foot range for dance.",
        pick = "Dancing 4–6 days a week; after class while warm.",
    )

    /**
     * Athletics: 3–5 days/week, shoulders, hips, ankles, upper back.
     * Static 20–30 s holds after training.
     */
    fun athleticsStatic(): StretchRoutine = StretchRoutine(
        id = ID_ATHLETICS,
        name = "Athletics: Post-training static",
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
        goal = "Restore shoulder, hip and ankle range after sport.",
        pick = "After practice or a game; the whole-body pick for field and court sports.",
    )

    /**
     * Athletics: dynamic before training. Shorter 15–20s active prep holds.
     */
    fun athleticsDynamic(): StretchRoutine = StretchRoutine(
        id = ID_ATHLETICS_DYNAMIC,
        name = "Athletics: Pre-training dynamic",
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
        goal = "Warm up range without dulling power.",
        pick = "Before training or a game: short moving holds, not long static ones.",
    )

    /**
     * Returns every preset routine, each Flexibility day included, then the user's own, so any of them can be started on any day:
     * After workout, then [popular], then [more].
     */
    fun all(state: AppState? = null, date: LocalDate = LocalDate.now()): List<StretchRoutine> =
        listOf(afterWorkout(state, date)) + popular + more + state?.customStretches?.values.orEmpty()

    private fun move(m: StretchMove, sec: Int, dynamic: Boolean = false) = m.copy(holdSec = sec, isDynamic = dynamic)

    private fun preset(id: String, name: String, description: String, goal: String, pick: String, vararg moves: StretchMove) =
        StretchRoutine(id, name, description, "", moves.toList(), goal = goal, pick = pick)

    // A one-sided move's hold covers both sides, so 60 is 30 s a side (ACSM: 10–30 s per hold).
    private val desk = preset(
        "desk-reset", "Desk reset", "Standing stretches for chest, shoulders, spine and hips.",
        "Undo the rounded, hip-bent shape of long sitting.", "Between work blocks; no mat or floor needed.",
        move(StretchMoves.DOORWAY_PEC_STRETCH, 60), move(StretchMoves.BEHIND_BACK_CLASP, 30), move(StretchMoves.CROSS_BODY_SHOULDER, 50),
        move(StretchMoves.OVERHEAD_TRICEPS, 50), move(StretchMoves.OVERHEAD_SIDE_BEND, 50), move(StretchMoves.SEATED_SPINAL_TWIST, 50),
        move(StretchMoves.STANDING_QUAD_STRETCH, 60), move(StretchMoves.STANDING_FORWARD_FOLD, 35),
    )
    private val runners = preset(
        "runners-cooldown", "Runner's cooldown", "Calves, quads, hip flexors and glutes, the muscles a run works hardest.",
        "Ease post-run tightness in the legs and hips.", "Right after a run, while still warm.",
        move(StretchMoves.WALL_CALF_STRETCH, 60), move(StretchMoves.HEEL_DROP_STRETCH, 50), move(StretchMoves.STANDING_QUAD_STRETCH, 60),
        move(StretchMoves.COUCH_STRETCH, 60), move(StretchMoves.DOWNWARD_DOG, 30), move(StretchMoves.PIGEON_POSE, 60),
        move(StretchMoves.STANDING_FORWARD_FOLD, 35),
    )
    private val morning = preset(
        "morning-flow", "Morning flow", "Sun salutation style: fold, cobra and down dog, three rounds.",
        "Wake the spine and hamstrings with gentle movement.", "First thing in the morning, or as a 4-minute warm-up.",
        move(StretchMoves.CAT_COW_STRETCH, 20, dynamic = true),
        *List(3) { listOf(StretchMoves.STANDING_FORWARD_FOLD, StretchMoves.COBRA_POSE, StretchMoves.DOWNWARD_DOG) }.flatten().map { move(it, 20, dynamic = true) }.toTypedArray(),
        move(StretchMoves.CHILDS_POSE_LAT, 40),
    )
    private val bedtime = preset(
        "bedtime-wind-down", "Bedtime wind-down", "Slow floor holds: child's pose, forward fold, butterfly and a twist.",
        "Long, easy holds to settle down before sleep.", "In the evening; works on the bed.",
        move(StretchMoves.CAT_COW_STRETCH, 30), move(StretchMoves.CHILDS_POSE_LAT, 60), move(StretchMoves.SEATED_SINGLE_LEG, 60),
        move(StretchMoves.BUTTERFLY_STRETCH, 60), move(StretchMoves.SUPINE_SPINAL_TWIST, 60),
    )

    /** The few routines listed right under After workout. */
    val popular: List<StretchRoutine> by lazy { listOf(desk, runners, morning, bedtime) }

    /** The rest of the built-in library, behind Show more. */
    val more: List<StretchRoutine> by lazy {
        listOf(
            preset(
                "low-back-relief", "Low back relief", "Cat, child's pose, a knees-to-side twist and cobra.",
                "Ease a stiff low back with gentle bending, turning and arching.", "Back feels tight after sitting or lifting. Stop if pain runs down a leg.",
                move(StretchMoves.CAT_COW_STRETCH, 30), move(StretchMoves.CHILDS_POSE_LAT, 60), move(StretchMoves.SUPINE_SPINAL_TWIST, 60),
                move(StretchMoves.COBRA_POSE, 30), move(StretchMoves.PIGEON_POSE, 60), move(StretchMoves.CHILDS_POSE_LAT, 60),
            ),
            preset(
                "hip-flexor-reset", "Hip flexor reset", "Couch stretch held two minutes a side, then lizard and pigeon.",
                "Win back the hip extension that sitting takes.", "The front of the hip feels short; pick it over Desk reset for depth.",
                move(StretchMoves.COUCH_STRETCH, 240), move(StretchMoves.LIZARD_LUNGE, 120), move(StretchMoves.PIGEON_POSE, 120),
            ),
            preset(
                "front-splits", "Front splits", "Long holds on the front leg's hamstring and the back leg's hip flexor.",
                "Progress toward a full front split.", "Warm, a few times a week; narrower than Legs & splits.",
                move(StretchMoves.LIZARD_LUNGE, 90), move(StretchMoves.COUCH_STRETCH, 90), move(StretchMoves.SEATED_SINGLE_LEG, 90),
                move(StretchMoves.DOWNWARD_DOG, 45), move(StretchMoves.PIGEON_POSE, 90), move(StretchMoves.STANDING_FORWARD_FOLD, 45),
            ),
            flexibilityLegs(),
            flexibilityBack(),
            flexibilityRest(),
            preset(
                "overhead-mobility", "Overhead mobility", "Lats, chest, triceps and upper back for arms overhead.",
                "Lock out overhead without arching the low back.", "Before pressing, snatches or handstands.",
                move(StretchMoves.CHILDS_POSE_LAT, 60), move(StretchMoves.DOWNWARD_DOG, 30), move(StretchMoves.THREAD_THE_NEEDLE, 60),
                move(StretchMoves.DOORWAY_PEC_STRETCH, 60), move(StretchMoves.OVERHEAD_TRICEPS, 50), move(StretchMoves.COBRA_POSE, 30),
            ),
            preset(
                "deep-squat-prep", "Deep squat prep", "Short holds for ankles, calves, groin and hips.",
                "Squat deeper with heels down and chest up.", "Before squats, when heels lift or depth runs short.",
                move(StretchMoves.KNEELING_ANKLE_DORSI, 40, dynamic = true), move(StretchMoves.WALL_CALF_STRETCH, 40, dynamic = true),
                move(StretchMoves.BUTTERFLY_STRETCH, 20, dynamic = true), move(StretchMoves.LIZARD_LUNGE, 40, dynamic = true),
                move(StretchMoves.PIGEON_POSE, 40, dynamic = true), move(StretchMoves.COUCH_STRETCH, 40, dynamic = true),
            ),
            preset(
                "calves-and-ankles", "Calves and ankles", "Calf, Achilles and ankle holds, back and front.",
                "Ankle range for squats, running and long days standing.", "On your feet all day, or calves tight after jumping.",
                move(StretchMoves.WALL_CALF_STRETCH, 60), move(StretchMoves.HEEL_DROP_STRETCH, 50), move(StretchMoves.KNEELING_ANKLE_DORSI, 50),
                move(StretchMoves.PLANTAR_ANKLE_SIT, 30), move(StretchMoves.DOWNWARD_DOG, 30),
            ),
            preset(
                "cyclists-cooldown", "Cyclist's cooldown", "Hip flexors, quads, chest and upper back after the saddle.",
                "Undo the folded riding position.", "After a ride or a spin class.",
                move(StretchMoves.COUCH_STRETCH, 60), move(StretchMoves.STANDING_QUAD_STRETCH, 60), move(StretchMoves.PIGEON_POSE, 60),
                move(StretchMoves.STANDING_FORWARD_FOLD, 35), move(StretchMoves.DOORWAY_PEC_STRETCH, 60), move(StretchMoves.CAT_COW_STRETCH, 30),
                move(StretchMoves.COBRA_POSE, 30),
            ),
            preset(
                "throwers-shoulder", "Thrower's shoulder", "Cross-body and sleeper stretches for the back of the shoulder, then chest and lats.",
                "Win back the inward rotation throwing takes.", "Baseball, tennis, volleyball or swimming, after practice.",
                move(StretchMoves.CROSS_BODY_SHOULDER, 60), move(StretchMoves.SLEEPER_STRETCH, 60), move(StretchMoves.DOORWAY_PEC_STRETCH, 60),
                move(StretchMoves.OVERHEAD_TRICEPS, 50), move(StretchMoves.THREAD_THE_NEEDLE, 60), move(StretchMoves.CHILDS_POSE_LAT, 60),
            ),
            preset(
                "golf-warm-up", "Golf warm-up", "Short twists for the upper back, then hips and shoulders.",
                "Free up rotation before the first tee.", "Before a round or a range session.",
                move(StretchMoves.CAT_COW_STRETCH, 20, dynamic = true), move(StretchMoves.THREAD_THE_NEEDLE, 40, dynamic = true),
                move(StretchMoves.SEATED_SPINAL_TWIST, 40, dynamic = true), move(StretchMoves.OVERHEAD_SIDE_BEND, 40, dynamic = true),
                move(StretchMoves.LIZARD_LUNGE, 40, dynamic = true), move(StretchMoves.CROSS_BODY_SHOULDER, 40, dynamic = true),
            ),
            dance(),
            athleticsStatic(),
            athleticsDynamic(),
        )
    }

    /** The library routines that only have an id here, not a function of their own. */
    private val library: List<StretchRoutine> by lazy { (popular + more).filter { '-' in it.id } }

    /** The routine with [id]: a custom one, else a built-in, else After workout. */
    fun of(id: String, state: AppState? = null, date: LocalDate = LocalDate.now()): StretchRoutine {
        state?.customStretches?.get(id)?.let { return it }
        return when (id) {
            ID_AFTER_WORKOUT -> afterWorkout(state, date)
            ID_FLEXIBILITY -> flexibility(date)
            ID_DANCE -> dance()
            ID_ATHLETICS -> athleticsStatic()
            ID_ATHLETICS_DYNAMIC -> athleticsDynamic()
            else -> library.firstOrNull { it.id == id } ?: afterWorkout(state, date)
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
