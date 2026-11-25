package dev.gzclp.tracker.log

/**
 * [entries] with each [ExerciseRemap] applied to the lines before it, in log order: the remapped exercise's
 * `exercise_define` and `exercise_meta` lines drop out, and every other line names the exercise it was merged into.
 */
fun remapped(entries: List<Entry>): List<Entry> {
    val remaps = entries.mapNotNull { e -> (e.event as? ExerciseRemap)?.let { e.seq to it } }
    if (remaps.isEmpty()) return entries
    return entries.mapNotNull { e -> remaps.fold<Pair<Long, ExerciseRemap>, Entry?>(e) { acc, (seq, r) -> if (acc == null || acc.seq >= seq) acc else acc.remap(r) } }
}

private fun Entry.remap(r: ExerciseRemap): Entry? {
    fun id(x: String) = if (x == r.from) r.to else x
    val ev = when (val e = event) {
        is ExerciseDefine -> if (e.exercise == r.from) return null else e
        is ExerciseMeta -> if (e.exercise == r.from) return null else e.copy(parent = e.parent?.let(::id))
        is DayExercises -> e.copy(exercises = e.exercises.map(::id).distinct())
        is ProgramDefine -> e.copy(days = e.days.map { d -> d.copy(exercises = d.exercises.map { it.copy(exercise = id(it.exercise)) }.distinctBy { it.exercise }) })
        is ProgressionEvent -> e.copy(exercise = id(e.exercise))
        is ExerciseSwap -> e.copy(exercise = id(e.exercise), substitute = e.substitute?.let(::id)?.takeIf { it != id(e.exercise) })
        is SetCleared -> e.copy(exercise = id(e.exercise))
        is SetLogged -> when {
            e.exercise != r.from -> e
            // The weight typed for a bodyweight move was the body weight; read it as a calisthenics set with nothing added.
            r.bodyweight && e.addedLb == null && e.bodyweightLb == null ->
                e.copy(exercise = r.to, weight = 0, addedLb = 0.0, bodyweightLb = e.weight.takeIf { it > 0 }?.toDouble())
            else -> e.copy(exercise = r.to)
        }
        else -> e
    }
    return if (ev == event) this else copy(event = ev)
}
