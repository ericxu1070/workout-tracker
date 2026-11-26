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

/** The best estimated 1RM of [exercise] over finished sessions; null if it was never done. */
fun AppState.pastBest(exercise: String): Int? =
    sessions.asSequence().flatMap { it.sets }.filter { it.exercise == exercise && it.actualReps > 0 }.maxOfOrNull { epley(it.weight, it.actualReps) }

/** [past] raised by this workout's logged [sets] of [exercise] but [except], the set being logged. */
fun bestSoFar(past: Int?, sets: Map<SetKey, LoggedSet>, exercise: String, except: SetKey): Int? =
    (sets.filterKeys { it != except }.values.map { it.event }.filter { it.exercise == exercise && it.actualReps > 0 }.map { epley(it.weight, it.actualReps) } + listOfNotNull(past)).maxOrNull()

/** Whether [weight] for [reps] beats [best], the lift's best estimated 1RM so far. A lift with no best yet sets no record. */
fun isRecord(best: Int?, weight: Int, reps: Int): Boolean = best != null && reps > 0 && epley(weight, reps) > best
