package dev.gzclp.tracker.stretch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.StretchLogged
import java.time.OffsetDateTime

/**
 * State machine managing the guided stretch player session, run in milliseconds along each move's [MoveSchedule]: a
 * [READY_SEC] get-ready countdown, then its hold; a one-sided move holds its first side, counts [SWITCH_SEC] to change sides,
 * then holds the second. A dynamic move fills its holds with reps paced by [pacing]. The whole-second fields change only when
 * their value does, so a screen reading them recomposes once a second; [msIntoMove] moves every frame, for drawing.
 */
class StretchPlayerState(
    val routine: StretchRoutine,
    initialMoveIndex: Int = 0,
    private val pacing: (StretchMove) -> Pacing? = { if (it.isDynamic) Motions.pacing(it) else null },
) {
    val moves: List<StretchMove> = routine.moves

    var moveIndex: Int by mutableIntStateOf(initialMoveIndex.coerceIn(0, (moves.size - 1).coerceAtLeast(0)))
        private set

    val currentMove: StretchMove? get() = moves.getOrNull(moveIndex)
    val nextMove: StretchMove? get() = moves.getOrNull(moveIndex + 1)

    /** The current move's timeline; null once there is none. */
    var schedule: MoveSchedule? by mutableStateOf(currentMove?.let { MoveSchedule(it, pacing(it)) })
        private set

    /** How far into the current move, in milliseconds. */
    var msIntoMove: Long by mutableLongStateOf(0L)
        private set

    /** The getting ready, hold or switch under way. */
    var span: Span? by mutableStateOf(schedule?.spans?.first())
        private set

    /** True from the switch on, while a one-sided move works its second side. */
    val isSecondSide: Boolean get() = span?.side == 1

    val isGettingReady: Boolean get() = span?.stage.let { it == Stage.READY || it == Stage.SWITCH }

    /** Whole seconds left to get into position (or switch sides) before the hold starts, rounded up; 0 while holding. */
    var readyRemaining: Int by mutableIntStateOf(if (moves.isEmpty()) 0 else READY_SEC)
        private set

    /** Whole seconds left of this side's hold, rounded up; the whole side while getting ready for it. */
    var secondsRemaining: Int by mutableIntStateOf(currentMove?.firstSideSec ?: 0)
        private set

    /** The length of the countdown under way: getting ready, switching sides, or this side's hold. */
    val countdownSec: Int get() = ((span?.lengthMs ?: 0L) / 1000).toInt()

    /** Milliseconds left of the countdown under way, for a ring that drains every frame. */
    val countdownMsLeft: Long get() = span?.let { it.endMs - msIntoMove }?.coerceAtLeast(0) ?: 0

    /** Where a dynamic move's reps stand; null while getting ready and for a still hold. */
    val rep: RepPosition? get() = schedule?.repAt(msIntoMove)

    /** Which rep and phase a dynamic hold is in, changing only when they do. */
    var repPhase: Pair<Int, Phase>? by mutableStateOf(null)
        private set

    /** Seconds into a dynamic move's motion, for its figure to keep time with the reps; 0 before the hold. */
    val motionSec: Double get() = rep?.let { r -> currentMove?.let { Motions.motionSec(it, r) } } ?: 0.0

    private var playedMs = 0L

    val elapsedTotalSec: Long get() = playedMs / 1000

    var isPaused: Boolean by mutableStateOf(false)
        private set

    var isFinished: Boolean by mutableStateOf(moves.isEmpty())
        private set

    private val completedMoveList = mutableListOf<String>()
    val completedMoves: List<String> get() = completedMoveList.toList()

    private val pending = ArrayDeque<Cue>().apply { schedule?.cues?.filter { it.atMs == 0L }?.let(::addAll) }

    /** The cues passed since the last call, oldest first. */
    fun drainCues(): List<Cue> = pending.toList().also { pending.clear() }

    val progressFraction: Float
        get() {
            if (moves.isEmpty()) return 1f
            val move = currentMove ?: return 1f
            val sp = span ?: return 1f
            val held = when {
                sp.stage == Stage.HOLD -> (msIntoMove - sp.startMs) + sp.side * move.firstSideSec * 1000L
                sp.side == 1 -> move.firstSideSec * 1000L
                else -> 0L
            }
            return (moveIndex + held.toFloat() / (move.holdSec * 1000L)) / moves.size
        }

    /** Moves the clock on [ms], through as many moves as that takes, collecting the cues it passes. */
    fun advance(ms: Long) {
        if (isPaused || isFinished) return
        playedMs += ms
        var left = ms
        while (left > 0 && !isFinished) {
            val s = schedule ?: return
            val step = minOf(left, s.totalMs - msIntoMove)
            val from = msIntoMove
            msIntoMove += step
            left -= step
            s.cues.filterTo(pending) { it.atMs > from && it.atMs <= msIntoMove && it.atMs < s.totalMs }
            if (msIntoMove >= s.totalMs) advanceMove() else sync()
        }
    }

    /** One second on, as the clock used to tick. */
    fun tick() = advance(1000)

    fun togglePause() {
        isPaused = !isPaused
    }

    fun pause() {
        isPaused = true
    }

    fun resume() {
        isPaused = false
    }

    fun skipNext() {
        if (!isFinished) advanceMove()
    }

    fun skipPrev() {
        if (moves.isEmpty()) return
        begin((moveIndex - 1).coerceAtLeast(0))
        isFinished = false
    }

    /** Whether the move under way reached its hold; a move skipped while getting ready was never done. */
    private fun held(): Boolean = schedule?.let { s -> msIntoMove > s.spans.first { it.stage == Stage.HOLD }.startMs } ?: false

    private fun advanceMove() {
        if (held()) currentMove?.let { completedMoveList.add(it.id) }
        if (moveIndex + 1 < moves.size) {
            begin(moveIndex + 1)
        } else {
            msIntoMove = schedule?.totalMs ?: 0
            secondsRemaining = 0
            readyRemaining = 0
            repPhase = null
            isFinished = true
        }
    }

    private fun begin(i: Int) {
        moveIndex = i
        val s = MoveSchedule(moves[i], pacing(moves[i]))
        schedule = s
        msIntoMove = 0
        s.cues.filterTo(pending) { it.atMs == 0L }
        sync()
    }

    /** Brings the whole-second fields up to the clock; each changes, and recomposes its readers, only when its value does. */
    private fun sync() {
        val s = schedule ?: return
        val sp = s.spanAt(msIntoMove)
        span = sp
        fun ceilSec(ms: Long) = ((ms + 999) / 1000).toInt()
        val hold = if (sp.stage == Stage.HOLD) sp else s.spans.first { it.stage == Stage.HOLD && it.side == sp.side }
        readyRemaining = if (sp.stage == Stage.HOLD) 0 else ceilSec(sp.endMs - msIntoMove)
        secondsRemaining = ceilSec(hold.endMs - maxOf(msIntoMove, hold.startMs))
        repPhase = rep?.let { it.rep to it.phase }
    }

    init {
        sync()
    }

    /**
     * Builds the [StretchLogged] event to commit to the workout log.
     */
    fun createLoggedEvent(
        id: String? = null,
        at: OffsetDateTime = OffsetDateTime.now(),
    ): StretchLogged {
        val entryId = id ?: "st${at.toEpochSecond()}"
        val finalMoves = (completedMoveList + listOfNotNull(currentMove?.id?.takeIf { !isFinished && held() })).distinct()
        return StretchLogged(
            id = entryId,
            at = LogCodec.TS.format(at),
            routine = routine.id,
            durationSec = elapsedTotalSec.coerceAtLeast(1L),
            moves = finalMoves,
        )
    }

    companion object {
        /** Long enough to get down to the floor or switch sides. */
        const val READY_SEC = (MoveSchedule.READY_MS / 1000).toInt()

        /** Long enough to change sides in a one-sided stretch. */
        const val SWITCH_SEC = (MoveSchedule.SWITCH_MS / 1000).toInt()
    }
}
