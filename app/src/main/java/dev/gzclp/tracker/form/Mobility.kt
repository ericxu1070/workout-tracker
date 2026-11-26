package dev.gzclp.tracker.form

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
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
            var pitch = lerp(24.0, 46.0, d)
            sit(pitch, waist = 6 * d, chest = 4 * d)
            // A mesh's fuller seat lifts the hips off the floor further than the pieces' pelvis, so it folds on until the
            // shoulders are low enough for straight arms to reach the floor.
            if (Figure.on) repeat(4) {
                val over = shoulders().y - 0.022 - (Build.UPPER_ARM + Build.FOREARM - 0.005)
                if (over > 0) { pitch += over / ((shoulders().z - this[Bone.PELVIS].o.z) * DEG); sit(pitch, waist = 6 * d, chest = 4 * d) }
            }
            head(nod = lerp(0.0, 12.0, d))
            for (s in SIDES) {
                lower(s, 88.0 + pitch, 3) { leg(s, it, plane = 42.0, knee = 3.0, twist = 12.0, ankle = 8.0) }
                reachFloor(s, V(s * 0.25, 0.0, 1.0), 0.53, V(s * 0.6, 0.3, -1.0))
            }
        }
    },
    "spine_sun_salutation_a" to { sunSalutationA() },
    "upper_back_foam_roll" to {
        // On the back over a roller across the shoulder blades, hands behind the head and hips up: the legs roll the upper back over it.
        Motion(ROLLING) { d ->
            val len = trunkLength(chest = -6.0)
            val sh = V(0.0, 0.25, lerp(-0.24, -0.05, d))
            trunk(sh + V(0.0, 0.07, sqrt(len * len - 0.0049)), sh, chest = -6.0)
            rest(2 * ROLLER, listOf(Bone.CHEST, Bone.WAIST))
            head(nod = 22.0)
            val h = this[Bone.HEAD]
            for (s in SIDES) palm(s, h.at(s * 0.05, 0.1, -0.1), h.x * -s.toDouble() + h.y * 0.4, h.x * s.toDouble() + h.z * 0.6, -h.z)
            for (s in SIDES) plant(s, V(s * 0.13, 0.0, 0.78), 6.0)
            roller(V(0.0, ROLLER, 0.0), V.LEFT)
        }
    },
    "shoulders_foam_roll_rear" to {
        // On the left side with the roller in the armpit and the body turned back a little, the bottom arm long overhead:
        // the top hand and foot push the body in short passes along the back of the shoulder.
        var found: Double? = null
        var turn = 0.0
        Motion(ROLLING, yaw = 20f) { d ->
            // The bottom arm reaches long along the floor from wherever the shoulder is; it goes in with the trunk so a mesh's armpit sits where it will.
            fun reach() { val bottom = shoulder(L); grip(L, bottom + V(sqrt(sq(Build.ARM - 0.01) - sq(bottom.y - 0.05)), 0.05 - bottom.y, 0.0), V.UP, V.FWD) }
            fun trunk(roll: Double, slide: Double) {
                pelvis(Frame.WORLD.moved(V(slide, 0.3, 0.0)).tilt(roll).twist(-20.0))
                spine(side = -6.0, twist = turn)
                head(tilt = 20.0)
                reach()
            }
            fun seat(roll: Double, slide: Double): V {
                trunk(roll, 0.0)
                val lift = 2 * ROLLER - lowest(listOf(Bone.CHEST))
                val pit = shoulder(L) + V(-0.07, 0.0, 0.0)
                trunk(roll, slide)
                nudge(V(0.0, lift, 0.0))
                return pit
            }
            val spot = V(0.42, 0.0, 0.2)
            val fingers = V(1.0, 0.0, 0.3)
            // The trunk tips up from the floor just enough that the hips stay down while the armpit rides on the roller. That
            // roll is the same at every d, so it is found once (at rest, breath held), not on every frame.
            val roll = found ?: run {
                var lo = 60.0
                var hi = 90.0
                repeat(16) {
                    val roll = (lo + hi) / 2
                    trunk(roll, 0.0)
                    rest(2 * ROLLER, listOf(Bone.CHEST))
                    if (lowest(listOf(Bone.PELVIS, Bone.WAIST)) > 0.005) lo = roll else hi = roll
                }
                (lo + hi) / 2
            }.also { roll ->
                if (found == null && Figure.on) {
                    // A mesh's armpit holds the body higher on the roller than the pieces' chest, too high for the top hand to
                    // reach its spot: the chest turns back onto the roller until the hand reaches it at both ends of the pass.
                    val wrist = spot + V(0.0, 0.022, 0.0) - fingers.unit() * 0.075
                    var lo = -60.0
                    var hi = 0.0
                    repeat(16) {
                        turn = (lo + hi) / 2
                        val far = listOf(-0.045, 0.045).maxOf { seat(roll, it); (wrist - shoulder(R)).len }
                        if (far > Build.UPPER_ARM + Build.FOREARM - 0.002) hi = turn else lo = turn
                    }
                    turn = lo
                }
                found = roll
            }
            val pit = seat(roll, lerp(-0.045, 0.045, d))
            reach()
            leg(L, raise = 8.0, knee = 8.0)
            palm(R, spot, fingers, V(0.3, 1.0, 0.3))
            plant(R, V(-0.36, 0.0, 0.3), 30.0, knee = V(0.0, 0.6, 1.0))
            roller(V(pit.x, ROLLER, pit.z), V.FWD)
        }
    },
    "shoulders_capsule" to {
        // On the left side, the bottom upper arm 45 degrees from the body with the elbow bent: the top hand presses its hand toward the floor.
        stretch { d -> sleeper(lerp(10.0, 62.0, d), 45.0) }
    },
    "chest_band_pec" to {
        // A band hung from the top of a rack, held in the left hand with the arm 45 degrees above level: the chest leans forward through it.
        stretch(yaw = 60f) { d ->
            pelvis(V(0.0, STAND - 0.02 - 0.02 * d, 0.14 * d), pitch = 14 * d)
            spine(waist = -2 * d, chest = -8 * d, twist = -16 * d)
            head(turn = -10 * d)
            plant(L, V(0.1, 0.0, -0.02), 8.0, rise = 12 * d)
            plant(R, V(-0.12, 0.0, 0.34), 6.0)
            arm(R, raise = 8.0, elbow = 12.0)
            // The band pulls the straight arm back toward where it is tied, behind and above.
            val top = V(0.45, 2.1, -0.75)
            val sh = shoulder(L)
            val hand = sh + (top - sh).unit() * (Build.ARM - 0.004)
            grip(L, hand, V.DOWN, V.FWD)
            rod(top, hand, 0.009, Tone.RUBBER)
            rod(V(top.x, 0.0, top.z), V(top.x, 2.3, top.z), 0.035)
        }
    },
    "lats_rack_lean" to {
        // The left hand holds a rack upright at chest height, the arm straight: the hips sit back and the trunk leans away and turns down.
        stretch(yaw = -40f) { d ->
            val post = V(0.15, 0.0, 0.62)
            val hand = V(post.x, 1.05 - 0.1 * d, post.z - 0.05)
            fun pose(z: Double) {
                pelvis(V(0.0, STAND - 0.12 * d, z), pitch = 40 * d, yaw = -8 * d)
                spine(waist = 8 * d, chest = 6 * d, side = -18 * d, twist = -14 * d)
            }
            // The hips sit back just as far as keeps the arm straight on the upright.
            pose(0.0)
            val sh = shoulder(L)
            val across = sq(sh.x - hand.x) + sq(sh.y - hand.y)
            pose(hand.z - sqrt(sq(Build.ARM - 0.004) - across) - sh.z)
            head(nod = 12 * d, turn = -10 * d)
            feet(width = 0.3)
            grip(L, hand, V(1.0, -0.4, -0.3), V.LEFT)
            arm(R, raise = 20.0 + 25 * d, elbow = 30.0)
            rod(post, post + V.UP * 2.3, 0.035)
        }
    },
    "shoulders_band_dislocates" to {
        // A band held wide in both hands, the arms straight: it travels from the front of the hips up overhead and down behind the back.
        Motion(Tempo(1.6, 0.3, 1.6, 0.5), balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0))
            spine(chest = -4 * phase(d, 0.3, 0.7))
            head()
            feet()
            val q = lerp(25.0, 320.0, d) * DEG
            val r = sqrt(sq(Build.ARM - 0.004) - sq(0.62 - Build.SHOULDER))
            val mid = shoulders()
            val hands = SIDES.map { s -> mid + V(s * 0.62, -r * cos(q), r * sin(q)) }
            for ((s, h) in SIDES.zip(hands)) grip(s, h, V(s * 1.0, -0.5, -sin(q) * 0.5), V(0.0, cos(q), -sin(q)) * -1.0)
            rod(hands[0], hands[1], 0.009, Tone.RUBBER)
        }
    },
)

