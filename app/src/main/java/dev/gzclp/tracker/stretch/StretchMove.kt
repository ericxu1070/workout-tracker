package dev.gzclp.tracker.stretch

import dev.gzclp.tracker.form.Focus

/**
 * A single stretch move with target body area, hold duration, one-line cue, and [focus], where on the body it is felt.
 * An [isUnilateral] move works one side at a time: its hold is split between the two sides.
 */
data class StretchMove(
    val id: String,
    val name: String,
    val targetArea: BodyArea,
    val holdSec: Int,
    val cue: String,
    val focus: Focus,
    val isUnilateral: Boolean = false,
    val isDynamic: Boolean = false,
) {
    init {
        require(holdSec in 1..300) { "Hold time must be positive, got $holdSec for $id" }
        require(cue.isNotBlank()) { "Cue must not be blank for $id" }
    }

    /** Seconds held on the first side, or the whole hold for a move done on both sides at once. */
    val firstSideSec: Int get() = if (isUnilateral) holdSec - holdSec / 2 else holdSec

    /** Seconds held on the second side; 0 when there is none. */
    val secondSideSec: Int get() = holdSec - firstSideSec
}
