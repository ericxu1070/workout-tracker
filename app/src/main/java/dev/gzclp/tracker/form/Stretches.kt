package dev.gzclp.tracker.form

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A stretch eases in, holds, eases out and rests, slowly enough to follow while the player is paused. */
internal fun stretch(balance: Boolean = false, ground: Boolean = false, yaw: Float? = null, build: Poser.(Double) -> Unit) =
    Motion(Tempo(2.5, 2.0, 2.0, 1.0), balance, ground, yaw, build)

internal fun sq(x: Double) = x * x

/** The knee joint's height kneeling on the floor, the front of the knee just touching it. */
internal const val KNEE = 0.056

/** The hips' midpoint over knees [width] apart kneeling at [z], the thighs [lean] degrees back from upright; [kneel] then lands each knee exactly. */
internal fun kneeling(width: Double, z: Double, lean: Double): V {
    val out = width / 2 - Build.HIP
    val r = sqrt(Build.THIGH * Build.THIGH - out * out)
    return V(0.0, KNEE + r * cos(lean * DEG), z - r * sin(lean * DEG))
}

/**
 * Pitches the trunk up from hips at [hip] so the shoulders land [arm] (side on) from the wrists at [wrists], the spine bent
 * as given and bowing toward [pole]. A side bend carries the shoulders over by its own amount; the wrists' x is free.
 */
internal fun Poser.bridge(hip: V, wrists: V, arm: Double, waist: Double = 0.0, chest: Double = 0.0, side: Double = 0.0, pole: V = V.UP) {
    pelvis(V.ZERO)
    spine(waist, chest, side)
    val q = shoulders() - this[Bone.PELVIS].o
    val sh = reach(V(0.0, hip.y, hip.z), V(0.0, wrists.y, wrists.z), sqrt(q.y * q.y + q.z * q.z), arm, pole)
    trunk(hip, V(hip.x + q.x, sh.y, sh.z), waist, chest, side)
}

/** The forward pitch about the hips at [hip] that brings [p], posed with the pelvis upright, to height [y]. */
internal fun pitchTo(hip: V, p: V, y: Double): Double {
    val v = p - hip
    return (acos(((y - hip.y) / sqrt(v.y * v.y + v.z * v.z)).coerceIn(-1.0, 1.0)) - atan2(v.z, v.y)) / DEG
}

/** The [s] palm flat on the floor along [dir] from its shoulder, [arm] from it, the elbow pointing toward [elbow]. */
internal fun Poser.reachFloor(s: Int, dir: V, arm: Double, elbow: V) {
    val sh = shoulder(s)
    val u = dir.across(V.UP).unit()
    val drop = sh.y - 0.022
    val wrist = V(sh.x, 0.022, sh.z) + u * sqrt(max(0.0, arm * arm - drop * drop))
    palm(s, wrist + u * 0.075 - V(0.0, 0.022, 0.0), u, elbow)
}

