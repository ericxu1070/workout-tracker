package dev.gzclp.tracker.log

import java.time.OffsetDateTime
import kotlin.math.roundToInt

/** Epley estimated 1-rep max, in whole lb. */
fun epley(weight: Int, reps: Int): Int = (weight * (1 + reps / 30.0)).roundToInt()

/** One session's best for an exercise: the heaviest set and the best estimated 1RM. */
data class SessionBest(val at: OffsetDateTime, val topWeight: Int, val topReps: Int, val e1rm: Int)

/** The heaviest set (then most reps) and best estimated 1RM over [sets] with reps; null when none has reps. */
fun best(at: OffsetDateTime, sets: List<SetLogged>): SessionBest? {
    val done = sets.filter { it.actualReps > 0 }
    val top = done.maxWithOrNull(compareBy({ it.weight }, { it.actualReps })) ?: return null
    return SessionBest(at, top.weight, top.actualReps, done.maxOf { epley(it.weight, it.actualReps) })
}

/** Every logged set of one exercise, by session, oldest first. */
data class ExerciseLog(val id: String, val sessions: List<Pair<SessionRecord, List<SetLogged>>>) {
    val bests: List<SessionBest> = sessions.mapNotNull { (s, sets) -> best(s.startedAt, sets) }
    val last: Pair<SessionRecord, List<SetLogged>> get() = sessions.last()
}

/** Every exercise ever logged, most recently done first. */
fun AppState.exerciseLogs(): List<ExerciseLog> =
    sessions.flatMap { s -> s.sets.map { it.exercise } }.distinct()
        .map { id -> ExerciseLog(id, sessions.mapNotNull { s -> s.sets.filter { it.exercise == id }.takeIf { it.isNotEmpty() }?.let { s to it } }) }
        .sortedByDescending { it.last.first.startedAt }