/** Limber 11 (Joe DeFranco), in its order. */
internal val LIMBER: Map<String, () -> Motion> = mapOf(
    "hips_foam_roll_it_band" to {
        // On the left side propped on the left hand, the outside of the left thigh on the roller and the right foot planted in
        // front: hand and foot slide the body so the roller travels from the hip toward the knee.
        Motion(ROLLING, yaw = 20f) { d ->
            // The bottom leg runs from the floor over the roller, so the hip slides up and down that line.
            val top = V(-0.06, 2 * ROLLER + 0.065, 0.0)
            val line = V(cos(10 * DEG), sin(10 * DEG), 0.0)
            fun trunk(along: Double) {
                pelvis(Frame.WORLD.moved(V(0.0, 0.3, 0.0)).tilt(66.0))
                spine(side = 8.0)
                head(tilt = -12.0)
                nudge(top + line * along - hip(L))
            }
            trunk(0.21)
            val hand = shoulder(L) + V(0.02, 0.0, 0.08)
            val foot = hip(R) + V(-0.2, 0.0, 0.34)
            trunk(lerp(0.34, 0.08, d))
            legToward(L, -line, knee = 4.0, ankle = -20.0)
            plant(R, V(foot.x, 0.0, foot.z), 20.0, knee = V(0.0, 1.0, 0.4))
            palm(L, V(hand.x, 0.0, hand.z), V(1.0, 0.0, 0.3), V(0.0, 0.2, -1.0))
            arm(R, raise = 12.0, elbow = 25.0)
            roller(V(top.x, ROLLER, 0.0), V.FWD)
        }
    },
    "hips_foam_roll_adductors" to {
        // Face down on the forearms and the right toes, the left knee bent and out to the side with the inside of the thigh on a
        // roller: the hips shift sideways so the roller travels from the groin toward the knee.
        Motion(ROLLING, yaw = 150f) { d ->
            val elbow = Build.UPPER_ARM + 0.04
            val reach = sqrt(sq(trunkLength()) - sq(elbow - 0.2))
            fun lie(slide: Double) = trunk(V(slide, 0.2, 0.0), V(slide, elbow, reach))
            lie(0.0)
            val roller = V(hip(L).x + 0.2, ROLLER, hip(L).z - 0.1)
            val hands = SIDES.map { s -> shoulder(s).let { V(it.x - s * 0.03, 0.0, it.z + Build.FOREARM + 0.07) } }
            lie(lerp(0.12, -0.1, d))
            head(nod = -6.0)
            legTo(L, hip(L) + V(0.45, -0.08, -0.4), V(1.0, 0.3, 0.0), foot = -60.0)
            plant(R, V(-0.12, 0.0, -0.84), rise = 70.0, knee = V.UP)
            for ((s, at) in SIDES.zip(hands)) palm(s, at, V.FWD, V(s * 0.2, -1.0, -0.3))
            roller(roller, V.FWD)
        }
    },
    "hips_glute_ball" to {
        // Sitting back on the hands with the left ankle across the right knee and a ball under the left glute: small circles roll it.
        Motion(ROLLING) { d ->
            fun sit(q: Double, r: Double) {
                pelvis(V(r * cos(q), 0.3, r * sin(q)), pitch = -52.0, roll = -6.0)
                spine(waist = 8.0, chest = 4.0)
                head(nod = 20.0)
                rest(0.0, listOf(Bone.PELVIS))
            }
            // Straight arms prop the body: the hands land under and behind the shoulders of the centred pose.
            sit(0.0, 0.0)
            val hands = SIDES.map { s -> shoulder(s).let { V(it.x + s * 0.06, 0.0, it.z - sqrt(sq(Build.ARM - 0.05) - sq(it.y) - sq(0.06))) } }
            sit(2 * PI * d, 0.03)
            plant(R, V(-0.12, 0.0, 0.46), 6.0, knee = V(0.0, 1.0, 0.3))
            legTo(L, this[Bone.SHIN_R].o + V(0.05, 0.07, -0.03), V(1.0, 0.5, 0.2), foot = 50.0)
            for ((s, at) in SIDES.zip(hands)) palm(s, at, V.BACK, V(s * 0.3, 0.0, -1.0))
            ball(V(0.1, 0.032, -0.06), 0.032, Tone.RUBBER)
        }
    },
    "spine_iron_cross" to {
        // On the back with the arms wide and the hips and knees at 90 degrees, the knees lower to one side, then to the other.
        Motion(Tempo(6.0, 0.0, 0.0, 0.0, from = 0.25)) { d ->
            val a = cos(2 * PI * d)
            supineTwist(62 * a, a)
        }
    },
    "hamstrings_rollover_v_sit" to {
        // From a wide-legged seat the body rolls back until the legs pass overhead, then rolls forward into a V-sit and reaches between the legs.
        Motion(Tempo(1.8, 0.6, 1.8, 0.4)) { d ->
            val pitch = lerp(-120.0, 26.0, d)
            val waist = lerp(26.0, 18.0, d)
            val chest = lerp(24.0, 12.0, d)
            fun pose(y: Double, raise: Double) {
                // The rounded back rolls along the floor: about 0.2 m of travel a radian.
                pelvis(V(0.0, y, 0.2 * (pitch - 26.0) * DEG), pitch)
                spine(waist, chest)
                head(nod = 20.0)
                for (s in SIDES) leg(s, raise, plane = 50.0, knee = 4.0, ankle = -20.0)
                val w = phase(d, 0.55, 1.0)
                for (s in SIDES) arm(s, raise = lerp(25.0, 120.0, w), plane = lerp(70.0, 80.0, w), elbow = 8.0, pronate = lerp(0.0, 60.0, w))
            }
            // Seated, the straight legs lie on the floor; rolled back they keep the hips' pike and swing overhead.
            var raise = 90.0 + pitch
            if (d > 0.5) repeat(3) {
                pose(0.5, raise)
                raise -= asin(((lowest(listOf(Bone.SHIN_L, Bone.FOOT_L)) - lowest(listOf(Bone.PELVIS))) / 0.9).coerceIn(-1.0, 1.0)) / DEG
            }
            raise = lerp(115.0, raise, phase(d, 0.5, 0.85))
            pose(0.5, raise)
            pose(0.5 - lowest(), raise)
        }
    },
    "hips_rocking_frog" to {
        // On the hands and wide knees, the feet in line behind the knees: the hips rock back toward the heels, then forward.
        Motion(Tempo(2.0, 0.6, 2.0, 0.4)) { d ->
            bridge(kneeling(0.7, 0.0, lerp(-5.0, 55.0, d)), V(0.0, 0.022, 0.5), 0.54, waist = 6 * d, chest = 4.0)
            head(nod = 10.0)
            for (s in SIDES) {
                kneel(s, V(s * 0.35, KNEE, 0.0))
                palm(s, V(s * 0.19, 0.0, 0.575), V.FWD, V(s * 0.4, 0.2, -1.0))
            }
        }
    },
    "hips_fire_hydrant_circles" to {
        // On all fours, the left knee lifts out to the side and draws circles, forward and then back.
        Motion(Tempo(2.5, 0.25, 2.5, 0.25)) { d ->
            bridge(kneeling(0.2, 0.0, 0.0), V(0.0, 0.022, 0.5), 0.54)
            head(nod = -5.0)
            val q = 2 * PI * d
            kneel(R, V(-0.1, KNEE, 0.0))
            leg(L, raise = 90.0 + 20 * sin(q), plane = 30.0 + 18 * (1 - cos(q)), knee = 90.0, ankle = -60.0)
            for (s in SIDES) palm(s, V(s * 0.19, 0.0, 0.575), V.FWD, V(s * 0.4, 0.2, -1.0))
        }
    },
    "hips_mountain_climber" to {
        // From the top of a push-up one foot steps up outside its hand and the hips sink, then it steps back and the other foot goes.
        Motion(Tempo(6.0, 0.0, 0.0, 0.0, from = 0.25)) { d ->
            val u = cos(2 * PI * d)
            val w = SIDES.map { s -> jerk(max(0.0, s * u)) }
            val sh = V(0.0, 0.56, 0.45)
            trunk(lerp(V(0.0, 0.386, -0.02), V(0.0, 0.3, -0.03), w.max()), sh)
            head(nod = -10.0)
            for ((s, t) in SIDES.zip(w)) {
                palm(s, V(s * 0.18, 0.0, 0.525), V.FWD, V(s * 0.3, 0.2, -1.0))
                val back = V(s * 0.1, 0.0, -0.78)
                val front = V(s * 0.38, 0.0, 0.42)
                plant(s, lerp(back, front, t) + V.UP * (0.12 * sin(PI * t)), 10 * t, rise = 70 * (1 - t), knee = V(s * 1.0, 0.4, 0.4))
            }
        }
    },
    "hips_cossack_squat" to {
        // Feet wide and turned out: the hips sink over one bent leg while the other straightens with its toes up, then cross over.
        Motion(Tempo(6.0, 0.0, 0.0, 0.0, from = 0.25)) { d ->
            val u = cos(2 * PI * d)
            val side = if (u >= 0) L else R
            val deep = jerk(kotlin.math.abs(u))
            pelvis(V(side * 0.3 * deep, STAND - 0.07 - 0.4 * deep, -0.06 * deep), pitch = 8 + 30 * deep)
            spine(waist = 4.0, chest = 4 * deep)
            head(nod = -6 * deep)
            for (s in SIDES) {
                val toes = V.FWD.turn(V.UP, s * 25.0)
                val ball = V(s * 0.4, 0.0, 0.08)
                // The straight leg's foot pivots up on its heel.
                val up = if (s == side) 0.0 else 40 * deep
                val heel = ball - toes * 0.2
                plant(s, heel + toes * (0.2 * cos(up * DEG)) + V.UP * (0.2 * sin(up * DEG)), 25.0, rise = -up)
                arm(s, raise = 20 + 65 * deep, plane = 90.0, elbow = 15.0)
            }
        }
    },
    "hips_seated_piriformis" to {
        // Sitting tall, the left foot crosses over the straight right leg and both arms hug the left knee in toward the right shoulder.
        stretch { d ->
            val pitch = lerp(6.0, 12.0, d)
            sit(pitch, chest = -2.0, twist = 10 * d)
            head(turn = 10 * d)
            outstretched(R, pitch, -25.0)
            plant(L, V(-0.2, 0.0, 0.5), knee = lerp(V(-0.1, 0.8, 0.5), V(-0.5, 0.8, 0.3), d))
            val shin = this[Bone.SHIN_L]
            for (s in SIDES) grip(s, shin.at(s * 0.035, 0.08, 0.07), V(s * 1.0, -0.4, -0.3), -shin.z)
        }
    },
    "hips_rfe_hip_flexor" to {
        // Standing in a split stance with the left foot laces-down on a bench behind: the back knee sinks and the hips press
        // forward under a tall chest.
        stretch(yaw = 65f) { d ->
            pelvis(V(0.0, lerp(0.65, 0.58, d), lerp(0.0, 0.04, d)), pitch = lerp(4.0, -4.0, d))
            spine(waist = 2.0, chest = 2.0)
            head(nod = 4.0)
            plant(R, V(-0.12, 0.0, 0.5), 5.0)
            legTo(L, V(0.1, 0.5, -0.62), V(0.0, -1.0, 0.4), foot = -60.0)
            val t = this[Bone.THIGH_R]
            palm(R, t.at(0.0, 0.32, 0.062), t.y, V(-0.6, 0.0, -1.0), t.z)
            palm(L, t.at(0.0, 0.14, 0.088), t.y, V(0.6, 0.0, -1.0), t.z)
            block(0.1, -0.8, 0.4, lowest(listOf(Bone.FOOT_L, Bone.TOES_L)), 0.45)
        }
    },
)

