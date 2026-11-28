package dev.gzclp.tracker.log

import dev.gzclp.tracker.engine.Activities
import dev.gzclp.tracker.engine.Activity
import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.engine.ChangeKind
import dev.gzclp.tracker.engine.Cycle
import dev.gzclp.tracker.engine.CycleState
import dev.gzclp.tracker.engine.Increments
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Outcome
import dev.gzclp.tracker.engine.Plates
import dev.gzclp.tracker.engine.Program
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Progression
import dev.gzclp.tracker.engine.Schemes
import dev.gzclp.tracker.engine.Slot
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.Wave
import dev.gzclp.tracker.engine.round5
import dev.gzclp.tracker.stretch.StretchMove
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchRoutine
import dev.gzclp.tracker.stretch.StretchRoutines
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.roundToInt

/** Turns user intent plus current state into the events to append. Pure. */
object Commands {
    fun setup(fiveRm: Map<Lift, Int>, program: String): List<Event> = buildList {
        add(ProgramStart(fiveRm.mapKeys { it.key.id }, program))
        Increments().let { add(IncrementsSet(it.upperStep, it.lowerStep, it.cycleBump)) }
        Catalog.all.forEach { add(ExerciseDefine(it.id, it.name, it.lower)) }
        for ((lift, rm) in fiveRm) {
            add(ProgressionEvent(lift.id, Tier.T1.id, ChangeKind.INITIAL.id, toWeight = Progression.t1FromFiveRm(rm), toStage = Schemes.t1[0].label, fiveRm = rm))
            add(ProgressionEvent(lift.id, Tier.T2.id, ChangeKind.INITIAL.id, toWeight = Progression.t2FromFiveRm(rm), toStage = Schemes.t2[0].label))
            add(cycleEvent(lift.id, ChangeKind.INITIAL, null, CycleState(Cycle.baseFromFiveRm(rm)), Increments().step(!lift.upper), null, null))
        }
    }

    /** A cycle tier line: [Tier.CUSTOM] keyed by [wave] when it is set, [Tier.CYCLE] otherwise. */
    private fun cycleEvent(id: String, kind: ChangeKind, from: Exercise?, to: CycleState, step: Int, sessionId: String?, wave: Wave?) =
        ProgressionEvent(
            id, (if (wave == null) Tier.CYCLE else Tier.CUSTOM).id, kind.id, from?.weight, Cycle.weight(to, step), from?.scheme?.label,
            (wave?.weeks ?: Cycle.weeks)[to.week].label, sessionId, cycleWeek = to.week + 1, cycleBase = to.base, scheme = wave?.key,
        )

    private fun cycleEvent(state: AppState, ex: Exercise, kind: ChangeKind, to: CycleState, sessionId: String?) =
        cycleEvent(ex.id, kind, ex, to, state.step(ex.id), sessionId, ex.wave)

    /** Custom exercises that were typed in place of a built-in one, each merged into it once. */
    private val merges = listOf(ExerciseRemap("pull_ups", "pull_up", bodyweight = true), ExerciseRemap("incl", "incline_db_press"))

    /** The [merges] this log still needs: a custom exercise with that id exists and was never merged before. */
    fun merge(state: AppState): List<Event> =
        merges.filter { r -> r.from in state.moves && r.from !in state.remappedFrom && Catalog.all.none { it.id == r.from } }

    fun chooseProgram(state: AppState, id: String): List<Event> = if (id == state.programId) emptyList() else listOf(ProgramChosen(id))

    fun setIncrements(state: AppState, to: Increments): List<Event> =
        if (to == state.increments) emptyList() else listOf(IncrementsSet(to.upperStep, to.lowerStep, to.cycleBump))

    /** The first exercise of [day] that still needs a starting weight. */
    fun missingWeight(state: AppState, day: ProgramDay = state.nextDay): Exercise? = state.exercises(day).firstOrNull { it.weight == null }

    fun startingWeight(state: AppState, ex: Exercise, weight: Int): List<Event> {
        if (ex.weight != null) return emptyList()
        val w = round5(weight.toDouble()).coerceAtLeast(0)
        return listOf(
            if (ex.tier.cyclic) cycleEvent(ex.id, ChangeKind.INITIAL, null, CycleState(w), state.step(ex.id), null, ex.wave)
            else ProgressionEvent(ex.id, ex.tier.id, ChangeKind.INITIAL.id, toWeight = w, toStage = Schemes.of(ex.tier)[0].label),
        )
    }

