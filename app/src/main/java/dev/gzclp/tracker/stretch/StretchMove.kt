package dev.gzclp.tracker.stretch

/**
 * A single stretch move with target body area, hold duration, one-line cue, and keyframed poses.
 */
data class StretchMove(
    val id: String,
    val name: String,
    val targetArea: BodyArea,
    val holdSec: Int,
    val cue: String,
    val startPose: Pose,
    val endPose: Pose,
    val holdPose: Pose = endPose,
    val isBilateral: Boolean = false,
    val isDynamic: Boolean = false,
) {
    init {
        require(holdSec in 1..300) { "Hold time must be positive, got $holdSec for $id" }
        require(cue.isNotBlank()) { "Cue must not be blank for $id" }
    }
}
