package dev.gzclp.tracker.form

import kotlin.math.pow

/** A flat bench's pad top. */
internal const val PAD = 0.44

/**
 * A barbell hinge over the middle of the foot: the bar rides straight up and down at height [bar], the shoulders sit [ahead]
 * of it on straight arms, and the legs reach [legs] from ankle to hip, which sets how bent the knees are.
 */
private fun Poser.hinge(bar: Double, ahead: Double, legs: Double) {
    shoulders(up = -0.01)
    val sh = V(0.0, bar + drop(0.5, ahead), ahead)
    val hip = reach(V(0.0, Build.ANKLE, -0.07), sh, legs, trunkLength(-3.0, 3.0), V.BACK)
    trunk(hip, sh, waist = -3.0, chest = 3.0)
    val mid = V(0.0, bar, 0.0)
    hold(mid, 0.5, V(0.0, 0.0, -1.0), V(0.3, 0.0, -1.0))
    barbell(mid)
    for (s in SIDES) plant(s, V(s * 0.14, 0.0, 0.069), 6.0)
}

internal val LIFTS: Map<String, () -> Motion> = mapOf(
    "squat" to {
        Motion(Tempo(1.7, 0.25, 1.2, 1.0), balance = true) { d ->
            pelvis(V(0.0, lerp(STAND, 0.46, d), -0.2 * d), pitch = 6 + 38 * d)
            spine(waist = -10 + 8 * d, chest = 6.0)
            head(nod = 6 + 10 * d)
            shoulders(fwd = -0.02)
            val bar = this[Bone.CHEST].at(0.0, 0.262, -0.112).let { V(0.0, it.y, it.z) }
            carry(0.9, bar)
            hold(bar, 0.86, V(0.0, 0.2, 1.0), V(0.3, -1.0, -0.5))
            barbell(bar)
            feet(0.5, 0.12, 25.0)
        }
    },
    "bench" to {
        Motion(Tempo(1.6, 0.3, 1.1, 0.9)) { d ->
            shoulders(up = -0.015, fwd = -0.03)
            trunk(V(0.0, 0.6, 0.3), V(0.0, 0.62, -0.2), waist = -14.0, chest = -10.0)
            head(nod = 22.0)
            rest(PAD, listOf(Bone.PELVIS, Bone.CHEST))
            val sh = shoulders()
            val top = V(0.0, sh.y + drop(0.62), sh.z + 0.02)
            val chest = this[Bone.CHEST].at(0.0, 0.13, 0.137).let { V(0.0, it.y + 0.014, it.z) }
            val bar = V(0.0, lerp(top.y, chest.y, d), lerp(top.z, chest.z, d * d))
            hold(bar, 0.62, V(0.0, 0.3, 1.0), V(0.7, -1.0, 0.45))
            barbell(bar)
            for (s in SIDES) plant(s, V(s * 0.3, 0.0, 0.85), 18.0)
            block(Frame.WORLD.moved(V(0.0, PAD - 0.04, -0.12)), 0.29, 0.08, 1.2)
            block(0.0, -0.45, 0.12, PAD - 0.08, 0.12, Tone.METAL)
            block(0.0, 0.3, 0.12, PAD - 0.08, 0.12, Tone.METAL)
            for (s in SIDES) rod(V(s * 0.55, 0.0, top.z - 0.08), V(s * 0.55, top.y + 0.05, top.z - 0.08), 0.03)
        }
    },
    "deadlift" to {
        Motion(Tempo(1.4, 0.5, 1.6, 0.8)) { d ->
            hinge(lerp(0.225, 0.83, d), lerp(0.04, -0.12, phase(d, 0.25, 1.0)), lerp(0.668, 0.857, 1 - (1 - d).pow(2.5)))
            head(nod = -8 * (1 - d))
        }
    },
    "romanian_deadlift" to {
        Motion(Tempo(2.0, 0.4, 1.2, 0.8)) { d ->
            hinge(lerp(0.83, 0.42, d), lerp(-0.12, 0.04, phase(d, 0.0, 0.7)), lerp(0.85, 0.84, d))
        }
    },
    "ohp" to {
        Motion(Tempo(1.2, 0.4, 1.6, 0.8), balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0), pitch = -3 * (1 - d))
            spine(waist = -6.0, chest = 2.0)
            shoulders(up = 0.035 * phase(d, 0.6, 1.0), fwd = 0.01)
            val sh = shoulders()
            val rack = V(0.0, sh.y + 0.03, sh.z + 0.1)
            val top = V(0.0, sh.y + drop(0.54), sh.z + 0.01)
            val bar = V(0.0, lerp(rack.y, top.y, d), lerp(rack.z, top.z, phase(d, 0.3, 0.9)))
            head(nod = 4.0, retract = 12 * phase(d, 0.05, 0.3) * (1 - phase(d, 0.55, 0.85)))
            carry(0.55, bar)
            hold(bar, 0.54, V(0.0, 0.35, 1.0), lerp(V(0.25, -1.0, 0.7), V(1.0, -0.1, 0.1), phase(d, 0.2, 0.9)))
            barbell(bar)
            feet(0.3, 0.1, 8.0)
        }
    },
    "barbell_row" to {
        Motion(Tempo(0.9, 0.3, 1.5, 0.6), balance = true) { d ->
            shoulders(fwd = lerp(0.03, -0.04, d))
            val hip = V(0.0, 0.89, -0.18)
            trunk(hip, hip + V(0.0, 0.33, 0.39), waist = -3.0, chest = 3.0)
            head(nod = -12.0)
            val sh = shoulders()
            val hang = V(0.0, sh.y - drop(0.5), sh.z)
            val touch = this[Bone.CHEST].at(0.0, 0.03, 0.15).let { V(0.0, it.y, it.z) }
            val bar = lerp(hang, touch, d)
            carry(0.5, bar)
            hold(bar, 0.5, V(0.0, -0.3, -1.0), V(0.45, 0.6, -0.6))
            barbell(bar)
            feet(0.3, 0.05, 8.0)
        }
    },
    "barbell_curl" to {
        Motion(Tempo(1.0, 0.3, 1.8, 0.6), balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0), pitch = 2.0)
            spine(waist = -6.0, chest = 3.0)
            shoulders(fwd = -0.01)
            for (s in SIDES) arm(s, raise = 6 + 10 * d, plane = 80.0, elbow = lerp(8.0, 140.0, d), pronate = 0.0, fist = true)
            val mid = (hand(L) + hand(R)) * 0.5
            carry(0.3, mid)
            barbell(mid, plate = 0.14, half = 0.42, sleeve = 0.18)
            feet(0.3, 0.1, 8.0)
        }
    },
)