    fun start(state: AppState, now: OffsetDateTime): List<Event> =
        if (state.active != null || state.cardioActive != null || !state.started || missingWeight(state) != null || state.exercises(state.nextDay).isEmpty()) emptyList()
        else listOf(SessionStart("s" + now.toEpochSecond(), state.programId, state.nextDay.id))

    /**
     * Logs a set, or corrects one already logged (keeping its rest fields). [rpe] outside 1 to 10 is dropped, others round to a half.
     * With [sets] the line is a top-sets session's: the exercise's best set and how many sets it got in all, with no rest and no AMRAP.
     */
    fun logSet(
        state: AppState, ex: Exercise, setIndex: Int, reps: Int, weight: Int, now: OffsetDateTime, restTargets: Map<Tier, Int>, rpe: Double? = null,
        sets: Int? = null,
    ): List<Event> {
        val a = state.active ?: return emptyList()
        val key = SetKey(ex.id, ex.tier, setIndex)
        val existing = a.sets[key]?.event
        val prev = if (sets != null) null else (a.sets - key).values.maxByOrNull { it.ts }
        val scheme = ex.scheme
        val target = ex.weight ?: 0
        val event = SetLogged(
            sessionId = a.id, exercise = ex.id, tier = ex.tier.id, stage = scheme.label, setIndex = setIndex,
            targetReps = scheme.reps, actualReps = reps.coerceAtLeast(0), targetWeight = target, weight = weight.coerceAtLeast(0),
            amrap = sets == null && scheme.amrap && setIndex == scheme.sets,
            restSec = if (existing != null) existing.restSec else prev?.let { Duration.between(it.ts, now).seconds.toInt() },
            restTargetSec = if (existing != null) existing.restTargetSec else prev?.let { p -> Tier.of(p.event.tier)?.let { restSec(it, p.event.targetReps, restTargets) } },
            rpe = rpe?.let { Math.round(it * 2) / 2.0 }?.takeIf { it in 1.0..10.0 },
            sets = sets?.coerceIn(TOP_SETS),
        ).let { bodyweight(state, it, existing, now) }
        return if (event == existing) emptyList() else listOf(event)
    }

    /** Bodyweight moves snapshot the body weight in effect, so later weigh-ins never rewrite history; an assisted variant's weight is help, logged negative. */
    private fun bodyweight(state: AppState, e: SetLogged, existing: SetLogged?, now: OffsetDateTime): SetLogged =
        if (!isCalisthenics(e.exercise)) e
        else e.copy(
            addedLb = e.weight.toDouble().let { if (e.exercise.startsWith("assisted_")) -it else it },
            bodyweightLb = existing?.bodyweightLb ?: state.snapshotBodyweight(now),
        )

    /**
     * A quick log: [sets] × [reps] of the exercise named [name] (a new one is defined), at [weight] lb or none, done just now
     * off the program. It is a whole session in one go, mode = "quick", that counts for stats only: no progression line, no
     * rotation, no strength model. Its length is an estimate, editable later like any workout's. A bodyweight move's weight is
     * what was added. Nothing while a workout or cardio runs, since a session start would replace it.
     */
    fun quickLog(state: AppState, name: String, sets: Int, reps: Int, weight: Int?, now: OffsetDateTime): List<Event> {
        if (state.active != null || state.cardioActive != null || !state.started || sets !in QUICK_SETS || reps !in QUICK_REPS) return emptyList()
        val (exercise, define) = findOrDefine(state, name) ?: return emptyList()
        // Two quick logs can land in one second.
        val id = generateSequence(now.toEpochSecond()) { it + 1 }.map { "s$it" }.first { id -> state.sessions.none { it.id == id } }
        val lines = (1..sets).map { i ->
            val e = SetLogged(
                sessionId = id, exercise = exercise, tier = Tier.CUSTOM.id, stage = "${sets}x$reps", setIndex = i,
                targetReps = reps, actualReps = reps, targetWeight = 0, weight = (weight ?: 0).coerceAtLeast(0), amrap = false,
            )
            bodyweight(state, e, null, now)
        }
        // The top-sets estimate for one line counting every set: their work and rest, and getting set up.
        val sec = topDurationSec(listOf(lines[0].copy(sets = sets)))
        val day = state.nextDay.id
        return define + SessionStart(id, state.programId, day, mode = "quick", counts = false) + lines +
            SessionEnd(id, state.programId, day, LogCodec.TS.format(now.minusSeconds(sec)), sec, counts = false)
    }

    /** The rest after a set at [tier] with [reps] target reps: a split sets it by reps, every other tier by its Settings row. */
    fun restSec(tier: Tier, reps: Int, restTargets: Map<Tier, Int>): Int? = if (tier == Tier.CUSTOM) Wave.restSec(reps) else restTargets[tier]