/** All fours, from the tabletop. */
private val KNEELING: Map<String, () -> Motion> = mapOf(
    "upper_back_cat" to {
        stretch { d ->
            shoulders(fwd = 0.035 * d)
            bridge(kneeling(0.2, 0.0, 0.0), V(0.0, 0.022, 0.5), 0.54, waist = 24 * d, chest = lerp(-4.0, 28.0, d))
            head(nod = lerp(-5.0, 45.0, d))
            for (s in SIDES) {
                kneel(s, V(s * 0.1, KNEE, 0.0))
                palm(s, V(s * 0.19, 0.0, 0.575), V.FWD, V(s * 0.4, 0.2, -1.0))
            }
        }
    },
    "upper_back_thread_needle" to {
        // The left arm slides under the chest to the right, palm turning up, until the left shoulder and temple rest on the mat.
        stretch { d ->
            val hip = kneeling(0.2, 0.0, 0.0)
            val twist = -70 * d
            pelvis(hip)
            spine(waist = 6 * d, chest = 4 * d, twist = twist)
            pelvis(hip, pitch = pitchTo(hip, shoulder(L), lerp(0.562, 0.065, d)))
            spine(waist = 6 * d, chest = 4 * d, twist = twist)
            head(nod = 10 * d, tilt = -35 * d)
            for (s in SIDES) kneel(s, V(s * 0.1, KNEE, 0.0))
            // The hand slides along the floor, rolling about its own length onto the little-finger edge until the palm faces up.
            val fingers = lerp(V.FWD, V(-1.0, 0.0, 0.0), d).unit()
            val roll = 180 * d * DEG
            val n = V.UP.turn(fingers, -180 * d)
            val half = 0.044 * sin(roll)
            val face = lerp(0.022, 0.012, d) * cos(roll)
            palm(L, lerp(V(0.19, 0.0, 0.575), V(-0.45, 0.0, 0.5), d) + V.UP * (sqrt(half * half + face * face) - n.y * 0.022), fingers, V(0.3, 1.0, -0.5), n)
            palm(R, V(-0.19, 0.0, 0.575), V.FWD, V(-0.6, 0.6, -0.4))
        }
    },
    "lats_childs_pose" to {
        // Knees wide, big toes together: the hips sink back onto the heels, then the hands walk right to lengthen the left side.
        stretch { d ->
            val sink = phase(d, 0.0, 0.7)
            val hip = kneeling(0.48, 0.0, 64 * sink)
            bridge(hip, V(0.0, 0.022, 0.62), 0.52, waist = 8 * sink, chest = 8 * sink, side = -15 * phase(d, 0.4, 1.0))
            head(nod = -12 * sink)
            val walk = V.FWD.turn(V.UP, -25 * phase(d, 0.4, 1.0))
            for (s in SIDES) {
                kneel(s, V(s * 0.24, KNEE, 0.0), V(s * 0.325, 0.0, 0.946))
                reachFloor(s, walk, 0.53, V(s * 0.5, 0.6, -0.4))
            }
        }
    },
    "ankles_plantar_sit" to {
        // From tall kneeling, sit back onto the heels with the tops of the feet flat; the hands ride on the thighs.
        stretch { d ->
            pelvis(kneeling(0.2, 0.0, 66 * d), pitch = 5 * d)
            spine(waist = -6.0, chest = 3.0)
            for (s in SIDES) {
                kneel(s, V(s * 0.1, KNEE, 0.0))
                val t = this[Bone.thigh(s)]
                palm(s, t.at(0.0, lerp(0.08, 0.19, d), lerp(0.09, 0.07, d)), t.y, V(s * 0.6, 0.0, -1.0), t.z)
            }
        }
    },
    "quads_kneeling_lean" to {
        // Knees to head in one straight line, leaning back from the knees; the arms reach forward to counterbalance.
        stretch { d ->
            val lean = 35 * d
            pelvis(kneeling(0.2, 0.0, lean), pitch = -lean)
            spine(waist = -4.0, chest = 2.0)
            head(nod = 20 * d)
            for (s in SIDES) {
                kneel(s, V(s * 0.1, KNEE, 0.0))
                arm(s, raise = lerp(5.0, 90.0 - lean, d), plane = 85.0, elbow = 8.0)
            }
        }
    },
)

