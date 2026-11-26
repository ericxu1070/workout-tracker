package dev.gzclp.tracker.log

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The muscles the muscle map shades, front and rear; [REAR_DELTS] is the deltoid seen from behind, [SHOULDERS] the one seen from the front. */
enum class Muscle(val label: String) {
    CHEST("Chest"), SHOULDERS("Front delts"), REAR_DELTS("Rear delts"), BICEPS("Biceps"), TRICEPS("Triceps"), FOREARMS("Forearms"),
    ABS("Abs"), OBLIQUES("Obliques"), TRAPS("Traps"), UPPER_BACK("Lats and upper back"), LOWER_BACK("Lower back"),
    GLUTES("Glutes"), QUADS("Quads"), HAMSTRINGS("Hamstrings"), ADDUCTORS("Adductors"), CALVES("Calves"),
}

/** The muscles a move works: [primary] are what it trains, [secondary] what helps. */
data class Targets(val primary: List<Muscle>, val secondary: List<Muscle> = emptyList())

/** A window's sets for one muscle: [sets] that worked it at all, and [load], each primary set 1 and each secondary set a half. */
data class MuscleWork(val sets: Int, val load: Double)

object Muscles {
    private fun t(primary: String, secondary: String = "") = Targets(list(primary), list(secondary))
    private fun list(s: String) = s.split(' ').filter { it.isNotEmpty() }.map { Muscle.valueOf(it) }

    /** Every catalog move; a weighted_ or assisted_ variant works what its base move does. */
    val catalog: Map<String, Targets> = mapOf(
        "squat" to t("QUADS GLUTES", "ADDUCTORS LOWER_BACK"),
        "bench" to t("CHEST", "SHOULDERS TRICEPS"),
        "deadlift" to t("HAMSTRINGS GLUTES LOWER_BACK", "QUADS TRAPS UPPER_BACK FOREARMS"),
        "ohp" to t("SHOULDERS", "TRICEPS TRAPS"),
        "barbell_row" to t("UPPER_BACK", "REAR_DELTS BICEPS LOWER_BACK"),
        "lat_pulldown" to t("UPPER_BACK", "BICEPS"),
        "dumbbell_row" to t("UPPER_BACK", "REAR_DELTS BICEPS"),
        "seated_cable_row" to t("UPPER_BACK", "REAR_DELTS BICEPS"),
        "incline_db_press" to t("CHEST", "SHOULDERS TRICEPS"),
        "chest_fly" to t("CHEST", "SHOULDERS"),
        "lateral_raise" to t("SHOULDERS", "TRAPS"),
        "rear_delt_fly" to t("REAR_DELTS", "UPPER_BACK"),
        "face_pull" to t("REAR_DELTS", "TRAPS UPPER_BACK"),
        "barbell_curl" to t("BICEPS", "FOREARMS"),
        "hammer_curl" to t("BICEPS FOREARMS"),
        "tricep_pushdown" to t("TRICEPS"),
        "overhead_tricep_extension" to t("TRICEPS"),
        "romanian_deadlift" to t("HAMSTRINGS GLUTES", "LOWER_BACK"),
        "leg_press" to t("QUADS", "GLUTES"),
        "leg_curl" to t("HAMSTRINGS", "CALVES"),
        "leg_extension" to t("QUADS"),
        "calf_raise" to t("CALVES"),
        "push_up" to t("CHEST", "SHOULDERS TRICEPS ABS"),
        "pull_up" to t("UPPER_BACK", "BICEPS FOREARMS"),
        "chin_up" to t("UPPER_BACK BICEPS", "FOREARMS"),
        "dip" to t("CHEST TRICEPS", "SHOULDERS"),
        "muscle_up" to t("UPPER_BACK TRICEPS", "BICEPS CHEST"),
        "inverted_row" to t("UPPER_BACK", "BICEPS REAR_DELTS"),
        "pistol_squat" to t("QUADS GLUTES", "HAMSTRINGS ADDUCTORS"),
    )

