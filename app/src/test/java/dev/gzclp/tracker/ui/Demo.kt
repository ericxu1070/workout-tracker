package dev.gzclp.tracker.ui

import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.round5
import dev.gzclp.tracker.food.FoodLog
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.FoodEstimate
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.log.StretchCleared
import dev.gzclp.tracker.log.StretchLogged
import dev.gzclp.tracker.log.WaterLog
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchRoutines
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.floor

/**
 * June to November 2026 for one person using every part of the app, written through [Store.dispatch] like the app writes its log:
 * GZCLP Monday, Wednesday and Friday with a T3 added to each day, cardio and stretching around it, morning weigh-ins and water.
 * Reps come from a slowly rising strength curve, so stages turn, T1 and T2 reset and T3s step up where the engine says.
 * A beach week in August (hikes, a swim, an open hotel-gym session), the flu in late September (a blank week, then a deload),
 * a lean bulk into September and a slow cut after. Ends on Monday Nov 30, ten minutes into the day's workout.
 * Every value is literal or worked out from the date, never the clock or a random source, so renders stay stable.
 */
object Demo {
    private val zone = ZoneId.of("America/Los_Angeles")
    private fun at(day: LocalDate, h: Int, m: Int): OffsetDateTime = day.atTime(h, m).atZone(zone).toOffsetDateTime()
    private fun nov(day: Int): LocalDate = LocalDate.of(2026, 11, day)
    private fun date(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    val first: LocalDate = date(6, 1)

    /** Mid-workout on the last day. */
    val now: OffsetDateTime = at(nov(30), 18, 56).plusSeconds(18)
    val today: LocalDate = now.toLocalDate()

    const val EVENING = "Evening wind-down"
    private val rest = Tier.entries.associateWith { it.defaultRestSec }
    private val startWeights = mapOf("lat_pulldown" to 100, "dumbbell_row" to 50, "face_pull" to 35, "barbell_curl" to 50, "rear_delt_fly" to 20)

    /** Away at the beach: no program workouts, one open session at the hotel gym. */
    private val beach = date(8, 10)..date(8, 16)
    /** The flu: lifting stops on the 18th; nothing at all is logged the week of the 21st. */
    private val flu = date(9, 18)..date(9, 28)
    private val sick = date(9, 21)..date(9, 27)
    /** Holidays off: the Fourth of July weekend and Labor Day. */
    private val off = setOf(date(7, 3), date(9, 7))

    private val liftDow = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
    private fun lifts(d: LocalDate) = d.dayOfWeek in liftDow && d !in beach && d !in flu && d !in off

    /** Estimated 1RM per exercise (June 1 value, gain it tends to): fast early gains that level off, and six percent lost to the flu, back in three weeks. */
    private val e1rm = mapOf(
        "squat" to (250.0 to 60.0), "bench" to (190.0 to 32.0), "deadlift" to (300.0 to 70.0), "ohp" to (125.0 to 18.0),
        "lat_pulldown" to (200.0 to 55.0), "dumbbell_row" to (95.0 to 28.0), "face_pull" to (65.0 to 22.0), "barbell_curl" to (88.0 to 16.0),
        "rear_delt_fly" to (40.0 to 0.0),
    )
    private fun strength(id: String, day: LocalDate): Double {
        val (e0, gain) = e1rm[id] ?: (100.0 to 0.0)
        val t = ChronoUnit.DAYS.between(first, day).toDouble()
        val back = ChronoUnit.DAYS.between(flu.endInclusive, day)
        val lost = if (back in 0..20) 0.06 * (21 - back) / 21 else 0.0
        return (e0 + gain * (1 - exp(-t / 90))) * (1 - lost)
    }
    private fun noise(k: Int, i: Int) = ((k * 37 + i * 11) % 15 - 7) / 10.0

    /** (day, hour, minute, activity, minutes, meters, RPE, notes); [live] runs were started and stopped on the phone, the rest typed in after. */
    private data class Run(val day: LocalDate, val h: Int, val m: Int, val id: String, val min: Int, val meters: Int?, val rpe: Double, val notes: String? = null, val live: Boolean = false)
    private val november = listOf(
        Run(nov(1), 9, 30, "hiking", 112, 8530, 5.0),
        Run(nov(3), 7, 5, "running", 35, 6120, 6.0, live = true),
        Run(nov(5), 18, 10, "basketball", 70, null, 7.0),
        Run(nov(7), 8, 0, "running", 56, 10040, 7.5, live = true),
        Run(nov(9), 19, 35, "stationary_bike", 15, null, 4.0),
        Run(nov(10), 7, 10, "rowing_machine", 25, 5210, 7.0),
        Run(nov(12), 7, 0, "running", 41, 7280, 6.0, live = true),
        Run(nov(14), 10, 0, "basketball", 85, null, 8.0),
        Run(nov(18), 19, 40, "running", 24, 4590, 5.0),
        Run(nov(19), 17, 30, "cycling", 62, 24300, 6.0),
        Run(nov(21), 8, 45, "hiking", 138, 11250, 5.5),
        Run(nov(24), 7, 5, "running", 46, 8350, 7.0),
        Run(nov(26), 8, 0, "running", 31, 5000, 8.5, "Turkey trot"),
        Run(nov(28), 10, 30, "basketball", 62, null, 7.0),
        Run(nov(30), 6, 50, "running", 30, 5420, 6.0, live = true),
    )
    private val extras = listOf(
        Run(date(8, 10), 9, 0, "hiking", 140, 9800, 5.0),
        Run(date(8, 11), 8, 30, "open_water_swim", 35, 1400, 6.0),
        Run(date(8, 13), 8, 30, "hiking", 185, 13200, 6.5, "Ridge loop"),
        Run(date(8, 15), 9, 30, "paddling", 70, 6100, 4.5),
        Run(date(10, 17), 8, 0, "running", 55, 10020, 8.0, "First 10K", live = true),
    )

    /** June to October follow the week: a Tuesday run that gets longer and faster, Thursday rowing, the bike or a run, a long Saturday outing, Sunday basketball. */
    private fun cardio(d: LocalDate): List<Run> {
        if (d.month.value == 11) return november.filter { it.day == d }
        if (d in beach || d in sick) return extras.filter { it.day == d }
        val w = ChronoUnit.DAYS.between(first, d).toInt() / 7
        val summer = d.month.value <= 9
        return extras.filter { it.day == d } + when (d.dayOfWeek) {
            DayOfWeek.TUESDAY -> (26 + w / 2 + w % 3 * 3).let { listOf(Run(d, 7, 0, "running", it, it * (160 + w * 2), 6.0 + w % 3 * 0.5, live = true)) }
            DayOfWeek.THURSDAY -> when (w % 3) {
                0 -> listOf(Run(d, 7, 10, "rowing_machine", 22 + w / 4, (22 + w / 4) * 205, 7.0))
                1 -> listOf(Run(d, 7, 10, "stationary_bike", 30, null, 5.0))
                else -> listOf(Run(d, 7, 0, "running", 30, 30 * (170 + w * 2), 7.0, live = true))
            }
            DayOfWeek.SATURDAY -> when {
                d.dayOfMonth in 15..21 && d.month.value == 10 -> emptyList()
                !summer -> listOf(Run(d, 10, 0, "basketball", 75, null, 7.5))
                w % 2 == 0 -> (100 + w % 5 * 12).let { listOf(Run(d, 9, 0, "hiking", it, it * 78, 5.5)) }
                else -> (60 + w % 4 * 10).let { listOf(Run(d, 8, 30, "cycling", it, it * 390, 6.0)) }
            }
            DayOfWeek.SUNDAY -> if (w % 2 == 1) listOf(Run(d, 10, 30, "basketball", 65, null, 7.5)) else emptyList()
            else -> emptyList()
        }
    }

    /** November's stretches by (day, hour, minute); "after_workout" follows that day's lifts wherever it is listed. */
    private val novemberStretches = listOf(
        Triple(1, 21, 30) to "bedtime-wind-down", Triple(2, 19, 50) to StretchRoutines.ID_AFTER_WORKOUT, Triple(4, 19, 40) to StretchRoutines.ID_AFTER_WORKOUT,
        Triple(5, 21, 15) to EVENING, Triple(6, 19, 55) to StretchRoutines.ID_AFTER_WORKOUT, Triple(7, 9, 5) to "runners-cooldown",
        Triple(9, 19, 55) to StretchRoutines.ID_AFTER_WORKOUT, Triple(11, 12, 20) to "desk-reset", Triple(12, 21, 0) to EVENING,
        Triple(15, 10, 0) to StretchRoutines.ID_FLEXIBILITY, Triple(16, 19, 45) to StretchRoutines.ID_AFTER_WORKOUT, Triple(18, 20, 10) to EVENING,
        Triple(20, 19, 50) to StretchRoutines.ID_AFTER_WORKOUT, Triple(21, 11, 15) to "runners-cooldown", Triple(23, 19, 45) to StretchRoutines.ID_AFTER_WORKOUT,
        Triple(24, 7, 0) to "morning-flow", Triple(26, 21, 30) to EVENING, Triple(27, 19, 50) to StretchRoutines.ID_AFTER_WORKOUT,
        Triple(29, 10, 30) to StretchRoutines.ID_FLEXIBILITY,
    )
    private val eveningMade = date(7, 8)

    /** Stretches other than after a workout, by (hour, minute, routine). */
    private fun stretches(d: LocalDate): List<Triple<Int, Int, String>> {
        if (d.month.value == 11) return novemberStretches.filter { it.first.first == d.dayOfMonth && it.second != StretchRoutines.ID_AFTER_WORKOUT }
            .map { Triple(it.first.second, it.first.third, it.second) }
        if (d in sick) return emptyList()
        val w = ChronoUnit.DAYS.between(first, d).toInt() / 7
        return when (d.dayOfWeek) {
            DayOfWeek.SUNDAY -> listOf(Triple(21, 15, if (d > eveningMade) EVENING else "bedtime-wind-down")) +
                if (w % 3 == 0) listOf(Triple(9, 30, StretchRoutines.ID_FLEXIBILITY)) else emptyList()
            DayOfWeek.SATURDAY -> listOf(Triple(16, 0, if (d.month.value <= 9 && w % 2 == 1) "cyclists-cooldown" else "calves-and-ankles"))
            DayOfWeek.THURSDAY -> if (w % 2 == 0) listOf(Triple(12, 20, "desk-reset")) else emptyList()
            DayOfWeek.TUESDAY -> if (w % 4 == 1) listOf(Triple(6, 20, "morning-flow")) else listOf(Triple(8, 10, "runners-cooldown"))
            else -> emptyList()
        }
    }
    private fun afterWorkout(d: LocalDate): Boolean =
        if (d.month.value == 11) novemberStretches.any { it.first.first == d.dayOfMonth && it.second == StretchRoutines.ID_AFTER_WORKOUT }
        else (ChronoUnit.DAYS.between(first, d) / 7 + d.dayOfWeek.value) % 3 != 0L

    /** Body weight knots: a lean bulk to mid-September, the flu, back up, then a slow cut. */
    private val knots = listOf(first to 176.2, date(9, 17) to 185.8, date(9, 29) to 182.9, date(10, 20) to 185.4, nov(30) to 181.4)
    private fun trend(d: LocalDate): Double {
        val (a, b) = knots.zipWithNext().first { d <= it.second.first }
        val f = ChronoUnit.DAYS.between(a.first, d).toDouble() / ChronoUnit.DAYS.between(a.first, b.first)
        return a.second + (b.second - a.second) * f
    }
    private val noWeighIn = setOf(nov(8), nov(14), nov(22), nov(27))
    private fun weighs(d: LocalDate) = d !in noWeighIn && d !in beach && d !in sick && d != date(7, 14) && (d.month.value == 11 || d.dayOfYear * 7 % 9 != 0)
    private val noise7 = listOf(0.0, 0.8, -0.4, 0.5, -0.6, 0.2, 0.9)

    /** Breakfast, lunch, a snack and dinner, at the times the food photos are taken. */
    fun meals(day: LocalDate): List<OffsetDateTime> =
        listOf(7 to 48, 12 to 31, 15 to 52, 19 to 14).map { (h, m) -> at(day, h, m).plusMinutes(day.dayOfMonth % 5 * 6L) }
    private val dishes = listOf(Triple("Oatmeal and berries", 380, 460), Triple("Chicken rice bowl", 620, 760), Triple("Greek yogurt", 180, 240), Triple("Salmon and potatoes", 650, 820))

    /** The demo log, built once: every dispatch syncs to disk. */
    val state: AppState by lazy { build(File.createTempFile("demo", ".jsonl").apply { delete(); deleteOnExit() }) }

    /** Writes the demo half-year to [file] and returns its fold. */
    fun build(file: File): AppState {
        val store = Store(file)
        store.dispatch(at(first, 6, 0)) { Commands.setup(mapOf(Lift.SQUAT to 215, Lift.BENCH to 165, Lift.DEADLIFT to 255, Lift.OHP to 105), Programs.GZCLP) }
        // A pulling T3 on the A days and curls on the B days, added from Days.
        val gz = store.state.value.program
        for ((i, day) in gz.days.withIndex()) store.dispatch(at(first, 6, 0)) { Commands.addExercise(it, gz, day, if (i % 2 == 0) "Face pull" else "Barbell curl") }

        var k = 0
        var deloaded = false
        var swapped = false
        var d = first
        while (d <= today) {
            val day = d
            // Every day's lines in the order they happened, as the phone would append them.
            val lines = mutableListOf<Pair<OffsetDateTime, () -> Unit>>()
            val busy = mutableListOf<ClosedRange<OffsetDateTime>>()
            if (weighs(day)) {
                val t = at(day, 6, 40 + day.dayOfMonth * 7 % 15)
                val lb = trend(day) + noise7[day.dayOfMonth % 7] + if (day == nov(28)) 1.4 else 0.0
                lines += t to { store.dispatch(t) { Commands.logBodyWeight(it, null, t, lb) } }
            }
            for (r in cardio(day)) {
                val t = at(day, r.h, r.m)
                val sec = r.min * 60L + day.dayOfMonth * 7 % 50
                val end = t.plusSeconds(sec)
                busy += t..end
                if (r.live) {
                    lines += t to { store.dispatch(t) { Commands.startCardio(it, Activities.of(r.id)!!, t) } }
                    lines += end to { store.dispatch(end) { Commands.stopCardio(it, end, sec, r.meters, r.rpe, r.notes) } }
                } else lines += t to { store.dispatch(end) { Commands.logCardio(it, null, Activities.of(r.id)!!, t, sec, r.meters, r.rpe, r.notes, end) } }
            }
            if (lifts(day) || day == today) {
                val n = k++
                val t = at(day, 18, if (day == today) 42 else 5 + n * 3 % 20)
                busy += t..t.plusMinutes(if (day == date(7, 22)) 300 else 140)
                lines += t to {
                    // Back from the flu: every T1 and T2 down a tenth before the first workout.
                    if (day > flu.endInclusive && !deloaded) {
                        deloaded = true
                        val s = store.state.value
                        for (pd in s.program.days) for (ex in s.exercises(pd)) if (ex.tier != Tier.T3 && ex.weight != null) {
                            store.dispatch(t) { Commands.editWeight(it, store.state.value.exercises(pd).first { e -> e.key == ex.key }, round5(ex.weight!! * 0.9)) }
                        }
                    }
                    val swap = !swapped && day >= date(10, 12) && store.state.value.exercises(store.state.value.nextDay).any { it.id == "face_pull" }
                    if (swap) swapped = true
                    val end = store.workout(t, n, end = day != today, swap = swap, slip = day == date(6, 17), late = day == date(7, 22))
                    if (day != today && afterWorkout(day) && day != date(7, 22)) store.stretch(end.plusMinutes(4), StretchRoutines.ID_AFTER_WORKOUT)
                }
            }
            if (day == date(8, 12)) at(day, 17, 0).let { t ->
                busy += t..t.plusMinutes(60)
                lines += t to { store.hotelGym(t) }
            }
            if (day == eveningMade) at(day, 21, 0).let { t ->
                lines += t to {
                    store.dispatch(t) { Commands.newStretchRoutine(it, EVENING, listOf(StretchMoves.CHILDS_POSE_LAT, StretchMoves.PIGEON_POSE, StretchMoves.SUPINE_SPINAL_TWIST, StretchMoves.BUTTERFLY_STRETCH)) }
                    store.dispatch(t) { s -> Commands.setStretchHold(s, s.customStretches.keys.last(), 1, 60) }
                }
            }
            for ((h, m, id) in stretches(day)) {
                val t = at(day, h, m)
                if (busy.none { t in it }) {
                    busy += t..t.plusMinutes(20)
                    lines += t to { store.stretch(t, id) }
                }
            }
            // Water through the waking day: more on training days, less on rest days.
            val cups = listOf(350, 250, 500, 250, 350, 500, 250, 350)
            val n = if (day in sick) 0 else 6 + (day.dayOfMonth * 5 % 4) + if (lifts(day) || cardio(day).isNotEmpty()) 2 else 0
            for (i in 0 until n) {
                val t = at(day, 7 + i * 14 / n, (day.dayOfMonth * 11 + i * 23) % 60)
                // Mid-workout drinks are left out so each day's lines stay in time order.
                if (t <= now && busy.none { t in it }) lines += t to { store.dispatch(t) { WaterLog.logWater(it, cups[(day.dayOfMonth + i) % cups.size], t) } }
            }
            // The last week's meals, named the way the camera names them.
            if (ChronoUnit.DAYS.between(day, today) in 0..7) for ((i, t) in meals(day).withIndex()) if (t <= now && busy.none { t in it }) {
                val (label, low, high) = dishes[i]
                lines += t to { store.dispatch(t) { listOf(FoodEstimate(FoodLog.name(t), label, low, high, confirmed = true)) } }
            }
            lines += oneOffs(store, day)
            for ((_, write) in lines.sortedBy { it.first }) write()
            d = d.plusDays(1)
        }
        return store.state.value
    }

    /** Things done once and taken back: so every kind of line the app writes appears in the log. */
    private fun oneOffs(store: Store, day: LocalDate): List<Pair<OffsetDateTime, () -> Unit>> = when (day) {
        // A routine tried and dropped, and a stretch logged by mistake.
        date(6, 20) -> listOf(at(day, 20, 30) to {
            store.dispatch(at(day, 20, 30)) { Commands.newStretchRoutine(it, "Morning", listOf(StretchMoves.PIGEON_POSE)) }
        })
        date(6, 24) -> at(day, 21, 5).let { t -> listOf(t to { store.stretch(t, "bedtime-wind-down"); store.dispatch(t.plusMinutes(9)) { s -> listOf(StretchCleared(s.stretches.keys.last())) } }) }
        date(7, 1) -> listOf(at(day, 21, 40) to { store.dispatch(at(day, 21, 40)) { s -> Commands.deleteStretchRoutine(s, s.customStretches.keys.first()) } })
        // A cup logged twice.
        date(6, 10) -> at(day, 13, 2).let { t -> listOf(t to { store.dispatch(t) { WaterLog.logWater(it, 500, t) }; store.dispatch(t.plusMinutes(1)) { s -> WaterLog.clearWater(s.water.keys.last()) } }) }
        // A weigh-in typed wrong and fixed, and an evening one deleted.
        date(7, 14) -> at(day, 6, 35).let { t -> listOf(t to { store.dispatch(t) { Commands.logBodyWeight(it, null, t, 197.4) }; store.dispatch(t.plusMinutes(1)) { s -> Commands.logBodyWeight(s, "w${t.toEpochSecond()}", t, 179.4) } }) }
        date(8, 3) -> at(day, 21, 0).let { t -> listOf(t to { store.dispatch(t) { Commands.logBodyWeight(it, null, t, 182.6) }; store.dispatch(t.plusMinutes(1)) { s -> Commands.clearBodyWeight(s, "w${t.toEpochSecond()}") } }) }
        // A run started by accident and discarded.
        date(9, 2) -> at(day, 12, 15).let { t -> listOf(t to { store.dispatch(t) { Commands.startCardio(it, Activities.of("running")!!, t) }; store.dispatch(t.plusMinutes(1)) { s -> Commands.clearCardio(s, s.cardioActive!!.id) } }) }
        // A push/pull/legs split copied to look at, then deleted.
        date(7, 30) -> at(day, 21, 20).let { t -> listOf(t to { store.dispatch(t) { Commands.copySplit(it, "ppl", "Push pull legs") }; store.dispatch(t.plusMinutes(3)) { s -> Commands.deleteSplit(s, s.customPrograms.keys.last()) } }) }
        else -> emptyList()
    }

    /**
     * A GZCLP workout at [start], the [k]th of the log; returns when it ended. Reps come from the strength curve, so a heavy day falls short.
     * [swap] does the face pull as a rear delt fly, [slip] logs a set on the wrong row and clears it, [late] leaves the workout running and fixes its time.
     */
    fun Store.workout(start: OffsetDateTime, k: Int, end: Boolean, swap: Boolean = false, slip: Boolean = false, late: Boolean = false): OffsetDateTime {
        while (true) {
            val ex = Commands.missingWeight(state.value) ?: break
            dispatch(start) { Commands.startingWeight(it, ex, startWeights[ex.id] ?: 45) }
        }
        dispatch(start) { Commands.start(it, start) }
        if (swap) dispatch(start) { s -> Commands.swap(s, s.sessionExercises().first { it.id == "face_pull" }, "Rear delt fly") }
        var t = start
        var slipped = !slip
        for (ex in state.value.sessionExercises()) {
            val w = ex.weight ?: startWeights[ex.id] ?: 45
            val fresh = 30 * (strength(ex.id, start.toLocalDate()) * if (ex.tier == Tier.T2) 0.93 else 1.0) / w - 30
            val tired = when (ex.tier) { Tier.T1 -> 0.08; Tier.T2 -> 0.12; else -> 0.1 } * ex.scheme.reps
            for (i in 1..ex.scheme.sets) {
                if (!end && t > start.plusMinutes(10)) return t
                val amrap = ex.scheme.amrap && i == ex.scheme.sets
                val can = fresh - tired * (i - 1) + noise(k, i)
                val reps = if (amrap) floor(can).toInt().coerceIn(0, if (ex.tier == Tier.T3) 30 else ex.scheme.reps + 7) else floor(can).toInt().coerceIn(0, ex.scheme.reps)
                t = t.plusSeconds(rest.getValue(ex.tier) + 70L + (k * 13 + i * 7) % 40)
                if (!slipped && ex.tier == Tier.T3 && i == 2) {
                    slipped = true
                    dispatch(t) { Commands.logSet(it, ex, 3, reps, w, t, rest) }
                    t = t.plusSeconds(20)
                    dispatch(t) { Commands.clearSet(it, ex, 3) }
                }
                // RPE on last sets only, like a lifter who rates just the hard ones: closer to failure is higher.
                val rpe = if (i == ex.scheme.sets) (10 - (can - reps)).coerceIn(6.5, 10.0) else null
                dispatch(t) { Commands.logSet(it, ex, i, reps, w, t, rest, rpe) }
            }
        }
        if (!end) return t
        val e = t.plusSeconds(if (late) 9000 else 90)
        val rm = Commands.pendingFiveRm(state.value)?.let { round5(strength(it.id, e.toLocalDate()) * 6 / 7) }
        dispatch(e) { Commands.end(it, e, rm) }
        if (late) dispatch(e) { s -> Commands.editSession(s, s.sessions.last().id, java.time.Duration.between(start, t).seconds + 90) }
        return e
    }

    /** Three lifts at seven tenths on the beach week, done as an open session that does not count. */
    private fun Store.hotelGym(start: OffsetDateTime) {
        dispatch(start) { Commands.startOpen(it, start) }
        var t = start
        for (ex in state.value.sessionExercises().take(3)) for (i in 1..3) {
            t = t.plusMinutes(3)
            dispatch(t) { Commands.logSet(it, ex, i, 8, round5((ex.weight ?: 45) * 0.7), t, rest) }
        }
        t.plusMinutes(2).let { e -> dispatch(e) { Commands.end(it, e, null, counts = false) } }
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