    fun clearSet(state: AppState, ex: Exercise, setIndex: Int): List<Event> {
        val a = state.active ?: return emptyList()
        if (SetKey(ex.id, ex.tier, setIndex) !in a.sets) return emptyList()
        return listOf(SetCleared(a.id, ex.id, ex.tier.id, setIndex))
    }

    /** Clears every set of [target] (a whole finished workout, or one exercise of it), a cardio session or a weigh-in. */
    fun delete(state: AppState, target: Deletion): List<Event> = when (target) {
        is Deletion.Workout -> clearSets(state, target.id) { true }
        is Deletion.Exercise -> clearSets(state, target.sessionId) { it.exercise == target.exercise && it.tier == target.tier }
        is Deletion.Cardio -> if (target.id in state.cardio) clearCardio(state, target.id) else emptyList()
        is Deletion.WeighIn -> clearBodyWeight(state, target.id)
    }

    private fun clearSets(state: AppState, id: String, which: (SetLogged) -> Boolean): List<Event> =
        state.sessions.firstOrNull { it.id == id }?.sets.orEmpty().filter(which).map { SetCleared(id, it.exercise, it.tier, it.setIndex) }

    /** The weight to prefill for an unlogged set: the latest weight logged for this exercise this session, else the prescription; null when neither exists (a first-time substitute). */
    fun setWeight(state: AppState, ex: Exercise, setIndex: Int): Int? {
        val sets = state.active?.sets.orEmpty()
        return (setIndex - 1 downTo 1).firstNotNullOfOrNull { sets[SetKey(ex.id, ex.tier, it)]?.event?.weight } ?: ex.weight
    }

    fun editWeight(state: AppState, ex: Exercise, weight: Int): List<Event> {
        if (ex.swappedFrom != null) return emptyList()
        val from = ex.weight ?: return startingWeight(state, ex, weight)
        if (ex.tier.cyclic) {
            val c = state.cycleState(ex.id, ex.wave) ?: return emptyList()
            val to = Cycle.manualEdit(c, weight, state.step(ex.id))
            return if (to == c) emptyList() else listOf(cycleEvent(state, ex, ChangeKind.MANUAL_EDIT, to, null))
        }
        val g = state.gz[ex.key] ?: return emptyList()
        val to = Progression.manualEdit(g, weight)
        if (to.weight == from) return emptyList()
        return listOf(ProgressionEvent(ex.id, ex.tier.id, ChangeKind.MANUAL_EDIT.id, from, to.weight, ex.scheme.label, ex.scheme.label))
    }

    fun reps(state: AppState, ex: Exercise): List<Int?> {
        val sets = state.active?.sets.orEmpty()
        return (1..ex.scheme.sets).map { sets[SetKey(ex.id, ex.tier, it)]?.event?.actualReps }
    }

    private fun outcome(state: AppState, ex: Exercise, reps: List<Int?>): Outcome {
        val g = state.gz[ex.key] ?: return Outcome.Unchanged
        val lift = Lift.of(ex.id)
        return Progression.progress(ex.tier, g, reps, lift?.increment ?: Schemes.T3_INCREMENT, lift?.t2ResetBump ?: 0)
    }

    /** The T1 lift that failed 10x1+ this session and needs a new 5RM before the session can end. */
    fun pendingFiveRm(state: AppState): Exercise? {
        val a = state.active ?: return null
        return state.exercises(a.program, a.day).firstOrNull { !it.tier.cyclic && outcome(state, it, reps(state, it)) == Outcome.NeedsFiveRm }
    }

    /** Progression lines for finishing [day] with [repsOf] per exercise. */
    private fun progressions(state: AppState, program: Program, day: ProgramDay, sessionId: String?, newFiveRm: Int?, repsOf: (Exercise) -> List<Int?>): List<ProgressionEvent> =
        state.exercises(program, day).distinctBy { it.key }.mapNotNull { ex ->
            val from = ex.weight ?: return@mapNotNull null
            val reps = repsOf(ex)
            if (ex.tier.cyclic) {
                val c = state.cycleState(ex.id, ex.wave) ?: return@mapNotNull null
                val (kind, to) = Cycle.advance(c, reps, state.increments.cycleBump, ex.weeks) ?: return@mapNotNull null
                return@mapNotNull cycleEvent(state, ex, kind, to, sessionId)
            }
            when (val o = outcome(state, ex, reps)) {
                Outcome.Unchanged -> null
                is Outcome.Change -> ProgressionEvent(ex.id, ex.tier.id, o.kind.id, from, o.to.weight, ex.scheme.label, Schemes.of(ex.tier)[o.to.stage].label, sessionId)
                Outcome.NeedsFiveRm -> {
                    val rm = round5((newFiveRm ?: from).toDouble())
                    val to = Progression.t1Reset(rm)
                    ProgressionEvent(ex.id, ex.tier.id, ChangeKind.RESET.id, from, to.weight, ex.scheme.label, Schemes.t1[0].label, sessionId, rm)
                }
            }
        }

