package dev.gzclp.tracker.engine

/** A known exercise. [lower] marks lower-body compounds, which step up by the larger cycle increment. */
data class Move(val id: String, val name: String, val lower: Boolean = false)

object Catalog {
    val all = listOf(
        Move("squat", "Squat", true),
        Move("bench", "Bench press"),
        Move("deadlift", "Deadlift", true),
        Move("ohp", "Overhead press"),
        Move("barbell_row", "Barbell row"),
        Move("lat_pulldown", "Lat pulldown"),
        Move("dumbbell_row", "Dumbbell row"),
        Move("seated_cable_row", "Seated cable row"),
        Move("incline_db_press", "Incline dumbbell press"),
        Move("chest_fly", "Cable fly"),
        Move("lateral_raise", "Lateral raise"),
        Move("rear_delt_fly", "Rear delt fly"),
        Move("face_pull", "Face pull"),
        Move("barbell_curl", "Barbell curl"),
        Move("hammer_curl", "Hammer curl"),
        Move("tricep_pushdown", "Tricep pushdown"),
        Move("overhead_tricep_extension", "Overhead tricep extension"),
        Move("romanian_deadlift", "Romanian deadlift", true),
        Move("leg_press", "Leg press", true),
        Move("leg_curl", "Leg curl"),
        Move("leg_extension", "Leg extension"),
        Move("calf_raise", "Calf raise"),
    )

    /** Exercises loaded on a barbell: their targets round to what the plates make, and each set shows its plates. */
    val barbell = setOf("squat", "bench", "deadlift", "ohp", "barbell_row", "barbell_curl", "romanian_deadlift")
}

/** One exercise slot in a program day. Cycle programs use [Tier.CYCLE] for every slot; custom splits use [Tier.CUSTOM] with a [wave]. */
data class Slot(val ex: String, val tier: Tier, val wave: Wave? = null)

data class ProgramDay(val id: String, val title: String, val slots: List<Slot>)

/** [from] is set only on a custom split: the built-in preset it was copied from, which takes over if the split is deleted in use. */
data class Program(val id: String, val name: String, val days: List<ProgramDay>, val from: String? = null) {
    /** GZCLP runs the T1/T2/T3 engine; every other program runs the 3-week cycle. */
    val gzclp: Boolean get() = id == Programs.GZCLP
    val custom: Boolean get() = from != null
    fun day(id: String): ProgramDay? = days.firstOrNull { it.id == id }

    /** What a custom split must hold: a name, at least one day, unique day ids, and per day distinct exercises with valid waves. Days may be empty. */
    val validSplit: Boolean get() = custom && name.isNotBlank() && days.isNotEmpty() && days.distinctBy { it.id }.size == days.size &&
        days.all { d -> d.id.isNotBlank() && d.title.isNotBlank() && d.slots.distinctBy { it.ex }.size == d.slots.size && d.slots.all { it.tier == Tier.CUSTOM && it.wave?.valid == true } }
}

object Programs {
    const val GZCLP = "gzclp"

    private fun gz(id: String, t1: String, t2: String, t3: String) =
        ProgramDay(id, "Workout $id", listOf(Slot(t1, Tier.T1), Slot(t2, Tier.T2), Slot(t3, Tier.T3)))

    private fun day(id: String, title: String, vararg ex: String) = ProgramDay(id, title, ex.map { Slot(it, Tier.CYCLE) })

    val all = listOf(
        Program(
            GZCLP, "GZCLP",
            listOf(
                gz("A1", "squat", "bench", "lat_pulldown"),
                gz("B1", "ohp", "deadlift", "dumbbell_row"),
                gz("A2", "bench", "squat", "lat_pulldown"),
                gz("B2", "deadlift", "ohp", "dumbbell_row"),
            ),
        ),
        Program(
            "bro", "Bro split",
            listOf(
                day("chest", "Chest", "bench", "incline_db_press", "chest_fly"),
                day("back", "Back", "deadlift", "barbell_row", "lat_pulldown", "seated_cable_row"),
                day("shoulders", "Shoulders", "ohp", "lateral_raise", "rear_delt_fly", "face_pull"),
                day("legs", "Legs", "squat", "romanian_deadlift", "leg_press", "leg_curl", "calf_raise"),
                day("arms", "Arms", "barbell_curl", "tricep_pushdown", "hammer_curl", "overhead_tricep_extension"),
            ),
        ),
        Program(
            "ppl", "Push, pull, legs",
            listOf(
                day("push_a", "Push A", "bench", "ohp", "lateral_raise", "tricep_pushdown"),
                day("pull_a", "Pull A", "deadlift", "lat_pulldown", "seated_cable_row", "face_pull", "barbell_curl"),
                day("legs_a", "Legs A", "squat", "romanian_deadlift", "leg_curl", "calf_raise"),
                day("push_b", "Push B", "incline_db_press", "ohp", "chest_fly", "overhead_tricep_extension"),
                day("pull_b", "Pull B", "barbell_row", "lat_pulldown", "rear_delt_fly", "hammer_curl"),
                day("legs_b", "Legs B", "leg_press", "romanian_deadlift", "leg_extension", "calf_raise"),
            ),
        ),
        Program(
            "upper_lower", "Upper, lower",
            listOf(
                day("upper_a", "Upper A", "bench", "barbell_row", "ohp", "lat_pulldown", "barbell_curl"),
                day("lower_a", "Lower A", "squat", "romanian_deadlift", "leg_curl", "calf_raise"),
                day("upper_b", "Upper B", "incline_db_press", "seated_cable_row", "lateral_raise", "tricep_pushdown", "hammer_curl"),
                day("lower_b", "Lower B", "deadlift", "leg_press", "leg_extension", "calf_raise"),
            ),
        ),
        Program(
            "full_body", "Full body",
            listOf(
                day("a", "Full body A", "squat", "bench", "barbell_row", "face_pull"),
                day("b", "Full body B", "deadlift", "ohp", "lat_pulldown", "barbell_curl"),
                day("c", "Full body C", "leg_press", "incline_db_press", "seated_cable_row", "tricep_pushdown"),
            ),
        ),
        Program(
            "chest_back_legs", "Chest, back, legs",
            listOf(
                day("chest", "Chest", "bench", "incline_db_press", "chest_fly", "tricep_pushdown"),
                day("back", "Back", "deadlift", "barbell_row", "lat_pulldown", "barbell_curl"),
                day("legs", "Legs", "squat", "romanian_deadlift", "leg_press", "calf_raise"),
            ),
        ),
        Program(
            "chest_back_arms_legs", "Chest and back, arms, legs",
            listOf(
                day("chest_back", "Chest and back", "bench", "lat_pulldown", "chest_fly", "barbell_row"),
                day("arms", "Arms", "ohp", "lateral_raise", "barbell_curl", "tricep_pushdown", "hammer_curl", "overhead_tricep_extension"),
                day("legs", "Legs", "squat", "romanian_deadlift", "leg_press", "leg_curl", "calf_raise"),
            ),
        ),
    )

    /** The built-in program [id], or GZCLP for an id it does not know. Custom splits live in the log: see AppState.programOf. */
    fun of(id: String): Program = all.firstOrNull { it.id == id } ?: all.first()
}
