package dev.gzclp.tracker.stretch

import kotlin.math.roundToInt

/** A dynamic rep's parts: ease into the stretch, hold at the far end, ease back out. */
enum class Phase(val label: String) { STRETCH("Stretch"), HOLD("Hold"), RELEASE("Release") }

/**
 * A dynamic move's rep in seconds per [Phase]. A flow (a circle, a stride) runs straight through its rep without stopping, so
 * it is all [stretch] and its rep has a single phase.
 */
data class Pacing(val stretch: Double, val hold: Double, val release: Double) {
    val period: Double get() = stretch + hold + release

    /** The phases a rep runs through, in order, with their seconds; a phase of no time is left out. */
    val phases: List<Pair<Phase, Double>>
        get() = listOf(Phase.STRETCH to stretch, Phase.HOLD to hold, Phase.RELEASE to release).filter { it.second > 0 }

    companion object {
        /** A brisk rep for a stretch held still elsewhere: ease in, a beat at the end, ease out. */
        val DYNAMIC = Pacing(1.5, 1.0, 1.5)
    }
}

/** What the player does on getting ready, on each side's hold, and on the switch between them. */
enum class Stage { READY, HOLD, SWITCH }

/** A stretch of a move's time: [stage] on [side] (0 or 1), from [startMs] into the move for [lengthMs]. */
data class Span(val stage: Stage, val side: Int, val startMs: Long, val lengthMs: Long) {
    val endMs: Long get() = startMs + lengthMs
}

/** Where a dynamic hold is: [rep] (from 0) of [reps], in [phase], [phaseFraction] and [repFraction] of the way through it. */
data class RepPosition(val rep: Int, val reps: Int, val phase: Phase, val phaseFraction: Float, val repFraction: Float)

/** A cue [atMs] into a move: [words] to speak when sound is on, or a tone when null. Every cue taps a haptic. */
data class Cue(val atMs: Long, val words: String?)

/**
 * One move's timeline: get ready, hold (the first side of a one-sided move), then switch and hold the second side. A dynamic
 * move ([pacing] not null) fills each hold with whole reps, stretched or squeezed from the pacing's tempo so the last rep ends
 * as the hold does.
 */
class MoveSchedule(val move: StretchMove, val pacing: Pacing?) {
    val spans: List<Span> = buildList {
        add(Span(Stage.READY, 0, 0, READY_MS))
        add(Span(Stage.HOLD, 0, READY_MS, move.firstSideSec * 1000L))
        if (move.secondSideSec > 0) {
            add(Span(Stage.SWITCH, 1, last().endMs, SWITCH_MS))
            add(Span(Stage.HOLD, 1, last().endMs, move.secondSideSec * 1000L))
        }
    }

    val totalMs: Long get() = spans.last().endMs

    /** The span under way [ms] into the move; past the end, the last. */
    fun spanAt(ms: Long): Span = spans.firstOrNull { ms < it.endMs } ?: spans.last()

    /** How many reps a hold of [lengthMs] fits at the pacing's tempo, at least one; 0 for a still hold. */
    fun reps(lengthMs: Long): Int = pacing?.let { (lengthMs / 1000.0 / it.period).roundToInt().coerceAtLeast(1) } ?: 0

    /** Where a dynamic hold stands [ms] into the move; null outside a hold or for a still move. */
    fun repAt(ms: Long): RepPosition? {
        val p = pacing ?: return null
        val span = spanAt(ms)
        if (span.stage != Stage.HOLD) return null
        val reps = reps(span.lengthMs)
        val repMs = span.lengthMs.toDouble() / reps
        val into = (ms - span.startMs).coerceIn(0, span.lengthMs - 1).toDouble()
        val rep = (into / repMs).toInt().coerceAtMost(reps - 1)
        var t = (into - rep * repMs) / repMs * p.period
        val repFraction = (t / p.period).toFloat()
        for ((phase, sec) in p.phases) {
            if (t < sec) return RepPosition(rep, reps, phase, (t / sec).toFloat(), repFraction)
            t -= sec
        }
        return RepPosition(rep, reps, p.phases.last().first, 1f, repFraction)
    }

    /**
     * The spoken script, in order: the move's name as it starts, "Start" as each hold does, "Switch" between the sides, and
     * "3", "2", "1" on the last seconds of each hold that has room for them. A dynamic hold adds a tone at each change of
     * phase, except where the countdown speaks.
     */
    val cues: List<Cue> = buildList {
        add(Cue(0, move.name))
        for (span in spans) when (span.stage) {
            Stage.READY -> {}
            Stage.SWITCH -> add(Cue(span.startMs, "Switch"))
            Stage.HOLD -> {
                add(Cue(span.startMs, "Start"))
                val count = (3 downTo 1).map { it to span.endMs - it * 1000L }.filter { it.second >= span.startMs + 1000 }
                val quiet = count.firstOrNull()?.second?.minus(500) ?: span.endMs
                pacing?.let { p ->
                    val reps = reps(span.lengthMs)
                    val repMs = span.lengthMs.toDouble() / reps
                    for (r in 0 until reps) {
                        var at = 0.0
                        for ((_, sec) in p.phases) {
                            val ms = (span.startMs + r * repMs + at / p.period * repMs).roundToInt().toLong()
                            if (ms > span.startMs && ms < quiet) add(Cue(ms, null))
                            at += sec
                        }
                    }
                }
                for ((n, at) in count) add(Cue(at, "$n"))
            }
        }
    }.sortedBy { it.atMs }

    companion object {
        /** Long enough to get down to the floor before the first hold. */
        const val READY_MS = 8_000L

        /** Long enough to change sides in a one-sided stretch. */
        const val SWITCH_MS = 3_000L
    }
}
