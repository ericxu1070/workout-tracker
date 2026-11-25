package dev.gzclp.tracker.stretch

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.StretchLogged
import java.time.OffsetDateTime

/**
 * State machine managing the guided stretch player session. Each move opens with a [READY_SEC] get-ready countdown, then
 * its hold; a one-sided move holds its first side, counts [SWITCH_SEC] to change sides, then holds the second. Its fields
 * are snapshot state, so a screen reading them recomposes on every tick.
 */
class StretchPlayerState(
    val routine: StretchRoutine,
    initialMoveIndex: Int = 0,
) {
    val moves: List<StretchMove> = routine.moves

    var moveIndex: Int by mutableIntStateOf(initialMoveIndex.coerceIn(0, (moves.size - 1).coerceAtLeast(0)))
        private set

    val currentMove: StretchMove? get() = moves.getOrNull(moveIndex)
    val nextMove: StretchMove? get() = moves.getOrNull(moveIndex + 1)

    /** True from the switch on, while a one-sided move works its second side. */
    var isSecondSide: Boolean by mutableStateOf(false)
        private set

    /** Seconds left to get into position (or switch sides) before the hold starts; 0 while holding. */
    var readyRemaining: Int by mutableIntStateOf(if (moves.isEmpty()) 0 else READY_SEC)
        private set

    val isGettingReady: Boolean get() = readyRemaining > 0

    /** The length of the countdown under way: getting ready, switching sides, or this side's hold. */
    val countdownSec: Int
        get() = when {
            isGettingReady -> if (isSecondSide) SWITCH_SEC else READY_SEC
            isSecondSide -> currentMove?.secondSideSec ?: 0
            else -> currentMove?.firstSideSec ?: 0
        }

    var secondsRemaining: Int by mutableIntStateOf(currentMove?.firstSideSec ?: 0)
        private set

    var elapsedTotalSec: Long by mutableLongStateOf(0L)
        private set

    var isPaused: Boolean by mutableStateOf(false)
        private set

    var isFinished: Boolean by mutableStateOf(moves.isEmpty())
        private set

    private val completedMoveList = mutableListOf<String>()
    val completedMoves: List<String> get() = completedMoveList.toList()

    val progressFraction: Float
        get() {
            if (moves.isEmpty()) return 1f
            val move = currentMove ?: return 1f
            val held = if (isSecondSide) move.firstSideSec + move.secondSideSec - secondsRemaining else move.firstSideSec - secondsRemaining
            val curProgress = held.coerceAtLeast(0).toFloat() / move.holdSec
            return (moveIndex + curProgress) / moves.size
        }

    fun tick() {
        if (isPaused || isFinished) return
        elapsedTotalSec++
        when {
            readyRemaining > 0 -> readyRemaining--
            secondsRemaining > 1 -> secondsRemaining--
            !isSecondSide && (currentMove?.secondSideSec ?: 0) > 0 -> {
                isSecondSide = true
                secondsRemaining = currentMove!!.secondSideSec
                readyRemaining = SWITCH_SEC
            }
            else -> advance()
        }
    }

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
        if (!isFinished) advance()
    }

    fun skipPrev() {
        if (moves.isEmpty()) return
        begin((moveIndex - 1).coerceAtLeast(0))
        isFinished = false
    }

    private fun advance() {
        currentMove?.let { completedMoveList.add(it.id) }
        if (moveIndex + 1 < moves.size) {
            begin(moveIndex + 1)
        } else {
            secondsRemaining = 0
            readyRemaining = 0
            isFinished = true
        }
    }

    private fun begin(i: Int) {
        moveIndex = i
        isSecondSide = false
        secondsRemaining = moves[i].firstSideSec
        readyRemaining = READY_SEC
    }

    /**
     * Builds the [StretchLogged] event to commit to the workout log.
     */
    fun createLoggedEvent(
        id: String? = null,
        at: OffsetDateTime = OffsetDateTime.now(),
    ): StretchLogged {
        val entryId = id ?: "st${at.toEpochSecond()}"
        val finalMoves = (completedMoveList + listOfNotNull(currentMove?.id)).distinct()
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
        const val READY_SEC = 8

        /** Long enough to change sides in a one-sided stretch. */
        const val SWITCH_SEC = 3
    }
}