/** One knee down and one foot forward, or a long split stance. */
private val LUNGES: Map<String, () -> Motion> = mapOf(
    "hips_pigeon" to {
        // Left shin across the front of the mat, right leg long behind: the hips stay square while the chest folds over the shin.
        stretch { d ->
            val fold = phase(d, 0.15, 1.0)
            val h = 0.13
            bridge(V(0.0, h, 0.0), V(0.0, 0.022, lerp(0.5, 0.8, fold)), lerp(0.545, 0.5, fold), waist = 14 * fold, chest = 12 * fold)
            head(nod = 25 * fold)
            // The front knee rests out to the left and the shin crosses toward the right hip, the foot on its outer edge.
            val hipL = hip(L)
            val knee = V(0.24, 0.055, hipL.z + sqrt(sq(Build.THIGH) - sq(0.24 - hipL.x) - sq(h - 0.055)))
            val across = V(-cos(20 * DEG), 0.0, -sin(20 * DEG))
            val heel = V(knee.x, 0.047, knee.z) + across * sqrt(sq(Build.SHIN) - sq(0.008))
            val toes = V(across.z, 0.0, -across.x)
            plant(L, heel - ankle(V.ZERO, 0.0, toes, -across), ground = -across, ahead = toes, knee = knee - hipL)
            val hipR = hip(R)
            kneel(R, V(hipR.x - 0.02, KNEE, hipR.z - sqrt(sq(Build.THIGH) - sq(0.02) - sq(h - KNEE))))
            for (s in SIDES) palm(s, V(s * 0.2, 0.0, lerp(0.575, 0.875, fold)), V.FWD, V(s * 0.6, 0.3, -1.0))
        }
    },
    "hips_lizard" to {
        // Low lunge with the left foot outside both hands: the hips sink toward the floor and the front knee opens out.
        stretch { d ->
            bridge(kneeling(0.2, -0.3, -lerp(30.0, 63.0, d)), V(0.0, 0.022, 0.48), 0.53, waist = 6.0, chest = 4.0)
            head(nod = -10.0)
            kneel(R, V(-0.1, KNEE, -0.3))
            plant(L, V(0.3, 0.0, 0.56), out = 20.0)
            palm(L, V(0.06, 0.0, 0.555), V.FWD, V(0.3, 0.3, -1.0))
            palm(R, V(-0.22, 0.0, 0.555), V.FWD, V(-0.3, 0.3, -1.0))
        }
    },
    "ankles_kneeling_dorsi" to {
        // Half kneeling, left foot flat in front: the knee drives forward past the toes while the heel stays down.
        stretch { d ->
            val ball = V(0.1, 0.0, 0.57)
            val lean = 32 * d * DEG
            val front = ankle(ball, 0.0) + V(0.0, cos(lean), sin(lean)) * Build.SHIN
            val back = V(-0.1, KNEE, 0.0)
            val r = sqrt(sq(Build.THIGH) - sq(0.01))
            pelvis(reach(V(0.0, back.y, back.z), V(0.0, front.y, front.z), r, r, V.UP), pitch = 10 * d)
            spine(waist = -4.0, chest = 2.0)
            head(nod = 12 * d)
            kneel(R, back)
            plant(L, ball, knee = front - hip(L))
            val t = this[Bone.THIGH_L]
            palm(L, t.at(0.0, 0.36, 0.06), t.y, V(0.6, 0.0, -1.0), t.z)
            palm(R, t.at(0.0, 0.18, 0.085), t.y, V(-0.6, 0.0, -1.0), t.z)
        }
    },
    "quads_couch" to {
        // Left knee down at the foot of a couch, the shin up its front and the foot on the seat: the hips come forward under a tall chest.
        stretch { d ->
            val back = lerp(28.0, 36.0, d)
            val tilt = lerp(16.0, -6.0, d)
            pelvis(V(0.0, KNEE + Build.THIGH * cos(back * DEG), Build.THIGH * sin(back * DEG)), pitch = tilt)
            spine(waist = lerp(6.0, 4.0, d), chest = lerp(6.0, 2.0, d))
            head(nod = lerp(8.0, 0.0, d))
            leg(L, raise = tilt - back, knee = 180 - back)
            plant(R, V(-0.12, 0.0, 0.8), out = 5.0)
            val t = this[Bone.THIGH_R]
            palm(R, t.at(0.0, 0.32, 0.062), t.y, V(-0.6, 0.0, -1.0), t.z)
            palm(L, t.at(0.0, 0.14, 0.088), t.y, V(0.6, 0.0, -1.0), t.z)
            block(0.0, -0.305, 0.8, lowest(listOf(Bone.FOOT_L, Bone.TOES_L)), 0.5)
        }
    },
    "calves_wall_drive" to {
        // Hands on a wall, left leg long behind with the heel down: the hips drive toward the wall and the back ankle bends.
        stretch(yaw = 65f) { d ->
            val lean = lerp(14.0, 24.0, d)
            val ball = V(0.1, 0.0, -0.55)
            pelvis(ankle(ball, 0.0) + V(0.0, cos(lean * DEG), sin(lean * DEG)) * 0.859 - V(Build.HIP, 0.0, 0.0), pitch = 0.7 * lean)
            spine(waist = 2.0, chest = 2.0)
            head(nod = 5.0)
            plant(L, ball)
            plant(R, V(-0.12, 0.0, -0.15), out = 5.0)
            for (s in SIDES) palm(s, V(s * 0.2, 1.25, 0.1), V.UP, V(s * 1.0, -1.0, -0.5), V.BACK)
            block(0.0, 0.15, 0.6, 1.8, 0.1)
        }
    },
    "calves_heel_drop" to {
        // On the left foot at the edge of a step, a hand on the rail: the heel lowers below the step.
        stretch(balance = true) { d ->
            val rise = -26 * d
            pelvis(V(0.0, 0.07 + STAND + ankle(V.ZERO, rise).y - Build.ANKLE, 0.0))
            spine(waist = -4.0, chest = 2.0)
            plant(L, V(0.1, 0.1, 0.1), 6.0, rise = rise, edge = true)
            leg(R, raise = 10.0, knee = 75.0, ankle = -20.0)
            grip(L, V(0.42, 1.1, 0.15), V(0.4, -1.0, -0.6), V(-0.5, 0.0, 1.0))
            block(0.0, 0.23, 0.7, 0.1, 0.3)
            rod(V(0.42, 0.0, 0.15), V(0.42, 1.18, 0.15), 0.018)
        }
    },
)