/** Bends the [s] leg so its ankle lands on [ankle], the knee toward [pole] and the foot [foot] degrees up from square. */
internal fun Poser.legTo(s: Int, ankle: V, pole: V, foot: Double = -20.0) {
    val h = hip(s)
    val k = reach(h, ankle, Build.THIGH, Build.SHIN, pole)
    val bend = kotlin.math.acos(((k - h).unit() dot (ankle - k).unit()).coerceIn(-1.0, 1.0)) / DEG
    // The thigh's turn decides where the bent knee sends the shin: search it for the one that lands the ankle.
    fun miss(t: Double): Double {
        legToward(s, k - h, knee = bend, ankle = foot, twist = t)
        return (this[Bone.foot(s)].o - ankle).len
    }
    var best = (-180..180 step 10).minBy { miss(it.toDouble()) }.toDouble()
    var step = 5.0
    repeat(8) { best = listOf(best - step, best, best + step).minBy { miss(it) }; step /= 2 }
    miss(best)
}

/** Points the [s] leg along [dir], bent as given. */
internal fun Poser.legToward(s: Int, dir: V, knee: Double = 0.0, ankle: Double = 0.0, twist: Double = 0.0) {
    val p = this[Bone.PELVIS]
    val b = V(dir dot p.x, dir dot p.y, dir dot p.z).unit()
    leg(s, kotlin.math.acos(-b.y) / DEG, atan2(b.z, s * b.x) / DEG, twist, knee, ankle)
}

