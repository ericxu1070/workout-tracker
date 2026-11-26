package dev.gzclp.tracker.form

import kotlin.math.asin
import kotlin.math.sqrt

/** The moves the famous routines need: Limber 11, Simple 6, Starting to Stretch, Sun salutation A, and legs up the wall. */
internal val MOBILITY: Map<String, () -> Motion> = mapOf(
    "hamstrings_legs_up_wall" to {
        // On the back with the hips close to a wall: the feet slide up it from bent knees until the legs rest straight, the arms out and palms up.
        stretch { d ->
            pelvis(V(0.0, 0.3, 0.0), pitch = -90.0)
            spine(waist = -4.0, chest = 2.0)
            head(nod = 8.0)
            rest(0.0)
            for (s in SIDES) {
                leg(s, raise = lerp(115.0, 86.0, d), plane = 91.0, knee = lerp(59.0, 3.0, d), ankle = lerp(-50.0, -5.0, d))
                reachFloor(s, V(s * 0.8, 0.0, -0.6), 0.53, V(0.0, -0.2, 1.0))
            }
            block(Frame.WORLD.moved(V(0.0, 0.7, 0.18)), 1.2, 1.4, 0.1)
        }
    },
    "shoulders_backbend" to {
        // Standing in one straight line, glutes tight: straight arms go overhead, then on back past the ears without the ribs flaring.
        stretch(balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0), pitch = -2 * d)
            spine(waist = 2.0, chest = -4 * d)
            head(nod = 4 * d)
            shoulders(up = 0.025)
            feet(width = 0.22)
            for (s in SIDES) arm(s, raise = lerp(160.0, 198.0, d), plane = 82.0, elbow = 4.0, pronate = 20.0)
        }
    },
    "spine_standing_backbend" to {
        // Fingertips on the shoulder blades and glutes tight, the knees soften and the upper back arches as the body leans back.
        stretch(balance = true) { d ->
            pelvis(V(0.0, STAND - 0.04 * d, 0.0), pitch = -6 * d)
            spine(waist = -8 * d, chest = -26 * d)
            head(nod = -18 * d)
            feet(width = 0.26)
            val chest = this[Bone.CHEST]
            for (s in SIDES) palm(s, chest.at(s * 0.07, 0.2, -0.1), -chest.y, chest.y + chest.x * (s * 0.4), -chest.z)
        }
    },
    "shoulders_rear_hand_clasp" to {
        // A towel held overhead in the right hand and behind the back in the left: the top hand draws the bottom one up the spine.
        stretch(balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0))
            spine(chest = -2.0)
            head(nod = 4.0)
            feet()
            val chest = this[Bone.CHEST]
            val low = chest.at(0.0, lerp(-0.04, 0.06, d), -0.17)
            val high = chest.at(-0.02, lerp(0.42, 0.5, d), -0.13)
            grip(L, low, V(1.0, -0.4, -0.6), V.BACK)
            grip(R, high, V(-0.3, 1.0, 0.4), V.FWD)
            rod(low - V.UP * 0.08, high + V.UP * 0.08, 0.014, Tone.PAD)
        }
    },
    "shoulders_wall_wrist_biceps" to {
        // Side on to a wall, the left palm flat on it at shoulder height with the fingers back and the arm straight; the chest turns away.
        stretch(balance = true, yaw = -40f) { d ->
            fun trunk(t: Double) {
                pelvis(V(0.0, STAND, 0.0), yaw = -12 * t)
                spine(twist = -28 * t)
                head(turn = -30 * t)
            }
            trunk(1.0)
            val wrist = shoulder(L) + V(0.545, 0.0, -0.06)
            trunk(d)
            feet()
            palm(L, wrist + V(0.022, 0.0, -0.075), V.BACK, V(0.0, -1.0, 0.0), V(-1.0, 0.0, 0.0))
            block(Frame.WORLD.moved(V(wrist.x + 0.072, 1.0, 0.0)), 0.1, 2.0, 1.2)
        }
    },
    "hamstrings_one_leg_pike" to {
        // The left heel up on a chair with the leg straight, standing on the right: the trunk leans forward with a flat back.
        stretch { d ->
            val pitch = lerp(8.0, 42.0, d)
            pelvis(V(0.0, STAND, 0.0), pitch = pitch)
            spine(waist = 4 * d, chest = 2.0)
            head(nod = 10 * d)
            plant(R, V(-0.1, 0.0, 0.1), 6.0)
            var raise = 70.0 + pitch
            repeat(3) {
                leg(L, raise, knee = 3.0, ankle = 15.0)
                raise -= asin(((lowest(listOf(Bone.SHIN_L, Bone.FOOT_L)) - 0.45) / 0.86).coerceIn(-1.0, 1.0)) / DEG
            }
            leg(L, raise, knee = 3.0, ankle = 15.0)
            val heel = this[Bone.FOOT_L].o
            block(heel.x, heel.z, 0.4, 0.45, 0.4)
            val hip = hip(L)
            val leg = this[Bone.FOOT_L].o - hip
            for (s in SIDES) {
                val n = V(s * 0.5, 0.87, 0.0).across(leg.unit()).unit()
                fun on(t: Double) = hip + leg * t + n * lerp(0.085, 0.05, t)
                // The hands slide as far down the leg as straight arms reach.
                val t = (90 downTo 10).map { it / 100.0 }.firstOrNull { (on(it) - leg.unit() * 0.075 - shoulder(s)).len <= 0.53 } ?: 0.1
                palm(s, on(t), leg, V(s * 1.0, -0.2, -0.3), n)
            }
        }
    },
    "hips_kneeling_lunge" to {
        // Right knee down and the left foot well ahead between the hands: the hips sink forward toward the front heel, the back long.
        stretch { d ->
            val foot = 0.7
            bridge(kneeling(0.2, -0.3, -lerp(28.0, 58.0, d)), V(0.0, 0.022, foot - 0.02), 0.53, waist = 4.0, chest = 2.0)
            head(nod = -6.0)
            kneel(R, V(-0.1, KNEE, -0.3))
            plant(L, V(0.1, 0.0, foot + 0.05), out = 4.0)
            palm(L, V(0.27, 0.0, foot), V.FWD, V(0.4, 0.3, -1.0))
            palm(R, V(-0.1, 0.0, foot), V.FWD, V(-0.4, 0.3, -1.0))
        }
    },
    "hamstrings_pancake" to {
        // Sitting in a wide straddle with the knees up: the hips fold forward with a flat back as the hands walk out along the floor.
        stretch { d ->
            val pitch = lerp(24.0, 46.0, d)
            sit(pitch, waist = 6 * d, chest = 4 * d)
            head(nod = lerp(0.0, 12.0, d))
            for (s in SIDES) {
                var raise = 88.0 + pitch
                repeat(3) {
                    leg(s, raise, plane = 42.0, knee = 3.0, twist = 12.0, ankle = 8.0)
                    raise -= asin((lowest(listOf(Bone.shin(s), Bone.foot(s))) / 0.9).coerceIn(-1.0, 1.0)) / DEG
                }
                leg(s, raise, plane = 42.0, knee = 3.0, twist = 12.0, ankle = 8.0)
                reachFloor(s, V(s * 0.25, 0.0, 1.0), 0.53, V(s * 0.6, 0.3, -1.0))
            }
        }
    },
)