/** Standing tall, on both feet or one. */
private val STANDING: Map<String, () -> Motion> = mapOf(
    "hamstrings_standing_fold" to {
        // Soft knees and a hinge from the hips, then the spine lets go and the torso hangs heavy, arms dangling.
        stretch(balance = true) { d ->
            val hinge = 72 * phase(d, 0.0, 0.8)
            val hang = phase(d, 0.2, 1.0)
            pelvis(V(0.0, STAND - 0.05 * d, 0.0), pitch = hinge)
            spine(waist = 26 * hang, chest = 18 * hang)
            head(nod = 25 * hang)
            feet(width = 0.24, out = 6.0)
            // Raised by the trunk's own pitch, the arms hang straight down.
            for (s in SIDES) arm(s, raise = hinge + 44 * hang, plane = 92.0, elbow = 12 * d)
        }
    },
    "quads_standing" to {
        // On the right leg with the left ankle in hand: the knee draws back beside the other as the tailbone tucks.
        stretch(balance = true) { d ->
            val tuck = 6 * d
            pelvis(V(0.0, STAND, 0.0), pitch = -tuck)
            spine(waist = 4 * d, chest = -2.0)
            shoulders(up = -0.015, side = L)
            plant(R, V(-0.11, 0.0, 0.1), 6.0)
            leg(L, raise = -8 * d - tuck, knee = lerp(150.0, 153.0, d), ankle = -55.0)
            val foot = this[Bone.FOOT_L]
            grip(L, foot.at(0.0, 0.0, 0.03), V(0.2, -1.0, 0.3), -foot.z)
            arm(R, raise = 45.0, plane = 30.0, elbow = 10.0)
        }
    },
    "upper_back_eagle" to {
        // The right elbow crosses over the left and rests in its crook, so the right forearm rises just behind the left one and
        // the backs of the hands meet; the elbows lift and press away.
        stretch(balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0))
            spine(waist = 3 * d, chest = 12 * d)
            head(nod = 14 * d)
            shoulders(fwd = 0.04 + 0.035 * d)
            feet()
            val sh = shoulder(L)
            val y = sh.y - 0.06 + 0.08 * d
            val elbow = V(-0.02, y, sh.z + sqrt(sq(Build.UPPER_ARM) - sq(-0.02 - sh.x) - sq(y - sh.y)))
            val wrist = elbow + V(0.02, sqrt(sq(Build.FOREARM) - sq(0.02) - sq(0.025)), 0.025)
            val up = (wrist - elbow).across(V(0.0, 0.0, 1.0)).unit()
            val back = V(0.0, 0.0, -1.0) // the back of the left hand faces the body
            palm(L, wrist + up * 0.075 - back * 0.022, up, elbow - sh, back)
            palm(R, wrist + V(0.0, 0.1, -0.035) + up * 0.075 + back * 0.022, up, V(0.04, y + 0.08, elbow.z - 0.05) - shoulder(R), -back)
        }
    },
    "chest_doorway" to {
        // Left arm at 90/90 with the forearm upright on the door frame: the chest steps through and turns a little away.
        stretch(yaw = 125f) { d ->
            pelvis(V(0.0, STAND - 0.02, 0.02 + 0.12 * d), pitch = 4 * d)
            spine(waist = -3 * d, chest = -4 * d, twist = -10 * d)
            head(turn = -8 * d)
            // The forearm stays put on the frame, so the body slides sideways to keep the level upper arm its length.
            val post = V(0.455, 0.0, 0.025)
            val sh = shoulder(L)
            nudge(V(post.x - sqrt(sq(Build.UPPER_ARM) - sq(sh.z - post.z)) - sh.x, 0.0, 0.0))
            plant(L, V(0.1, 0.0, 0.0), 8.0, rise = 14 * d)
            plant(R, V(-0.12, 0.0, 0.34), 6.0)
            val elbow = V(post.x, sh.y, post.z)
            val wrist = elbow + V(0.0, sqrt(sq(Build.FOREARM) - sq(0.013)), 0.013)
            palm(L, wrist + V(0.0, 0.075, 0.022), V.UP, elbow - shoulder(L), V.BACK)
            for (s in SIDES) block(Frame.WORLD.moved(V(s * 0.47, 1.03, 0.12)), 0.1, 2.06, 0.12)
            block(Frame.WORLD.moved(V(0.0, 2.1, 0.12)), 1.04, 0.08, 0.12)
        }
    },
    "chest_behind_back_clasp" to {
        // Fingers laced behind the back: the arms straighten and lift away while the chest rises and the shoulder blades draw in.
        stretch(balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0))
            spine(waist = -4 * d, chest = -10 * d)
            head(nod = -8 * d)
            shoulders(fwd = -0.03 * d)
            feet()
            for (s in SIDES) grip(s, V(s * 0.025, lerp(0.88, 0.97, d), lerp(-0.14, -0.36, d)), V(s * 1.0, 0.0, -0.4), V(-s * 1.0, 0.0, 0.0))
        }
    },
    "shoulders_cross_body" to {
        // The left arm comes straight across the chest and the right hand draws it in just above the elbow; the shoulder stays down.
        stretch(balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0))
            spine()
            head(turn = 10 * d)
            feet()
            shoulders(fwd = 0.025 * d, side = L)
            arm(L, raise = 82.0, plane = lerp(135.0, 155.0, d), elbow = 6.0)
            val upper = this[Bone.ARM_L]
            val n = V.FWD.across(upper.y).unit()
            palm(R, upper.at(0.0, 0.24, 0.0) + n * 0.045, V.UP.across(n), V(-1.0, -1.0, 0.0), n)
        }
    },
    "shoulders_overhead_triceps" to {
        // The left hand reaches down the middle of the back; the right hand on the elbow guides it back behind the head.
        stretch(balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0))
            spine(chest = -3 * d)
            head(nod = 6 * d)
            feet()
            // The palm lies flat on the upper back (its surface 0.1 behind the spine) and slides down it, so the elbow rises beside the head.
            val chest = this[Bone.CHEST]
            palm(L, chest.at(0.0, lerp(0.22, 0.14, d), -0.1), -chest.y, chest.y, -chest.z)
            palm(R, this[Bone.ARM_L].at(0.0, Build.UPPER_ARM + 0.04, 0.0), V.LEFT, V(-1.0, 0.0, 0.6))
        }
    },
    "lats_side_bend" to {
        // Feet hip width, hands clasped overhead: the ribs arc over to the right, lengthening the whole left side.
        stretch(balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0))
            spine(chest = -2.0, side = -30 * d)
            head(tilt = -6 * d)
            shoulders(up = 0.02)
            feet(width = 0.3)
            val chest = this[Bone.CHEST]
            for (s in SIDES) grip(s, chest.at(s * 0.026, 0.8, -0.03), chest.x * s.toDouble(), chest.x * -s.toDouble())
        }
    },
)

