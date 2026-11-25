package dev.gzclp.tracker.stretch

/**
 * A single stretch move with target body area, hold duration, and one-line cue.
 */
data class StretchMove(
    val id: String,
    val name: String,
    val targetArea: BodyArea,
    val holdSec: Int,
    val cue: String,
    val isBilateral: Boolean = false,
    val isDynamic: Boolean = false,
) {
    init {
        require(holdSec in 1..300) { "Hold time must be positive, got $holdSec for $id" }
        require(cue.isNotBlank()) { "Cue must not be blank for $id" }
    }
}
