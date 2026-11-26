package dev.gzclp.tracker.ui

import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.StretchLogged
import dev.gzclp.tracker.log.WaterLog
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchRoutines
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * November 2026 for one person using every part of the app, written through [Store.dispatch] like the app writes its log:
 * GZCLP three days a week with a T3 added to each day, cardio and stretching around it, morning weigh-ins and water.
 * Ends on Monday Nov 30, ten minutes into the day's workout. Every value is literal, so renders stay stable.
 */
object Demo {
    private val pst = ZoneOffset.ofHours(-8)
    private fun at(day: Int, h: Int, m: Int): OffsetDateTime = OffsetDateTime.of(2026, 11, day, h, m, 0, 0, pst)

    /** Mid-workout on the last day. */
    val now: OffsetDateTime = at(30, 18, 56).plusSeconds(18)
    val today: LocalDate = now.toLocalDate()

    const val EVENING = "Evening wind-down"
    private val rest = Tier.entries.associateWith { it.defaultRestSec }
    private val startWeights = mapOf("lat_pulldown" to 100, "dumbbell_row" to 50, "face_pull" to 35, "barbell_curl" to 50)

    /** Lifting days of the month, in order; the last is in progress at [now]. */
    private val liftDays = listOf(2, 4, 6, 9, 11, 13, 16, 18, 20, 23, 25, 27, 30)

    /** (day, hour, minute, activity, minutes, meters, RPE, notes) */
    private data class Run(val day: Int, val h: Int, val m: Int, val id: String, val min: Int, val meters: Int?, val rpe: Double, val notes: String? = null)
    private val cardio = listOf(
        Run(1, 9, 30, "hiking", 112, 8530, 5.0),
        Run(3, 7, 5, "running", 35, 6120, 6.0),
        Run(5, 18, 10, "basketball", 70, null, 7.0),
        Run(7, 8, 0, "running", 56, 10040, 7.5),
        Run(9, 19, 35, "stationary_bike", 15, null, 4.0),
        Run(10, 7, 10, "rowing_machine", 25, 5210, 7.0),
        Run(12, 7, 0, "running", 41, 7280, 6.0),
        Run(14, 10, 0, "basketball", 85, null, 8.0),
        Run(18, 19, 40, "running", 24, 4590, 5.0),
        Run(19, 17, 30, "cycling", 62, 24300, 6.0),
        Run(21, 8, 45, "hiking", 138, 11250, 5.5),
        Run(24, 7, 5, "running", 46, 8350, 7.0),
        Run(26, 8, 0, "running", 31, 5000, 8.5, "Turkey trot"),
        Run(28, 10, 30, "basketball", 62, null, 7.0),
        Run(30, 6, 50, "running", 30, 5420, 6.0),
    )

    /** (day, hour, minute, routine id); "after_workout" follows that day's lifts. */
    private val stretches = listOf(
        Triple(1, 21, 30) to "bedtime-wind-down", Triple(2, 19, 50) to StretchRoutines.ID_AFTER_WORKOUT, Triple(4, 19, 40) to StretchRoutines.ID_AFTER_WORKOUT,
        Triple(5, 21, 15) to EVENING, Triple(6, 19, 55) to StretchRoutines.ID_AFTER_WORKOUT, Triple(7, 9, 5) to "runners-cooldown",
        Triple(9, 19, 55) to StretchRoutines.ID_AFTER_WORKOUT, Triple(11, 12, 20) to "desk-reset", Triple(12, 21, 0) to EVENING,
        Triple(15, 10, 0) to StretchRoutines.ID_FLEXIBILITY, Triple(16, 19, 45) to StretchRoutines.ID_AFTER_WORKOUT, Triple(18, 20, 10) to EVENING,
        Triple(20, 19, 50) to StretchRoutines.ID_AFTER_WORKOUT, Triple(21, 11, 15) to "runners-cooldown", Triple(23, 19, 45) to StretchRoutines.ID_AFTER_WORKOUT,
        Triple(24, 7, 0) to "morning-flow", Triple(26, 21, 30) to EVENING, Triple(27, 19, 50) to StretchRoutines.ID_AFTER_WORKOUT,
        Triple(29, 10, 30) to StretchRoutines.ID_FLEXIBILITY,
    )

    /** Mornings without a weigh-in: travel, a sleep-in, the day after Thanksgiving. */
    private val noWeighIn = setOf(8, 14, 22, 27)
    private val noise = listOf(0.0, 0.8, -0.4, 0.5, -0.6, 0.2, 0.9)

    /** The demo log, built once: every dispatch syncs to disk. */
    val state: AppState by lazy { build(File.createTempFile("demo", ".jsonl").apply { delete(); deleteOnExit() }) }

    /** Writes the demo month to [file] and returns its fold. */
    fun build(file: File): AppState {
        val store = Store(file)
        store.dispatch(at(1, 6, 0)) { Commands.setup(mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115), Programs.GZCLP) }
        // A pulling T3 on the A days and curls on the B days, added from Days.
        val gz = store.state.value.program
        for ((i, day) in gz.days.withIndex()) store.dispatch(at(1, 6, 0)) { Commands.addExercise(it, gz, day, if (i % 2 == 0) "Face pull" else "Barbell curl") }