/** A foam roller's radius. */
private const val ROLLER = 0.075

/** Slow, even passes back and forth over a roller or ball. */
private val ROLLING = Tempo(1.8, 0.2, 1.8, 0.2)

/** A foam roller lying on the floor centred on [c], its length along [axis]. */
private fun Poser.roller(c: V, axis: V) = rod(c - axis * 0.3, c + axis * 0.3, ROLLER, Tone.PAD)

/**
 * One position of a flow: the hips and the trunk's pitch and bend, the head, the balls of the feet [lift] up at [ball] with the
 * heels [rise] degrees up (past 180 the foot has rolled over onto its top), and the arms, raised [arms] degrees or with the
 * palms on the floor; [sec] to reach it, the feet [hop] high on the way.
 */
private class Key(
    val hip: V, val pitch: Double, val waist: Double, val chest: Double, val nod: Double,
    val ball: Double, val rise: Double, val arms: Double?, val sec: Double = 3.0, val hop: Double = 0.0, val lift: Double = 0.0,
)

/**
 * Surya Namaskara A as Ashtanga counts it, one breath a count: Ekam arms up, Dwe fold, Trini look up, Chatvari jump back to
 * chaturanga, Pancha up dog, Shat down dog for five breaths, Sapta jump forward and look up, Ashtau fold, Nava arms up, then
 * stand. The loop opens and closes mid down dog, so its still frame is the pose held longest, and plays from standing.
 */