    fun end(state: AppState, now: OffsetDateTime, newFiveRm: Int?, counts: Boolean? = null): List<Event> {
        val a = state.active ?: return emptyList()
        val duration = Duration.between(a.startedAt, now).seconds.coerceAtLeast(0)
        if (counts == false) {
            return listOf(SessionEnd(a.id, a.program.id, a.day.id, LogCodec.TS.format(a.startedAt), duration, counts = false))
        }
        val changes = progressions(state, a.program, a.day, a.id, newFiveRm) { reps(state, it) }
        return changes + SessionEnd(a.id, a.program.id, a.day.id, LogCodec.TS.format(a.startedAt), duration, counts = counts)
    }

    /** Starts an open session with mode = "open". */
    fun startOpen(state: AppState, now: OffsetDateTime, day: ProgramDay = state.nextDay): List<Event> =
        if (state.active != null || state.cardioActive != null || !state.started) emptyList()
        else listOf(SessionStart("s" + now.toEpochSecond(), state.programId, day.id, mode = "open"))

    /** Starts a top-sets session with mode = "top". It prefills what it can and asks for no starting weights. */
    fun startTop(state: AppState, now: OffsetDateTime, day: ProgramDay = state.nextDay): List<Event> =
        if (state.active != null || state.cardioActive != null || !state.started) emptyList()
        else listOf(SessionStart("s" + now.toEpochSecond(), state.programId, day.id, mode = "top"))

    /** The newest top-sets line of [exerciseId] in a finished session, which prefills its next one. */
    fun lastTop(state: AppState, exerciseId: String): SetLogged? =
        state.sessions.asReversed().firstNotNullOfOrNull { s -> s.sets.lastOrNull { it.exercise == exerciseId && it.sets != null } }

    /**
     * How long a top-sets session with these lines likely took, to 5 minutes: each set's work and the rest its reps call
     * for, and a changeover per exercise. The finish sheet starts here and the user drags it right.
     */
    fun topDurationSec(lines: Collection<SetLogged>): Long {
        val sec = lines.sumOf { (it.sets ?: 1) * (TOP_SET_SEC + Wave.restSec(it.actualReps)) + TOP_CHANGEOVER_SEC }
        return (Math.round(sec / TOP_DURATION_STEP_SEC.toDouble()) * TOP_DURATION_STEP_SEC).coerceIn(TOP_DURATION_STEP_SEC, SESSION_DURATION_SEC.last)
    }

    /**
     * [ex]'s reps for a top-sets session's progression: its top set stands in for every set. All null (nothing changes) when it
     * has no line, or one lighter than prescribed, which says nothing about the prescription.
     */
    private fun topReps(a: ActiveSession, ex: Exercise): List<Int?> {
        val top = a.sets[SetKey(ex.id, ex.tier, 1)]?.event?.takeIf { it.weight >= (ex.weight ?: 0) }
        return List(ex.scheme.sets) { top?.actualReps }
    }

    /** Ends a top-sets session as [durationSec] long and ending now. A T1 lift that misses 10×1+ restarts with no 5RM asked, its weight taken as the 5RM (so 85% of it). */
    fun endTop(state: AppState, now: OffsetDateTime, durationSec: Long): List<Event> {
        val a = state.active?.takeIf { it.top } ?: return emptyList()
        val sec = durationSec.coerceIn(SESSION_DURATION_SEC)
        val changes = progressions(state, a.program, a.day, a.id, null) { topReps(a, it) }
        return changes + SessionEnd(a.id, a.program.id, a.day.id, LogCodec.TS.format(now.minusSeconds(sec)), sec)
    }

