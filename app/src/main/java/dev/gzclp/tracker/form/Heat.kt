package dev.gzclp.tracker.form

import dev.gzclp.tracker.log.Muscle
import kotlin.math.min

/** Each muscle's middle on the left of the body (the right mirrors it), in its bone's frame on the [STAR]. */
private val ANCHORS: List<Pair<Muscle, Spot>> = listOf(
    Muscle.CHEST to Spot(Bone.CHEST, V(0.09, 0.17, 0.1)),
    Muscle.SHOULDERS to Spot(Bone.ARM_L, V(0.0, 0.04, 0.045)),
    Muscle.REAR_DELTS to Spot(Bone.ARM_L, V(0.0, 0.04, -0.045)),
    Muscle.BICEPS to Spot(Bone.ARM_L, V(0.0, 0.17, 0.045)),
    Muscle.TRICEPS to Spot(Bone.ARM_L, V(0.0, 0.17, -0.045)),
    Muscle.FOREARMS to Spot(Bone.FOREARM_L, V(0.0, 0.09, 0.0)),
    Muscle.ABS to Spot(Bone.WAIST, V(0.03, 0.1, 0.1)),
    Muscle.OBLIQUES to Spot(Bone.WAIST, V(0.12, 0.08, 0.03)),
    Muscle.TRAPS to Spot(Bone.CHEST, V(0.07, 0.27, -0.05)),
    Muscle.UPPER_BACK to Spot(Bone.CHEST, V(0.11, 0.08, -0.08)),
    Muscle.UPPER_BACK to Spot(Bone.CHEST, V(0.03, 0.16, -0.11)),
    Muscle.LOWER_BACK to Spot(Bone.WAIST, V(0.04, 0.08, -0.1)),
    Muscle.GLUTES to Spot(Bone.PELVIS, V(0.08, -0.03, -0.11)),
    Muscle.QUADS to Spot(Bone.THIGH_L, V(0.0, 0.2, 0.08)),
    Muscle.HAMSTRINGS to Spot(Bone.THIGH_L, V(0.0, 0.22, -0.07)),
    Muscle.ADDUCTORS to Spot(Bone.THIGH_L, V(0.05, 0.12, 0.0)),
    Muscle.CALVES to Spot(Bone.SHIN_L, V(0.0, 0.14, -0.05)),
).flatMap { (m, s) -> listOf(m to s, m to s.mirrored()) }

/** How far along the body a muscle's tint reaches from its middle, in metres; hands, feet and the head lie past every one. */
private const val REACH = 0.16

/**
 * The muscle map on the 3D figure: each point of the skin takes the muscle whose middle is nearest, shaded by that
 * muscle's [levels] share (0..1) on the same ramp as the flat chart, so neighbouring muscles meet along a seam rather than blur into each other.
 */
class Heat(levels: Map<Muscle, Float>) : Focus(REACH, *ANCHORS.map { it.second }.toTypedArray()) {
    private val level = FloatArray(ANCHORS.size) { levels[ANCHORS[it].first] ?: 0f }

    override fun at(p: V): Double {
        var best = -1
        var near = Double.MAX_VALUE
        for (i in points.indices) { val d = (p - points[i]).len; if (d < near) { near = d; best = i } }
        // Full inside the muscle, fading over its last few centimetres so skin that belongs to none keeps the plain body color.
        val l = level[best]
        return if (l <= 0f) 0.0 else (0.25 + 0.75 * l) * min(1.0, (REACH - near) / 0.03).coerceAtLeast(0.0)
    }
}

/** Standing at ease, arms a little out from the sides so the torso shows between them, for the muscle map. */
val AT_EASE = Motion(Tempo(1.0, 0.0, 1.0, 0.0)) {
    pelvis(V(0.0, STAND, 0.0))
    for (s in SIDES) {
        arm(s, raise = 14.0, plane = 0.0)
        leg(s, raise = 4.0, plane = 0.0)
    }
}
