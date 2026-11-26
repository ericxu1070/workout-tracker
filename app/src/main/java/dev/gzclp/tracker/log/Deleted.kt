package dev.gzclp.tracker.log

/**
 * [entries] with the sets deleted from finished workouts taken out, so the fold rebuilds as if they were never logged.
 * A [SetCleared] after its session's [SessionEnd] drops that set's [SetLogged] lines; a [ProgressionEvent] of the session
 * drops too once none of its exercise's sets at that tier are left. Clears inside a workout are left to the fold.
 */
fun withoutDeleted(entries: List<Entry>): List<Entry> {
    val ended = entries.mapNotNull { e -> (e.event as? SessionEnd)?.let { it.sessionId to e.seq } }.toMap()
    val late = entries.mapNotNullTo(HashSet()) { e ->
        val c = e.event as? SetCleared ?: return@mapNotNullTo null
        if (e.seq > (ended[c.sessionId] ?: return@mapNotNullTo null)) SetId(c.sessionId, c.exercise, c.tier, c.setIndex) else null
    }
    if (late.isEmpty()) return entries
    // The newest set or clear line per set decides whether it survives.
    val alive = HashMap<SetId, Boolean>()
    for (e in entries) when (val ev = e.event) {
        is SetLogged -> alive[SetId(ev.sessionId, ev.exercise, ev.tier, ev.setIndex)] = true
        is SetCleared -> alive[SetId(ev.sessionId, ev.exercise, ev.tier, ev.setIndex)] = false
        else -> {}
    }
    val kept = alive.filter { it.value && it.key !in late }.keys.mapTo(HashSet()) { Triple(it.session, it.exercise, it.tier) }
    val gone = late.mapTo(HashSet()) { Triple(it.session, it.exercise, it.tier) } - kept
    return entries.filter { e ->
        when (val ev = e.event) {
            is SetLogged -> SetId(ev.sessionId, ev.exercise, ev.tier, ev.setIndex) !in late
            is ProgressionEvent -> ev.sessionId == null || Triple(ev.sessionId, ev.exercise, ev.tier) !in gone
            else -> true
        }
    }
}

private data class SetId(val session: String, val exercise: String, val tier: String, val setIndex: Int)
