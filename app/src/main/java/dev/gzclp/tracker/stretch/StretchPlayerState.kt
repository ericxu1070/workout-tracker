package dev.gzclp.tracker.stretch

import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.StretchLogged
import java.time.OffsetDateTime

/**
 * State machine managing the guided stretch player session.
 */
class StretchPlayerState(
    val routine: StretchRoutine,
    initialMoveIndex: Int = 0,
) {
    val moves: List<StretchMove> = routine.moves

    var moveIndex: Int = initialMoveIndex.coerceIn(0, (moves.size - 1).coerceAtLeast(0))
        private set

    val currentMove: StretchMove? get() = moves.getOrNull(moveIndex)
    val nextMove: StretchMove? get() = moves.getOrNull(moveIndex + 1)

    var secondsRemaining: Int = currentMove?.holdSec ?: 0
        private set

    var elapsedTotalSec: Long = 0L
        private set

    var isPaused: Boolean = false
        private set

    var isFinished: Boolean = moves.isEmpty()
        private set

    private val completedMoveList = mutableListOf<String>()
    val completedMoves: List<String> get() = completedMoveList.toList()

    val progressFraction: Float
        get() {
            if (moves.isEmpty()) return 1f
            val curHold = currentMove?.holdSec ?: 1
            val curProgress = (curHold - secondsRemaining).coerceAtLeast(0).toFloat() / curHold
            return (moveIndex + curProgress) / moves.size
        }

    fun tick() {
        if (isPaused || isFinished) return
        elapsedTotalSec++
        if (secondsRemaining > 1) {
            secondsRemaining--
        } else {
            // Move complete!
            currentMove?.let { completedMoveList.add(it.id) }
            if (moveIndex + 1 < moves.size) {
                moveIndex++
                secondsRemaining = moves[moveIndex].holdSec
            } else {
                secondsRemaining = 0
                isFinished = true
            }
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
        if (isFinished) return
        currentMove?.let { completedMoveList.add(it.id) }
        if (moveIndex + 1 < moves.size) {
            moveIndex++
            secondsRemaining = moves[moveIndex].holdSec
        } else {
            secondsRemaining = 0
            isFinished = true
        }
    }

    fun skipPrev() {
        if (moveIndex > 0) {
            moveIndex--
            secondsRemaining = moves[moveIndex].holdSec
            isFinished = false
        } else {
            secondsRemaining = currentMove?.holdSec ?: 0
        }
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
}