    /** A custom move by whole words in its id, checked in order (a leg curl is hamstrings, not quads or biceps); null when nothing matches. */
    private val words: List<Pair<List<String>, Targets>> = listOf(
        listOf("calf", "calves") to t("CALVES"),
        listOf("hamstring", "hamstrings", "rdl", "nordic") to t("HAMSTRINGS", "GLUTES"),
        listOf("glute", "glutes", "thrust", "bridge") to t("GLUTES", "HAMSTRINGS"),
        listOf("squat", "lunge", "lunges", "quad", "quads", "split") to t("QUADS GLUTES"),
        listOf("leg", "legs") to t("QUADS"),
        listOf("tricep", "triceps", "pushdown", "skullcrusher") to t("TRICEPS"),
        listOf("curl", "curls", "bicep", "biceps") to t("BICEPS", "FOREARMS"),
        listOf("bench", "chest", "fly", "flye", "pec") to t("CHEST", "SHOULDERS TRICEPS"),
        listOf("shrug", "shrugs", "trap", "traps") to t("TRAPS"),
        listOf("delt", "delts", "shoulder", "ohp", "raise") to t("SHOULDERS"),
        listOf("row", "pulldown", "pullup", "lat", "lats") to t("UPPER_BACK", "BICEPS"),
        listOf("crunch", "plank", "ab", "abs", "core", "situp") to t("ABS", "OBLIQUES"),
        listOf("oblique", "obliques", "twist") to t("OBLIQUES", "ABS"),
        listOf("back", "extension", "hyperextension") to t("LOWER_BACK"),
        listOf("wrist", "forearm", "forearms", "grip") to t("FOREARMS"),
    )

    fun of(exerciseId: String): Targets? {
        val id = exerciseId.lowercase().removePrefix("weighted_").removePrefix("assisted_")
        catalog[id]?.let { return it }
        val w = id.split('_', '-', ' ').toSet()
        if ("leg" in w && "curl" in w) return catalog["leg_curl"]
        return words.firstOrNull { (keys, _) -> keys.any { it in w } }?.second
    }

    /** Ten sets a week, the usual hypertrophy volume, is the least that shades a muscle fully, so a light week reads light. */
    const val FULL_SETS_PER_WEEK = 10.0

    /**
     * How dark each muscle shades over a window of [days] days, 0..1: its load against the most-worked muscle's, or against
     * ten sets a week when nothing reached that, so a heavy week still shows which muscles got the most. A window under a week counts as one.
     */
    fun levels(work: Map<Muscle, MuscleWork>, days: Long): Map<Muscle, Float> {
        val full = maxOf(work.values.maxOfOrNull { it.load } ?: 0.0, FULL_SETS_PER_WEEK * maxOf(days, 7) / 7)
        return work.mapValues { (it.value.load / full).toFloat() }
    }
}

/**
 * Each muscle's sets with reps from [from] to [to] (by the session's start day), the running session included.
 * Only lifting counts: stretching and cardio are left out, and so is a custom move whose id names no muscle.
 */
fun AppState.muscleWork(from: LocalDate, to: LocalDate): Map<Muscle, MuscleWork> {
    val sets = HashMap<Muscle, Int>()
    val load = HashMap<Muscle, Double>()
    fun count(exercise: String) {
        val t = Muscles.of(exercise) ?: return
        for (m in t.primary) { sets.merge(m, 1, Int::plus); load.merge(m, 1.0, Double::plus) }
        for (m in t.secondary - t.primary.toSet()) { sets.merge(m, 1, Int::plus); load.merge(m, 0.5, Double::plus) }
    }
    for (s in sessions) if (s.startedAt.toLocalDate() in from..to) for (set in s.sets) if (set.actualReps > 0) count(set.exercise)
    active?.let { a -> if (a.startedAt.toLocalDate() in from..to) for (set in a.sets.values) if (set.event.actualReps > 0) count(set.event.exercise) }
    return sets.mapValues { (m, n) -> MuscleWork(n, load.getValue(m)) }
}

/** The muscle map's window: this Monday-to-Sunday week, or this calendar month, as History's Week and Month mean them; both end [today]. */
fun muscleWindow(today: LocalDate, month: Boolean): Pair<LocalDate, LocalDate> =
    (if (month) today.withDayOfMonth(1) else mondayOf(today)) to today

/** Days in a window, both ends counted. */
fun days(window: Pair<LocalDate, LocalDate>): Long = ChronoUnit.DAYS.between(window.first, window.second) + 1
