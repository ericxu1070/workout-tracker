package dev.gzclp.tracker.stretch

/**
 * 2D stick-figure skeleton pose representation for keyframed animations.
 * Coordinates and lengths are normalized relative to canvas dimensions (0f..1f).
 * Angles are in degrees:
 * -90° = up (-Y)
 *   0° = right (+X)
 *  90° = down (+Y)
 * 180° = left (-X)
 */
data class Pose(
    val rootX: Float = 0.5f,
    val rootY: Float = 0.65f,
    val torsoAngle: Float = -90f,
    val torsoLen: Float = 0.22f,
    val armLen: Float = 0.12f,
    val forearmLen: Float = 0.12f,
    val thighLen: Float = 0.15f,
    val calfLen: Float = 0.15f,
    // Arm angles (degrees)
    val leftArmAngle: Float = 30f,
    val leftForearmAngle: Float = 30f,
    val rightArmAngle: Float = -30f,
    val rightForearmAngle: Float = -30f,
    // Leg angles (degrees)
    val leftThighAngle: Float = 80f,
    val leftCalfAngle: Float = 85f,
    val rightThighAngle: Float = 100f,
    val rightCalfAngle: Float = 95f,
    // Head tilt offset
    val headTiltAngle: Float = 0f,
) {
    companion object {
        fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

        /**
         * Interpolates smoothly between [from] and [to] poses given fraction [t] in [0f..1f].
         */
        fun interpolate(from: Pose, to: Pose, t: Float): Pose {
            val clamped = t.coerceIn(0f, 1f)
            return Pose(
                rootX = lerp(from.rootX, to.rootX, clamped),
                rootY = lerp(from.rootY, to.rootY, clamped),
                torsoAngle = lerp(from.torsoAngle, to.torsoAngle, clamped),
                torsoLen = lerp(from.torsoLen, to.torsoLen, clamped),
                armLen = lerp(from.armLen, to.armLen, clamped),
                forearmLen = lerp(from.forearmLen, to.forearmLen, clamped),
                thighLen = lerp(from.thighLen, to.thighLen, clamped),
                calfLen = lerp(from.calfLen, to.calfLen, clamped),
                leftArmAngle = lerp(from.leftArmAngle, to.leftArmAngle, clamped),
                leftForearmAngle = lerp(from.leftForearmAngle, to.leftForearmAngle, clamped),
                rightArmAngle = lerp(from.rightArmAngle, to.rightArmAngle, clamped),
                rightForearmAngle = lerp(from.rightForearmAngle, to.rightForearmAngle, clamped),
                leftThighAngle = lerp(from.leftThighAngle, to.leftThighAngle, clamped),
                leftCalfAngle = lerp(from.leftCalfAngle, to.leftCalfAngle, clamped),
                rightThighAngle = lerp(from.rightThighAngle, to.rightThighAngle, clamped),
                rightCalfAngle = lerp(from.rightCalfAngle, to.rightCalfAngle, clamped),
                headTiltAngle = lerp(from.headTiltAngle, to.headTiltAngle, clamped),
            )
        }

        // Common reference poses
        val STANDING_NEUTRAL = Pose(
            rootX = 0.5f,
            rootY = 0.55f,
            torsoAngle = -90f,
            leftArmAngle = 75f,
            leftForearmAngle = 80f,
            rightArmAngle = 105f,
            rightForearmAngle = 100f,
            leftThighAngle = 85f,
            leftCalfAngle = 90f,
            rightThighAngle = 95f,
            rightCalfAngle = 90f,
        )

        val SEATED_NEUTRAL = Pose(
            rootX = 0.45f,
            rootY = 0.72f,
            torsoAngle = -90f,
            leftArmAngle = 50f,
            leftForearmAngle = 60f,
            rightArmAngle = 130f,
            rightForearmAngle = 120f,
            leftThighAngle = 0f,
            leftCalfAngle = 90f,
            rightThighAngle = 0f,
            rightCalfAngle = 90f,
        )

        val KNEELING_NEUTRAL = Pose(
            rootX = 0.5f,
            rootY = 0.68f,
            torsoAngle = -90f,
            leftArmAngle = 80f,
            leftForearmAngle = 85f,
            rightArmAngle = 100f,
            rightForearmAngle = 95f,
            leftThighAngle = 90f,
            leftCalfAngle = 180f,
            rightThighAngle = 90f,
            rightCalfAngle = 180f,
        )

        val PRONE_NEUTRAL = Pose(
            rootX = 0.5f,
            rootY = 0.78f,
            torsoAngle = 180f,
            leftArmAngle = 180f,
            leftForearmAngle = 180f,
            rightArmAngle = 180f,
            rightForearmAngle = 180f,
            leftThighAngle = 0f,
            leftCalfAngle = 0f,
            rightThighAngle = 0f,
            rightCalfAngle = 0f,
        )
    }
}