    /** Prefills weight for [exerciseId] at [reps] from the strength model or parent ratio. */
    fun suggestWeight(state: AppState, exerciseId: String, reps: Int): Int {
        val model = state.strengthModel()
        val direct = model.suggest(exerciseId, reps, targetRir = 2.0)
        if (direct != null && direct > 0) return direct
        val meta = state.exerciseMeta[exerciseId]
        if (meta?.parent != null && meta.parentRatio != null) {
            val parentWeight = model.suggest(meta.parent, reps, targetRir = 2.0)
            if (parentWeight != null && parentWeight > 0) {
                return round5(parentWeight * meta.parentRatio).coerceAtLeast(0)
            }
        }
        val last = state.sessions.asReversed().firstNotNullOfOrNull { s ->
            s.sets.lastOrNull { it.exercise == exerciseId && it.weight > 0 }?.weight
        }
        if (last != null) return last
        return if (exerciseId in Catalog.barbell) 45 else 20
    }

    /** Sets finished session [id]'s duration, and its start when [startedAt] moves it; nothing for an unknown session, no change, or a duration out of range. */
    fun editSession(state: AppState, id: String, durationSec: Long, startedAt: OffsetDateTime? = null): List<Event> {
        val s = state.sessions.firstOrNull { it.id == id } ?: return emptyList()
        val start = startedAt?.takeIf { !it.isEqual(s.startedAt) }
        if (durationSec !in SESSION_DURATION_SEC || (durationSec == s.durationSec && start == null)) return emptyList()
        return listOf(SessionEdit(id, durationSec, start?.let(LogCodec.TS::format)))
    }

    /** The next [n] workouts in rotation order, each prescribed as if every earlier one hit every target. */
    fun upcoming(state: AppState, n: Int): List<Pair<ProgramDay, List<Exercise>>> {
        var s = state
        val program = state.active?.program ?: state.program
        val days = state.rotation(program, state.active?.day?.id ?: state.next[program.id])
        return List(n) {
            val day = days[it % days.size]
            val list = s.exercises(program, day)
            val events = progressions(s, program, day, null, null) { ex -> List(ex.scheme.sets) { ex.scheme.reps } }
            s = events.fold(s) { acc, e -> acc.apply(Entry(0, OffsetDateTime.MIN, e)) }
            day to list
        }
    }

    private fun clean(name: String) = name.trim().replace(Regex("\\s+"), " ")
    private fun label(name: String) = clean(name).take(SPLIT_NAME_MAX).trim()

    /** An id made from [name], such as `cable_crunch`, with `_2`, `_3` added when [taken]. */
    private fun newId(name: String, fallback: String, taken: (String) -> Boolean): String {
        val base = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { fallback }
        return generateSequence(1) { it + 1 }.map { if (it == 1) base else "${base}_$it" }.first { !taken(it) }
    }