/** Sits on the floor, the pelvis tipped [pitch] forward and the spine bent and turned as given; place the legs after it. */
internal fun Poser.sit(pitch: Double, waist: Double = 0.0, chest: Double = 0.0, twist: Double = 0.0) {
    pelvis(V(0.0, 0.2, 0.0), pitch = pitch)
    spine(waist, chest, twist = twist)
    rest(0.0, listOf(Bone.PELVIS))
}

/** The [s] leg straight out ahead of a pelvis tipped [pitch] forward, the knee soft and the foot [ankle] degrees up from square, lowered until it rests on the floor. */
internal fun Poser.outstretched(s: Int, pitch: Double, ankle: Double) {
    var raise = 88.0 + pitch
    repeat(2) {
        leg(s, raise, knee = 4.0, ankle = ankle)
        raise -= asin((lowest(listOf(Bone.shin(s), Bone.foot(s))) / 0.9).coerceIn(-1.0, 1.0)) / DEG
    }
    leg(s, raise, knee = 4.0, ankle = ankle)
}

/** Sitting or lying on the floor. */
private val FLOOR: Map<String, () -> Motion> = mapOf(
    "hamstrings_downward_dog" to {
        // The hips drive up and back from a soft-kneed dog as the heels sink toward the mat; the arms and spine make one long line.
        stretch { d ->
            // The hips ride a circle about the wrists just inside full reach, so the shoulders sit only a little below the line.
            val y = lerp(0.72, 0.77, d)
            bridge(V(0.0, y, 0.76 - sqrt(sq(lerp(1.047, 1.053, d)) - sq(y - 0.022))), V(0.0, 0.022, 0.76), 0.545, chest = -5.0, pole = V.DOWN)
            for (s in SIDES) {
                palm(s, V(s * 0.2, 0.0, 0.835), V.FWD, V(s * 0.6, 0.0, -1.0))
                plant(s, V(s * 0.1, 0.0, -0.35), rise = lerp(20.0, 3.0, d))
            }
        }
    },
    "hamstrings_seated_single" to {
        // The left leg lies straight with its foot flexed and the right sole against its inner thigh; the hands slide down it as the hips fold.
        stretch { d ->
            val pitch = lerp(8.0, 32.0, d)
            sit(pitch, waist = 14 * d, chest = lerp(4.0, 14.0, d))
            head(nod = lerp(4.0, 18.0, d))
            outstretched(L, pitch, 12.0)
            plant(R, V(0.0, 0.05, 0.28), ground = V(-1.0, 0.0, 0.0), knee = V(-1.0, 0.05, 0.2))
            val hip = hip(L)
            val leg = this[Bone.FOOT_L].o - hip
            val t = lerp(0.45, 0.92, d)
            for (s in SIDES) {
                val n = V(s * 0.45, 0.89, 0.0).across(leg.unit()).unit()
                palm(s, hip + leg * t + n * lerp(0.09, 0.035, t), leg, V(s * 1.0, -0.3, 0.0), n)
            }
        }
    },
    "hips_butterfly" to {
        // Soles together and knees open; the hands hold the feet as the trunk hinges forward over them.
        stretch { d ->
            sit(lerp(8.0, 30.0, d), waist = 14 * d, chest = lerp(4.0, 14.0, d))
            head(nod = 14 * d)
            for (s in SIDES) {
                plant(s, V(s * 0.012, 0.05, 0.34), ground = V(s * 1.0, 0.0, 0.0), knee = V(s * 1.0, 0.3, 0.0))
                grip(s, V(s * 0.07, 0.07, 0.38), V(s * 1.0, -0.2, -0.3), V(-s * 1.0, -0.5, 0.0))
            }
        }
    },
    "spine_seated_twist" to {
        // Sitting tall, the left foot steps over the straight right leg and the ribcage turns toward the raised knee, which the right arm hugs.
        stretch { d ->
            sit(4.0, chest = -2.0, twist = lerp(10.0, 55.0, d))
            head(turn = lerp(10.0, 45.0, d))
            outstretched(R, 4.0, -25.0)
            plant(L, V(-0.23, 0.0, 0.55), knee = V(-0.25, 0.8, 0.5))
            grip(R, this[Bone.SHIN_L].o + V(0.08, -0.02, 0.0), V(-0.3, -0.5, 1.0), V(-1.0, 0.0, 0.0))
            grip(L, V(0.16, 0.045, -0.24), V(0.5, 0.0, -1.0), V(-1.0, 0.0, 0.3))
        }
    },
    "spine_supine_twist" to {
        // On the back with the knees bent, the pelvis rolls them down to the right while the chest stays flat, the arms wide and the head turned left.
        stretch { d ->
            val fall = lerp(8.0, 62.0, d)
            // The rolled pelvis reaches deeper than the flat upper back, so the body tips head down about the hips until both
            // rest on the floor (the chest drops about 6.5 mm a degree).
            var tip = 0.0
            fun lie() {
                pelvis(Frame.WORLD.moved(V(0.0, 0.3, 0.0)).bend(-90.0 - tip).twist(-fall))
                spine(twist = fall)
            }
            repeat(2) { lie(); tip += (lowest(listOf(Bone.CHEST)) - lowest(listOf(Bone.PELVIS))) / 0.0065 }
            lie()
            head(turn = 40 * d)
            rest(0.0)
            for (s in SIDES) {
                // The top knee crosses further to rest on the bottom one; each thigh turns back as it swings so the shins stay level.
                val k = (if (s == L) 36.0 else 26.0) * d
                leg(s, raise = 92.0, plane = 90.0 + s * k, twist = -s * k, knee = 95.0, ankle = -20.0)
                val sh = shoulder(s)
                val wrist = V(sh.x + s * sqrt(sq(0.545) - sq(sh.y - 0.022)), 0.022, sh.z)
                palm(s, wrist + V(s * 0.075, -0.022, 0.0), V(s * 1.0, 0.0, 0.0), V(0.0, -0.2, 1.0))
            }
        }
    },
    "spine_cobra" to {
        // Face down, the hands press the floor away and the chest rises on bent elbows while the hips stay down.
        stretch { d ->
            pelvis(V(0.0, 0.3, 0.0), pitch = 90.0)
            spine(waist = lerp(-8.0, -28.0, d), chest = -18 * d)
            head(nod = lerp(4.0, -10.0, d))
            rest(0.0, listOf(Bone.PELVIS, Bone.WAIST, Bone.CHEST, Bone.HEAD))
            for (s in SIDES) {
                leg(s, raise = 2.0, ankle = -70.0)
                palm(s, V(s * 0.25, 0.0, 0.5), V.FWD, V(s * 0.3, 0.3, -1.0))
            }
        }
    },
    "shoulders_sleeper" to {
        // On the left side, the bottom arm reaches forward along the floor with the elbow bent; the top hand eases its forearm down toward the hips.
        stretch { d ->
            pelvis(V(0.0, 0.3, 0.0), roll = 90.0)
            spine(chest = 6.0, side = -12.0) // the shoulder stands wider than the hip, so the ribs bow up off the floor
            val q = lerp(10.0, 58.0, d) * DEG
            val fore = V(-sin(q), cos(q), 0.0)
            val back = V(cos(q), sin(q), 0.0) // the back of that forearm
            fun underneath() {
                val wrist = shoulder(L) + V(0.0, 0.0, Build.UPPER_ARM) + fore * Build.FOREARM
                palm(L, wrist + fore * 0.075 - back * 0.022, fore, V.FWD, back)
                leg(L, raise = 50.0, knee = 75.0)
            }
            underneath()
            rest(0.0, Bone.TRUNK + listOf(Bone.ARM_L, Bone.FOREARM_L, Bone.THIGH_L, Bone.SHIN_L, Bone.FOOT_L))
            underneath()
            palm(R, this[Bone.FOREARM_L].at(0.0, 0.2, 0.0) + back * 0.025, V.FWD, V(0.0, 1.0, 0.5), back)
            leg(R, raise = 50.0, plane = 104.0, knee = 75.0)
            val head = this[Bone.HEAD].at(0.0, 0.05, 0.0)
            block(head.x, head.z, 0.3, lowest(listOf(Bone.HEAD)), 0.4)
        }
    },
)

internal val STRETCHES: Map<String, () -> Motion> = KNEELING + LUNGES + STANDING + FLOOR + MOBILITY