private fun sunSalutationA(): Motion {
    val p = Poser()
    val hz = 0.15 // the palms' middles, beside the toes
    val fz = hz - 1.39 // the balls of the feet back in the plank
    val wrist = V(0.0, 0.022, hz - 0.075)
    fun length(waist: Double, chest: Double) = p.run { trunkLength(waist, chest) }
    fun pitch(hip: V, sh: V, waist: Double, chest: Double): Double {
        p.pelvis(V.ZERO); p.spine(waist, chest)
        val q = p.shoulders() - p[Bone.PELVIS].o
        val w = sh - hip
        return (atan2(w.z, w.y) - atan2(q.z, q.y)) / DEG
    }
    // The pitch that sets the shoulders [r] from the wrists, the hips held at [hip]: folding further only brings them closer.
    fun toFloor(hip: V, waist: Double, chest: Double, r: Double): Double {
        var lo = 40.0
        var hi = 175.0
        repeat(30) {
            val mid = (lo + hi) / 2
            p.pelvis(hip, mid); p.spine(waist, chest)
            if ((p.shoulders() - wrist).len > r) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }
    val stand = Key(V(0.0, STAND, -0.1), 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    val ekam = Key(stand.hip, -4.0, 0.0, -8.0, -25.0, 0.0, 0.0, 186.0)
    val foldHip = V(0.0, 0.85, -0.22)
    val dwe = Key(foldHip, toFloor(foldHip, 20.0, 14.0, 0.44), 20.0, 14.0, 30.0, 0.0, 0.0, null)
    val liftHip = V(0.0, 0.89, -0.15)
    val trini = Key(liftHip, toFloor(liftHip, -6.0, -4.0, 0.535), -6.0, -4.0, -28.0, 0.0, 0.0, null)
    val chatvari = run {
        val ank = ankle(V(0.0, 0.0, fz), 75.0)
        val len = 0.855 + length(0.0, 0.0)
        val y = 0.22
        val sh = V(0.0, y, ank.z + sqrt(len * len - (y - ank.y) * (y - ank.y)))
        val hip = reach(ank, sh, 0.855, len - 0.855, V.UP)
        Key(hip, pitch(hip, sh, 0.0, 0.0), 0.0, 0.0, -10.0, fz, 75.0, null, hop = 0.1)
    }
    // Rolled over the toes, the ball rises so the top of the foot rests on the floor.
    val top = V(0.0, 0.093, fz + 0.06)
    val pancha = run {
        val ank = ankle(top, 180.0)
        val sh = V(0.0, 0.022 + 0.535, hz - 0.075)
        val hip = reach(ank, sh, 0.855, length(-28.0, -14.0), V.DOWN)
        Key(hip, pitch(hip, sh, -28.0, -14.0), -28.0, -14.0, -30.0, top.z, 180.0, null, lift = top.y)
    }
    val dog = run {
        val ank = ankle(V(0.0, 0.0, fz), 25.0)
        val len = length(0.0, -5.0)
        val hip = reach(ank, wrist, 0.855, len + 0.545, V.UP)
        Key(hip, pitch(hip, hip + (wrist - hip).unit() * len, 0.0, -5.0), 0.0, -5.0, 15.0, fz, 25.0, null)
    }
    val hold = 15.0
    val keys = listOf(
        dog, dog.copy(hold), trini.copy(hop = 0.12), dwe, ekam, stand, ekam, dwe, trini, chatvari, pancha, dog, dog.copy(hold),
    )
    val times = keys.drop(1).runningFold(0.0) { t, k -> t + k.sec }
    val period = times.last()
    return Motion(Tempo(period, 0.0, 0.0, 0.0, from = times[5] / period), yaw = 70f) { d ->
        val t = d * period
        val i = (1 until keys.size).first { times[it] >= t || it == keys.size - 1 }
        val a = keys[i - 1]
        val b = keys[i]
        val u = ((t - times[i - 1]) / (times[i] - times[i - 1])).coerceIn(0.0, 1.0)
        val e = jerk(u)
        val hop = b.hop * sin(PI * u)
        pelvis(lerp(a.hip, b.hip, e) + V(0.0, hop * 0.6, 0.0), lerp(a.pitch, b.pitch, e))
        spine(lerp(a.waist, b.waist, e), lerp(a.chest, b.chest, e))
        head(nod = lerp(a.nod, b.nod, e))
        for (s in SIDES) {
            val knee = if (a.ball < -0.5 && b.ball < -0.5) V.DOWN else null
            plant(s, V(s * 0.07, lerp(a.lift, b.lift, e) + hop, lerp(a.ball, b.ball, e)), rise = lerp(a.rise, b.rise, e), knee = knee)
            val floor = V(s * 0.2, 0.0, hz)
            val elbow = V(s * 0.3, 0.3, -1.0)
            val ra = a.arms
            val rb = b.arms
            when {
                ra == null && rb == null -> palm(s, floor, V.FWD, elbow)
                ra != null && rb != null -> sweep(s, lerp(ra, rb, e))
                else -> {
                    // Between the floor and a free arm: the arm swings along the trunk, the palm settling onto the floor near the end.
                    val f = if (rb == null) e else 1 - e
                    sweep(s, lerp(ra ?: rb!!, 172.0, f))
                    val h = this[Bone.hand(s)]
                    val w = phase(f, 0.5, 1.0)
                    palm(s, lerp(hand(s), floor, w), lerp(h.y, V.FWD, w), elbow, lerp(-h.z, V.UP, w))
                }
            }
        }
    }
}

/** Arms swept out to the sides and up, [raise] degrees, turning so the palms meet overhead. */
private fun Poser.sweep(s: Int, raise: Double) {
    val up = phase(raise, 60.0, 180.0)
    arm(s, raise, plane = lerp(10.0, 6.0, up), twist = 90 * up, elbow = 4.0, pronate = lerp(90.0, 0.0, up))
}

private fun Key.copy(sec: Double = this.sec, hop: Double = this.hop) = Key(hip, pitch, waist, chest, nod, ball, rise, arms, sec, hop, lift)