    /** The id of the exercise named [name] and, for a name not seen before, the line that defines it. Null for a blank name. */
    private fun findOrDefine(state: AppState, name: String): Pair<String, List<Event>>? {
        val clean = clean(name)
        if (clean.isEmpty()) return null
        state.moves.values.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it.id to emptyList() }
        val id = newId(clean, "exercise") { it in state.moves }
        return id to listOf(ExerciseDefine(id, clean, false))
    }

    /** Adds [name] to a day: an existing exercise with that name, or a new one. */
    fun addExercise(state: AppState, program: Program, day: ProgramDay, name: String): List<Event> {
        val (id, define) = findOrDefine(state, name) ?: return emptyList()
        val list = dayList(state, program, day)
        return if (id in list) emptyList() else define + DayExercises(program.id, day.id, list + id)
    }

    /**
     * Does [ex] (as performed in the active session) as the exercise named [name], for this session only;
     * the slot's own name puts it back. Only before any set of it is logged. The substitute may not be an exercise
     * this session already has at that tier, so the slot's own lift never sees a set and keeps its progression.
     */
    fun swap(state: AppState, ex: Exercise, name: String): List<Event> {
        val a = state.active ?: return emptyList()
        if ((1..ex.scheme.sets).any { SetKey(ex.id, ex.tier, it) in a.sets }) return emptyList()
        val original = ex.swappedFrom ?: ex.id
        val (id, define) = findOrDefine(state, name) ?: return emptyList()
        if (id == ex.id) return emptyList()
        if (id == original) return listOf(ExerciseSwap(a.id, original, ex.tier.id, null))
        val taken = (state.exercises(a.program, a.day) + state.sessionExercises()).any { it.id == id && it.tier == ex.tier }
        return if (taken) emptyList() else define + ExerciseSwap(a.id, original, ex.tier.id, id)
    }

    fun removeExercise(state: AppState, program: Program, day: ProgramDay, id: String): List<Event> {
        val list = dayList(state, program, day)
        return if (id in list) listOf(DayExercises(program.id, day.id, list - id)) else emptyList()
    }

    /** The editable list for a day: the T3s in GZCLP, every exercise otherwise. */
    fun dayList(state: AppState, program: Program, day: ProgramDay): List<String> =
        state.slots(program, day).filter { !program.gzclp || it.tier == Tier.T3 }.map { it.ex }

    /**
     * A new custom split named [name], copied from the program [from] as the user has it (any program but GZCLP).
     * Each exercise starts at the source's sets × reps, 3 × 12·10·8 for a preset. Its id never reuses a built-in, split or done program id.
     */
    fun copySplit(state: AppState, from: String, name: String): List<Event> {
        val src = state.programOf(from).takeIf { it.id == from && !it.gzclp } ?: return emptyList()
        val taken = Programs.all.map { it.id } + state.customPrograms.keys + state.sessions.map { it.program.id }
        val days = src.days.map { d -> d.copy(slots = state.slots(src, d).map { it.copy(tier = Tier.CUSTOM, wave = it.wave ?: Wave.DEFAULT) }) }
        val p = Program(newId(clean(name), "split") { it in taken }, label(name), days, src.from ?: src.id)
        return if (p.validSplit) listOf(p.toDefine()) else emptyList()
    }

    /** One `program_define` line for split [id] after [edit]; nothing when the split is unknown, or the edit changes nothing or leaves it invalid. */
    private fun editSplit(state: AppState, id: String, edit: (Program) -> Program): List<Event> {
        val p = state.customPrograms[id] ?: return emptyList()
        val to = edit(p)
        return if (to == p || !to.validSplit) emptyList() else listOf(to.toDefine())
    }

    private fun editDay(state: AppState, id: String, day: String, edit: (ProgramDay) -> ProgramDay) =
        editSplit(state, id) { p -> p.copy(days = p.days.map { if (it.id == day) edit(it) else it }) }

    private fun <T> List<T>.moved(from: Int, to: Int): List<T> =
        if (from !in indices || to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }

    /** Deletes split [id]. When it is the one in use, the preset it came from takes over. */
    fun deleteSplit(state: AppState, id: String): List<Event> = if (id in state.customPrograms) listOf(ProgramRemove(id)) else emptyList()

    fun renameSplit(state: AppState, id: String, name: String) = editSplit(state, id) { it.copy(name = label(name)) }

    /** Adds an empty day titled [title] at the end; the rotation skips it until it has exercises. */
    fun addDay(state: AppState, id: String, title: String) = editSplit(state, id) { p ->
        val t = label(title)
        p.copy(days = p.days + ProgramDay(newId(t, "day") { d -> p.day(d) != null }, t, emptyList()))
    }

    /** Renames a day; its id, and so its place in the rotation and history, stays. */
    fun renameDay(state: AppState, id: String, day: String, title: String) = editDay(state, id, day) { it.copy(title = label(title)) }

    /** Deletes a day, never the last one. */
    fun removeDay(state: AppState, id: String, day: String) = editSplit(state, id) { p -> p.copy(days = p.days.filter { it.id != day }) }

    fun moveDay(state: AppState, id: String, from: Int, to: Int) = editSplit(state, id) { it.copy(days = it.days.moved(from, to)) }

    /** Adds the exercise named [name] (an existing one, or a new one) to a day at 3 × 12·10·8. */
    fun addSplitExercise(state: AppState, id: String, day: String, name: String): List<Event> {
        val (ex, define) = findOrDefine(state, name) ?: return emptyList()
        val edit = editDay(state, id, day) { it.copy(slots = it.slots + Slot(ex, Tier.CUSTOM, Wave.DEFAULT)) }
        return if (edit.isEmpty()) emptyList() else define + edit
    }

    fun removeSplitExercise(state: AppState, id: String, day: String, ex: String) = editDay(state, id, day) { d -> d.copy(slots = d.slots.filter { it.ex != ex }) }

    fun moveSplitExercise(state: AppState, id: String, day: String, from: Int, to: Int) = editDay(state, id, day) { it.copy(slots = it.slots.moved(from, to)) }

    /** Sets one exercise's sets and week reps. Its cycle state is per wave, so a new wave starts fresh and asks for a starting weight. */
    fun setWave(state: AppState, id: String, day: String, ex: String, wave: Wave) =
        editDay(state, id, day) { d -> d.copy(slots = d.slots.map { if (it.ex == ex) it.copy(wave = wave) else it }) }

    /** "Set all": every exercise in split [id] gets [wave]. */
    fun setAll(state: AppState, id: String, wave: Wave) =
        editSplit(state, id) { p -> p.copy(days = p.days.map { d -> d.copy(slots = d.slots.map { it.copy(wave = wave) }) }) }

    /**
     * A starting weight to prefill for [ex]: the best Epley 1RM among its sets in the last session that has any,
     * taken back to [ex]'s reps and rounded to [plates] for a barbell lift, to 5 lb otherwise. Null when it was never done.
     */
    fun estimate(state: AppState, ex: Exercise, plates: Plates): Int? {
        val sets = state.sessions.asReversed().firstNotNullOfOrNull { s -> s.sets.filter { it.exercise == ex.id && it.actualReps > 0 }.ifEmpty { null } } ?: return null
        val w = sets.maxOf { epley(it.weight, it.actualReps) } / (1 + ex.scheme.reps / 30.0)
        return if (ex.id in Catalog.barbell) plates.round(w.roundToInt()) else round5(w)
    }

    const val SPLIT_NAME_MAX = 40

    /**
     * A new custom stretch routine named [name] with [moves], a built-in routine's when duplicating one. Its id never reuses
     * a built-in, custom or logged routine id, so a logged session never changes routine.
     */
    fun newStretchRoutine(state: AppState, name: String, moves: List<StretchMove> = emptyList()): List<Event> {
        val n = label(name)
        if (n.isEmpty()) return emptyList()
        val taken = StretchRoutines.IDS + state.customStretches.keys + state.stretches.values.map { it.routine }
        val holds = moves.map { it.copy(holdSec = it.holdSec.coerceIn(StretchRoutines.HOLD_SEC)) }
        return listOf(StretchRoutine(newId(n, "routine") { it in taken }, n, "", "", holds, custom = true).toDefine())
    }

    /** One `stretch_routine_define` line for routine [id] after [edit]; nothing when it is unknown, unchanged, or left unnamed. */
    private fun editStretchRoutine(state: AppState, id: String, edit: (StretchRoutine) -> StretchRoutine): List<Event> {
        val r = state.customStretches[id] ?: return emptyList()
        val to = edit(r)
        return if (to == r || to.name.isEmpty()) emptyList() else listOf(to.toDefine())
    }

    fun renameStretchRoutine(state: AppState, id: String, name: String) = editStretchRoutine(state, id) { it.copy(name = label(name)) }

    /** Adds move [move] at the end, held for its own default. */
    fun addStretchMove(state: AppState, id: String, move: String): List<Event> {
        val m = StretchMoves.of(move) ?: return emptyList()
        return editStretchRoutine(state, id) { it.copy(moves = it.moves + m) }
    }

    fun removeStretchMove(state: AppState, id: String, index: Int) =
        editStretchRoutine(state, id) { r -> r.copy(moves = r.moves.filterIndexed { i, _ -> i != index }) }

    fun moveStretchMove(state: AppState, id: String, from: Int, to: Int) = editStretchRoutine(state, id) { it.copy(moves = it.moves.moved(from, to)) }

    /** Holds move [index] for [sec], split between the sides of a one-sided move. */
    fun setStretchHold(state: AppState, id: String, index: Int, sec: Int): List<Event> =
        if (sec !in StretchRoutines.HOLD_SEC) emptyList()
        else editStretchRoutine(state, id) { r -> r.copy(moves = r.moves.mapIndexed { i, m -> if (i == index) m.copy(holdSec = sec) else m }) }

    fun deleteStretchRoutine(state: AppState, id: String): List<Event> =
        if (id in state.customStretches) listOf(StretchRoutineRemove(id)) else emptyList()

    /** Logs a weigh-in, or edits the one with [id]. [lb] is kept to one decimal; nonsense weights write nothing. */
    fun logBodyWeight(state: AppState, id: String?, at: OffsetDateTime, lb: Double): List<Event> {
        val w = Math.round(lb * 10) / 10.0
        if (w !in BODY_WEIGHT_RANGE) return emptyList()
        val entryId = id ?: generateSequence(0) { it + 1 }.map { "w${at.toEpochSecond()}" + if (it == 0) "" else "_$it" }.first { it !in state.bodyWeights }
        val event = BodyWeightLogged(entryId, LogCodec.TS.format(at), w)
        val old = state.bodyWeights[entryId]
        return if (old != null && old.lb == w && old.at == at) emptyList() else listOf(event)
    }

    fun clearBodyWeight(state: AppState, id: String): List<Event> =
        if (id in state.bodyWeights) listOf(BodyWeightCleared(id)) else emptyList()

    /** Takes back the weigh-in just written to [id]: puts [before] back when it was an edit, else clears the new entry. */
    fun undoBodyWeight(state: AppState, id: String, before: BodyWeight?): List<Event> =
        if (before == null) clearBodyWeight(state, id) else logBodyWeight(state, id, before.at, before.lb)

    val BODY_WEIGHT_RANGE = 50.0..1000.0

    /** Starts a cardio session; nothing while a lifting workout or another cardio session is running. */
    fun startCardio(state: AppState, activity: Activity, now: OffsetDateTime): List<Event> {
        val name = activity.name.trim().take(Activities.NAME_MAX)
        if (state.active != null || state.cardioActive != null || activity.id.isBlank() || name.isEmpty()) return emptyList()
        return listOf(CardioStart(cardioId(state, now), activity.id, name))
    }

    /** Finishes the running session with what the stop sheet says. */
    fun stopCardio(state: AppState, now: OffsetDateTime, durationSec: Long, distanceM: Int?, rpe: Double?, notes: String?): List<Event> {
        val run = state.cardioActive ?: return emptyList()
        return listOfNotNull(cardioLine(run.id, Activity(run.activity, run.name), run.startedAt, durationSec, distanceM, rpe, notes, now))
    }

    /** Logs a past session ([id] null), or edits the finished one with [id]. An edit that changes nothing writes nothing. */
    fun logCardio(
        state: AppState, id: String?, activity: Activity, startedAt: OffsetDateTime, durationSec: Long,
        distanceM: Int?, rpe: Double?, notes: String?, now: OffsetDateTime,
    ): List<Event> {
        if (id != null && id !in state.cardio) return emptyList()
        val line = cardioLine(id ?: cardioId(state, startedAt), activity, startedAt, durationSec, distanceM, rpe, notes, now) ?: return emptyList()
        return if (id != null && line.toCardio() == state.cardio[id]) emptyList() else listOf(line)
    }

    /** Deletes a finished session, or discards the running one. */
    fun clearCardio(state: AppState, id: String): List<Event> =
        if (id in state.cardio || state.cardioActive?.id == id) listOf(CardioCleared(id)) else emptyList()

    /** The last [n] distinct activities done, newest first. */
    fun recentActivities(state: AppState, n: Int): List<Activity> =
        state.cardio.values.sortedByDescending { it.startedAt }.distinctBy { it.activity }.take(n)
            .map { Activities.of(it.activity) ?: Activity(it.activity, it.name) }

    private fun cardioId(state: AppState, at: OffsetDateTime): String =
        generateSequence(0) { it + 1 }.map { "c${at.toEpochSecond()}" + if (it == 0) "" else "_$it" }
            .first { it !in state.cardio && it != state.cardioActive?.id }

    /** The checked line, or null when the duration, start or name is out of bounds. Bad distance or RPE is dropped. */
    private fun cardioLine(
        id: String, activity: Activity, startedAt: OffsetDateTime, durationSec: Long,
        distanceM: Int?, rpe: Double?, notes: String?, now: OffsetDateTime,
    ): CardioLogged? {
        val name = activity.name.trim().take(Activities.NAME_MAX)
        if (durationSec !in CARDIO_DURATION_SEC || startedAt.isAfter(now) || activity.id.isBlank() || name.isEmpty()) return null
        return CardioLogged(
            id, activity.id, name, LogCodec.TS.format(startedAt), durationSec,
            distanceM?.takeIf { it in CARDIO_DISTANCE_M },
            rpe?.let { Math.round(it * 2) / 2.0 }?.takeIf { it in 1.0..10.0 },
            notes?.trim()?.take(CARDIO_NOTES_MAX)?.trim()?.ifEmpty { null },
        )
    }

    val CARDIO_DURATION_SEC = 1L..86_400L
    val SESSION_DURATION_SEC = 60L..86_400L
    /** How many sets a top-sets line can count for its exercise. */
    val TOP_SETS = 1..20
    /** A quick log's sets (set_index runs 1 to 10) and reps per set. */
    val QUICK_SETS = 1..10
    val QUICK_REPS = 1..100
    /** A top-sets session's estimate: the work of one set, and getting from one exercise to the next. */
    private const val TOP_SET_SEC = 40
    private const val TOP_CHANGEOVER_SEC = 120
    const val TOP_DURATION_STEP_SEC = 300L
    val CARDIO_DISTANCE_M = 1..1_000_000
    const val CARDIO_NOTES_MAX = 200
}

/** Something a History or Body weight row swipes away. */
sealed interface Deletion {
    data class Workout(val id: String) : Deletion
    data class Exercise(val sessionId: String, val exercise: String, val tier: String) : Deletion
    data class Cardio(val id: String) : Deletion
    data class WeighIn(val id: String) : Deletion
}