        // Every day's lines in the order they happened, as the phone would append them.
        for (d in 1..30) {
            val lines = mutableListOf<Pair<OffsetDateTime, () -> Unit>>()
            val busy = mutableListOf<ClosedRange<OffsetDateTime>>()
            if (d !in noWeighIn) {
                val t = at(d, 6, 40 + d * 7 % 15)
                val lb = 184.6 - d * 0.1 + noise[d % 7] + if (d == 28) 1.4 else 0.0
                lines += t to { store.dispatch(t) { Commands.logBodyWeight(it, null, t, lb) } }
            }
            for (r in cardio.filter { it.day == d }) {
                val t = at(d, r.h, r.m)
                val end = t.plusMinutes(r.min.toLong())
                busy += t..end
                lines += t to { store.dispatch(end) { Commands.logCardio(it, null, Activities.of(r.id)!!, t, r.min * 60L + d * 7 % 50, r.meters, r.rpe, r.notes, end) } }
            }
            val k = liftDays.indexOf(d)
            if (k >= 0) {
                val t = at(d, 18, if (d == 30) 42 else 5 + k * 3 % 20)
                busy += t..t.plusMinutes(80)
                lines += t to { store.workout(t, k, end = d != 30) }
            }
            if (d == 5) at(5, 21, 10).let { t ->
                lines += t to {
                    store.dispatch(t) { Commands.newStretchRoutine(it, EVENING, listOf(StretchMoves.CHILDS_POSE_LAT, StretchMoves.PIGEON_POSE, StretchMoves.SUPINE_SPINAL_TWIST, StretchMoves.BUTTERFLY_STRETCH)) }
                    store.dispatch(t) { s -> Commands.setStretchHold(s, s.customStretches.keys.last(), 1, 60) }
                }
            }
            for ((time, id) in stretches.filter { it.first.first == d }) {
                val t = at(d, time.second, time.third)
                lines += t to { store.stretch(t, id) }
            }
            // Water through the waking day: more on training days, less on rest days.
            val cups = listOf(350, 250, 500, 250, 350, 500, 250, 350)
            val n = 6 + (d * 5 % 4) + if (k >= 0 || cardio.any { it.day == d }) 2 else 0
            for (i in 0 until n) {
                val t = at(d, 7 + i * 14 / n, (d * 11 + i * 23) % 60)
                // Mid-workout drinks are left out so each day's lines stay in time order.
                if (t <= now && busy.none { t in it }) lines += t to { store.dispatch(t) { WaterLog.logWater(it, cups[(d + i) % cups.size], t) } }
            }
            for ((_, write) in lines.sortedBy { it.first }) write()
        }
        return store.state.value
    }

    /** A GZCLP workout at [start], [k]th of the month: a T1 miss on the 6th turns a stage, a T2 miss on the 11th. */
    fun Store.workout(start: OffsetDateTime, k: Int, end: Boolean) {
        while (true) {
            val ex = Commands.missingWeight(state.value) ?: break
            dispatch(start) { Commands.startingWeight(it, ex, startWeights[ex.id] ?: 45) }
        }
        dispatch(start) { Commands.start(it, start) }
        var t = start
        val a = state.value.active!!
        for (ex in state.value.exercises(a.program, a.day)) for (i in 1..ex.scheme.sets) {
            if (!end && t > start.plusMinutes(10)) return
            val amrap = ex.scheme.amrap && i == ex.scheme.sets
            val miss = (k == 5 && ex.tier == Tier.T1 && i >= 4) || (k == 10 && ex.tier == Tier.T2 && i == ex.scheme.sets)
            val reps = when {
                miss -> ex.scheme.reps - 1
                amrap && ex.tier == Tier.T3 -> 17 + k % 5 * 2
                amrap -> ex.scheme.reps + 3 - k % 3
                else -> ex.scheme.reps
            }
            t = t.plusSeconds(rest.getValue(ex.tier) + 70L + (k * 13 + i * 7) % 40)
            // RPE on last sets only, like a lifter who rates just the hard ones.
            val rpe = if (i == ex.scheme.sets) 8.0 + (k + i) % 4 * 0.5 else null
            dispatch(t) { Commands.logSet(it, ex, i, reps, ex.weight ?: 0, t, rest, rpe) }
        }
        if (end) t.plusSeconds(90).let { e -> dispatch(e) { Commands.end(it, e, null) } }
    }

    /** A routine played through to the end at [start], logged the way the stretch player logs it. */
    private fun Store.stretch(start: OffsetDateTime, id: String) {
        val s = state.value
        val routine = s.customStretches.values.firstOrNull { it.name == id } ?: StretchRoutines.all(s, start.toLocalDate()).first { it.id == id }
        // Each move's hold plus the player's get-ready count.
        val sec = routine.totalDurationSec + routine.moves.size * 8L
        val done = start.plusSeconds(sec)
        dispatch(done) { listOf(StretchLogged("st${done.toEpochSecond()}", LogCodec.TS.format(done), routine.id, sec, routine.moves.map { it.id }.distinct())) }
    }
}
