package dev.gzclp.tracker.form

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A dumbbell in each closed hand, its handle running across the fist. */
private fun Poser.bells(head: Double = 0.06) { for (s in SIDES) dumbbell(hand(s), this[Bone.hand(s)].x, head) }

/** Standing tall with soft knees, hip-width feet, the trunk tipped forward [lean] degrees. */
private fun Poser.stand(lean: Double = 0.0, width: Double = 0.3) {
    pelvis(V(0.0, STAND, 0.0), pitch = lean)
    spine(waist = -6.0, chest = 3.0)
    feet(width, 0.1, 8.0)
}

/** A cable tower: a post from the floor at [x], [z] and a pulley at [top], with the cable running to [to]. */
private fun Poser.tower(x: Double, z: Double, top: V, to: V) {
    rod(V(x, 0.0, z), V(x, top.y + 0.12, z), 0.04)
    rod(V(x, top.y, z), top, 0.025)
    disc(top, V.LEFT, 0.045, 0.03, Tone.METAL)
    cable(top, to)
}

internal val ACCESSORIES: Map<String, () -> Motion> = mapOf(
    "dumbbell_row" to {
        Motion(Tempo(0.9, 0.3, 1.5, 0.6)) { d ->
            shoulders(fwd = lerp(0.03, -0.03, d), side = R)
            trunk(V(0.03, 0.9, -0.22), V(0.03, 0.97, 0.27), waist = -2.0)
            head(nod = -15.0)
            val h = hip(L)
            val drop = h.y - PAD - 0.045
            kneel(L, h + V(0.0, -drop, sqrt(Build.THIGH * Build.THIGH - drop * drop)), floor = PAD)
            plant(R, V(-0.26, 0.0, -0.06), 10.0)
            palm(L, V(0.21, PAD, 0.33), V.FWD, V(1.0, 0.0, -0.3))
            val sh = shoulder(R)
            val c = lerp(V(sh.x + 0.03, sh.y - 0.6, sh.z), sh + V(-0.02, -0.175, -0.26), d)
            grip(R, c, V(-0.2, 0.6, -1.0), V.LEFT)
            dumbbell(c, V.FWD, 0.065)
            block(Frame.WORLD.moved(V(0.13, PAD - 0.04, 0.0)), 0.29, 0.08, 1.3)
            block(0.13, -0.5, 0.1, PAD - 0.08, 0.1, Tone.METAL)
            block(0.13, 0.5, 0.1, PAD - 0.08, 0.1, Tone.METAL)
        }
    },
    "incline_db_press" to {
        Motion(Tempo(1.6, 0.3, 1.1, 0.9)) { d ->
            shoulders(up = -0.01, fwd = -0.03)
            val hip = V(0.0, 0.55, 0.15)
            trunk(hip, hip + V(0.0, 0.293, -0.418), waist = -8.0, chest = -6.0)
            head(nod = 10.0)
            val sh = shoulders()
            val front = this[Bone.CHEST].z
            val down = (hip - sh).unit()
            for (s in SIDES) {
                // The upper arm swings from straight up to out, back and toward the hips; the forearm stays upright under the dumbbell.
                val el = shoulder(s) + lerp(V.UP, (V(s * 0.75, 0.0, 0.0) + down * 0.55 - front * 0.55).unit(), d).unit() * Build.UPPER_ARM
                grip(s, el + V.UP * (Build.FOREARM + Build.GRIP), el - shoulder(s), V.FWD)
                dumbbell(hand(s), V.LEFT, 0.07)
            }
            for (s in SIDES) plant(s, V(s * 0.25, 0.0, 0.75), 12.0)
            val up = (sh - hip).unit()
            block(Frame.along(lerp(hip, sh, 0.55) - front * 0.17, up, front), 0.29, 0.7, 0.08)
            val seat = lowest(listOf(Bone.PELVIS))
            block(Frame.WORLD.moved(V(0.0, seat - 0.04, hip.z + 0.08)), 0.29, 0.08, 0.36)
            block(0.0, hip.z + 0.05, 0.1, seat - 0.08, 0.1, Tone.METAL)
        }
    },
    "lateral_raise" to {
        Motion(Tempo(1.0, 0.3, 1.8, 0.6), balance = true) { d ->
            stand(6.0)
            for (s in SIDES) arm(s, raise = lerp(12.0, 85.0, d), plane = 25.0, elbow = 15.0, fist = true)
            bells()
            carry(0.1, (hand(L) + hand(R)) * 0.5)
        }
    },
    "rear_delt_fly" to {
        Motion(Tempo(1.0, 0.3, 1.8, 0.6), balance = true) { d ->
            val hip = V(0.0, 0.86, -0.15)
            trunk(hip, hip + V(0.0, 0.215, 0.462), waist = -3.0, chest = 3.0)
            head(nod = -12.0)
            feet(0.3, 0.05, 8.0)
            for (s in SIDES) arm(s, raise = lerp(65.0, 92.0, d), plane = lerp(90.0, 0.0, d), elbow = 20.0, fist = true)
            bells(0.05)
            carry(0.1, (hand(L) + hand(R)) * 0.5)
        }
    },
    "hammer_curl" to {
        Motion(Tempo(1.0, 0.3, 1.8, 0.6), balance = true) { d ->
            stand(2.0)
            for (s in SIDES) arm(s, raise = 5 + 10 * d, plane = 80.0, elbow = lerp(8.0, 135.0, d), fist = true)
            bells()
            carry(0.15, (hand(L) + hand(R)) * 0.5)
        }
    },
    "overhead_tricep_extension" to {
        Motion(Tempo(1.8, 0.3, 1.0, 0.6), balance = true) { d ->
            pelvis(V(0.0, STAND, 0.0))
            spine(waist = -8.0, chest = 0.0)
            head(nod = 6.0)
            feet(0.3, 0.1, 8.0)
            val sh = shoulders()
            val c = lerp(V(0.0, sh.y + 0.6, sh.z + 0.02), V(0.0, sh.y + 0.12, sh.z - 0.2), d)
            for (s in SIDES) grip(s, c + V(s * 0.035, 0.0, 0.0), V(s * 0.3, 1.0, 0.15), V(-s * 1.0, 0.5, 0.0))
            dumbbell(c + V(0.0, -0.06, 0.0), V.UP, 0.07)
            carry(0.15, c)
        }
    },
    "lat_pulldown" to {
        Motion(Tempo(1.0, 0.3, 1.8, 0.6)) { d ->
            shoulders(up = 0.03 * (1 - d), fwd = -0.03 * d)
            val lean = lerp(10.0, 18.0, d) * DEG
            val hip = V(0.0, 0.58, 0.0)
            trunk(hip, hip + V(0.0, 0.51 * cos(lean), -0.51 * sin(lean)), waist = -6.0 * d, chest = -4.0 * d)
            head(nod = -10 * d)
            rest(0.48, listOf(Bone.PELVIS))
            val sh = shoulders()
            val top = V(0.0, sh.y + drop(0.76, 0.2), sh.z + 0.2)
            val low = this[Bone.CHEST].at(0.0, 0.24, 0.13).let { V(0.0, it.y, it.z + 0.03) }
            val bar = lerp(top, low, d)
            hold(bar, 0.76, V(0.0, 0.4, 1.0), V(1.0, -0.6, -0.1))
            rod(bar + V(-0.6, 0.0, 0.0), bar + V(0.6, 0.0, 0.0), 0.014)
            for (s in SIDES) plant(s, V(s * 0.2, 0.0, 0.5), 10.0)
            block(Frame.WORLD.moved(V(0.0, 0.44, 0.02)), 0.36, 0.08, 0.4)
            block(0.0, 0.02, 0.1, 0.4, 0.1, Tone.METAL)
            val knee = this[Bone.thigh(L)].at(0.0, Build.THIGH - 0.06, 0.0)
            rod(V(-0.2, knee.y + 0.12, knee.z), V(0.2, knee.y + 0.12, knee.z), 0.045, Tone.PAD)
            tower(0.0, 0.85, V(0.0, 2.25, 0.12), bar)
        }
    },
    "seated_cable_row" to {
        Motion(Tempo(1.0, 0.3, 1.6, 0.6)) { d ->
            shoulders(fwd = lerp(0.04, -0.04, d))
            val lean = lerp(14.0, -5.0, d) * DEG
            val hip = V(0.0, 0.52, 0.0)
            trunk(hip, hip + V(0.0, 0.51 * cos(lean), 0.51 * sin(lean)), waist = lerp(4.0, -4.0, d))
            head(nod = -6.0)
            rest(0.42, listOf(Bone.PELVIS))
            // The handle runs level at the pulley's height, from straight arms in to the stomach.
            val sh = shoulders()
            val y = 0.8
            val far = V(0.0, y, sh.z + sqrt(Build.ARM * Build.ARM - (sh.y - y) * (sh.y - y) - 0.13 * 0.13) - 0.01)
            val near = V(0.0, y, this[Bone.WAIST].at(0.0, 0.12, 0.14).z + 0.06)
            val handle = lerp(far, near, d)
            hold(handle, 0.1, V(-1.0, 0.0, 0.0), V(0.2, -0.3, -1.0))
            for (s in SIDES) rod(handle + V(s * 0.05, 0.0, 0.0), handle + V(0.0, 0.0, 0.09), 0.012)
            for (s in SIDES) plant(s, V(s * 0.12, 0.28, 0.84), 5.0, ground = V(0.0, 0.25, -1.0), ahead = V.UP)
            block(Frame.along(V(0.0, 0.3, 0.9), V.UP, V(0.0, 0.25, -1.0)), 0.5, 0.45, 0.04, Tone.METAL)
            block(Frame.WORLD.moved(V(0.0, 0.38, -0.05)), 0.3, 0.08, 0.9)
            block(0.0, -0.05, 0.12, 0.34, 0.3, Tone.METAL)
            tower(0.0, 1.05, V(0.0, y, 0.96), handle + V(0.0, 0.0, 0.09))
        }
    },
    "chest_fly" to {
        Motion(Tempo(1.0, 0.4, 1.6, 0.6), balance = true) { d ->
            pelvis(V(0.0, STAND - 0.05, 0.0), pitch = 12.0)
            spine(waist = -4.0, chest = 4.0)
            plant(L, V(0.15, 0.0, 0.3), 6.0)
            plant(R, V(-0.15, 0.0, -0.2), 10.0, rise = 18.0)
            for (s in SIDES) arm(s, raise = lerp(95.0, 55.0, d), plane = lerp(-15.0, 110.0, d), elbow = 20.0, pronate = 0.0, fist = true)
            for (s in SIDES) {
                tower(s * 1.0, -0.15, V(s * 0.96, 1.95, -0.15), hand(s))
                rod(hand(s) - this[Bone.hand(s)].x * 0.06, hand(s) + this[Bone.hand(s)].x * 0.06, 0.012)
            }
        }
    },
    "face_pull" to {
        Motion(Tempo(1.0, 0.4, 1.6, 0.6), balance = true) { d ->
            stand(-4 * d)
            shoulders(fwd = lerp(0.03, -0.04, d))
            val sh = shoulders()
            val pulley = V(0.0, 1.7, 1.1)
            val spread = lerp(0.06, 0.2, d)
            val mid = V(0.0, lerp(1.6, 1.66, d), lerp(sh.z + 0.55, sh.z + 0.05, d))
            val k = mid + (pulley - mid).unit() * sqrt(0.0625 - spread * spread)
            for (s in SIDES) {
                val h = mid + V(s * spread, 0.0, 0.0)
                grip(s, h, V(s * 1.0, 0.3, -0.6), lerp(V(-s * 1.0, 0.0, 0.0), V(-s * 0.3, 0.0, 1.0), d))
                rod(h, k, 0.012, Tone.RUBBER)
            }
            tower(0.0, 1.15, pulley, k)
        }
    },
    "tricep_pushdown" to {
        Motion(Tempo(1.0, 0.4, 1.6, 0.6), balance = true) { d ->
            stand(10.0)
            for (s in SIDES) arm(s, raise = 10.0, plane = 85.0, elbow = lerp(95.0, 5.0, d), pronate = 180.0, fist = true)
            val mid = (hand(L) + hand(R)) * 0.5
            rod(mid + V(-0.25, 0.0, 0.0), mid + V(0.25, 0.0, 0.0), 0.014)
            tower(0.0, 0.5, V(0.0, 2.05, 0.45), mid)
        }
    },
    "leg_press" to {
        Motion(Tempo(2.0, 0.3, 1.2, 0.8)) { d ->
            // The sled runs up the 45-degree rail; the feet sit 19 cm up the platform from the hip line so the shins stay near upright.
            val n = V(0.0, -0.707, -0.707)
            val up = V(0.0, 0.707, -0.707)
            pelvis(V(0.0, 0.5, 0.0), pitch = -64.0)
            spine(waist = -3.0, chest = 6.0)
            head(nod = 30.0)
            val reach = 0.86 * cos(lerp(15.0, 85.0, d) * DEG / 2)
            val along = sqrt(reach * reach - 0.19 * 0.19 - 0.0016)
            for (s in SIDES) plant(s, hip(s) + V(s * 0.04, 0.0, 0.0) + up * (0.19 + Build.BALL) - n * (along + Build.ANKLE), ground = n, ahead = up)
            val hipMid = this[Bone.PELVIS].o
            val c = hipMid + up * (0.19 + Build.BALL) - n * (along + Build.ANKLE)
            block(Frame.along(c - n * 0.02 + up * 0.03, up, n), 0.62, 0.62, 0.04, Tone.METAL)
            block(Frame.along(c - n * 0.1 - up * 0.12, up, n), 0.5, 0.5, 0.12, Tone.METAL)
            for (s in SIDES) {
                val a = hipMid - up * 0.032 - n * 0.6 + V(s * 0.26, 0.0, 0.0)
                val b = hipMid - up * 0.032 - n * 1.2 + V(s * 0.26, 0.0, 0.0)
                rod(a, b, 0.03)
                rod(V(b.x, 0.0, b.z), a, 0.03)
                rod(V(b.x, 0.0, b.z), b, 0.03)
                val h = V(s * 0.3, hipMid.y + 0.04, hipMid.z)
                grip(s, h, V(s * 0.6, 0.2, -1.0), V(-s * 1.0, 0.0, 0.0))
                rod(h + V(0.0, 0.0, -0.1), h + V(0.0, 0.0, 0.1), 0.014)
                rod(h + V(0.0, 0.0, -0.1), V(h.x, 0.0, h.z - 0.1), 0.02)
            }
            val sh = shoulders()
            val front = this[Bone.CHEST].z
            block(Frame.along(lerp(hipMid, sh, 0.5) - front * 0.15, (sh - hipMid).unit(), front), 0.42, 0.85, 0.08)
            val seat = lowest(listOf(Bone.PELVIS))
            block(Frame.WORLD.moved(V(0.0, seat - 0.04, hipMid.z - 0.05)), 0.42, 0.08, 0.4)
            block(0.0, hipMid.z - 0.15, 0.36, seat - 0.08, 0.6, Tone.METAL)
        }
    },
    "leg_curl" to {
        Motion(Tempo(1.0, 0.5, 2.0, 0.6)) { d ->
            pelvis(V(0.0, 0.72, 0.0), pitch = 90.0)
            spine(chest = -4.0)
            head(nod = -30.0)
            rest(0.66, listOf(Bone.PELVIS, Bone.WAIST, Bone.CHEST))
            for (s in SIDES) leg(s, knee = lerp(5.0, 115.0, d))
            val roll = this[Bone.shin(L)].at(0.0, 0.37, -0.1)
            val knee = this[Bone.shin(L)].o
            rod(V(-0.2, roll.y, roll.z), V(0.2, roll.y, roll.z), 0.05, Tone.PAD)
            rod(V(-0.24, knee.y, knee.z), V(-0.24, roll.y, roll.z), 0.02)
            disc(V(-0.24, knee.y, knee.z), V.LEFT, 0.05, 0.04, Tone.METAL)
            rod(V(-0.24, 0.0, knee.z), V(-0.24, knee.y, knee.z), 0.03)
            val bar = V(0.0, 0.48, 0.72)
            hold(bar, 0.44, V(0.0, 0.0, -1.0), V(1.0, 0.4, -0.3))
            rod(bar + V(-0.3, 0.0, 0.0), bar + V(0.3, 0.0, 0.0), 0.014)
            for (s in SIDES) rod(V(s * 0.3, 0.0, bar.z), bar + V(s * 0.3, 0.0, 0.0), 0.02)
            block(Frame.WORLD.moved(V(0.0, 0.62, 0.09)), 0.32, 0.08, 0.94)
            block(0.0, -0.2, 0.12, 0.58, 0.12, Tone.METAL)
            block(0.0, 0.4, 0.12, 0.58, 0.12, Tone.METAL)
        }
    },
    "leg_extension" to {
        Motion(Tempo(1.0, 0.5, 2.0, 0.6)) { d ->
            pelvis(V(0.0, 0.6, 0.0), pitch = -10.0)
            spine(waist = -4.0, chest = 4.0)
            head(nod = 8.0)
            rest(0.5, listOf(Bone.PELVIS))
            for (s in SIDES) leg(s, raise = 80.0, knee = lerp(90.0, 5.0, d))
            val roll = this[Bone.shin(L)].at(0.0, 0.36, 0.1)
            val knee = this[Bone.shin(L)].o
            rod(V(-0.2, roll.y, roll.z), V(0.2, roll.y, roll.z), 0.05, Tone.PAD)
            rod(V(-0.25, knee.y, knee.z), V(-0.25, roll.y, roll.z), 0.02)
            disc(V(-0.25, knee.y, knee.z), V.LEFT, 0.05, 0.04, Tone.METAL)
            val hipMid = this[Bone.PELVIS].o
            for (s in SIDES) {
                val h = V(s * 0.3, 0.56, hipMid.z + 0.05)
                grip(s, h, V(s * 0.5, 0.0, -1.0), V(-s * 1.0, 0.0, 0.0))
                rod(h + V(0.0, 0.0, -0.1), h + V(0.0, 0.0, 0.1), 0.014)
                rod(h + V(0.0, 0.0, -0.1), V(h.x, 0.0, h.z - 0.1), 0.02)
            }
            val sh = shoulders()
            val front = this[Bone.CHEST].z
            block(Frame.along(lerp(hipMid, sh, 0.55) - front * 0.15, (sh - hipMid).unit(), front), 0.4, 0.6, 0.08)
            block(Frame.WORLD.moved(V(0.0, 0.46, hipMid.z + 0.06)), 0.4, 0.08, 0.6)
            block(0.0, hipMid.z - 0.05, 0.3, 0.42, 0.4, Tone.METAL)
        }
    },
    "calf_raise" to {
        Motion(Tempo(1.0, 0.6, 1.6, 0.6), balance = true) { d ->
            val rise = lerp(-18.0, 35.0, d)
            pelvis(V(0.0, 0.07 + STAND + ankle(V.ZERO, rise).y - Build.ANKLE, 0.0))
            spine(waist = -6.0, chest = 3.0)
            for (s in SIDES) plant(s, V(s * 0.13, 0.1, 0.1), 8.0, rise = rise, edge = true)
            for (s in SIDES) arm(s, raise = 4.0, plane = 30.0, elbow = 5.0, fist = true)
            bells()
            carry(0.3, (hand(L) + hand(R)) * 0.5)
            block(0.0, 0.23, 0.7, 0.1, 0.3)
        }
    },
)
