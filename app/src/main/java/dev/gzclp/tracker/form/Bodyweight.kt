package dev.gzclp.tracker.form

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The plain, weighted and assisted versions of a bodyweight exercise, which differ in their gear and sometimes their setup. */
internal enum class Kind { PLAIN, WEIGHTED, ASSISTED }

/** A pull-up bar's height. */
private const val BAR = 2.25

/** A dip belt around the hips with a 10 kg plate hanging on its chain in front of the thighs. */
private fun Poser.belt() {
    val p = this[Bone.PELVIS]
    val ring = (0 until 10).map { k -> (k * 36 * DEG).let { p.at(0.17 * sin(it), 0.07, 0.125 * cos(it) - 0.01) } }
    for (k in 0 until 10) rod(ring[k], ring[(k + 1) % 10], 0.014, Tone.RUBBER)
    val plate = V(p.o.x, p.o.y - 0.36, p.o.z) + p.z.across(V.UP).unit(V.FWD) * 0.2
    for (s in SIDES) rod(p.at(s * 0.15, 0.07, 0.06), plate, 0.006)
    disc(plate, p.z.across(V.UP).unit(V.FWD), 0.165, 0.035)
}

/** A resistance band looped over [top] and under both bent knees. */
private fun Poser.band(top: V) {
    val k = SIDES.map { this[Bone.shin(it)].at(0.0, 0.07, 0.055) }
    for (s in SIDES) rod(top + V(s * 0.03, 0.0, 0.0), k[if (s == L) 0 else 1], 0.008, Tone.RUBBER)
    rod(k[0], k[1], 0.008, Tone.RUBBER)
}

/** Hanging legs: together and a little ahead, toes pointed; bent at the knee to sit in a band. */
private fun Poser.dangle(kind: Kind, lift: Double) {
    for (s in SIDES) {
        if (kind == Kind.ASSISTED) leg(s, raise = 25 + lift, plane = 95.0, knee = 95.0, ankle = -25.0)
        else leg(s, raise = 6 + lift, plane = 100.0, knee = 5 + lift, ankle = -30.0)
    }
}

/** A pull-up (pronated, 1.5 shoulder widths) or chin-up (supinated, about shoulder width), from a dead hang to the chin over the bar. */
private fun pull(kind: Kind, chin: Boolean) = Motion(Tempo(1.0, 0.4, 1.6, 0.6), balance = true) { d ->
    val width = if (chin) 0.4 else 0.62
    shoulders(up = 0.03 * (1 - d), fwd = -0.02 * d)
    val sh = V(0.0, lerp(BAR - drop(width), BAR - 0.13, d), -0.07 * d)
    trunk(sh + V(0.0, -0.5, 0.02 + 0.04 * d), sh, waist = -4.0 * d, chest = -6.0 * d)
    head(nod = -14 * d)
    hold(V(0.0, BAR, 0.0), width, V(0.0, 0.4, if (chin) -1.0 else 1.0), if (chin) V(0.3, -0.3, 1.0) else V(1.0, -0.2, 0.25))
    dangle(kind, 5 * d)
    pullUpBar()
    when (kind) {
        Kind.WEIGHTED -> belt()
        Kind.ASSISTED -> band(V(0.0, BAR, 0.0))
        Kind.PLAIN -> {}
    }
}

/** A bar at [BAR] running between the tops of two uprights. */
private fun Poser.pullUpBar() {
    rod(V(-0.62, BAR, 0.0), V(0.62, BAR, 0.0), 0.016)
    for (s in SIDES) rod(V(s * 0.62, 0.0, 0.0), V(s * 0.62, BAR + 0.05, 0.0), 0.03)
}

/** A push-up on the toes with the body straight from heels to head, pivoting about the toes; weighted adds a plate on the back, assisted puts the hands on a box. */
private fun pushUp(kind: Kind) = Motion(Tempo(1.6, 0.3, 1.0, 0.6)) { d ->
    val box = if (kind == Kind.ASSISTED) 0.45 else 0.0
    val rise = 75.0 - box * 45
    val ball = if (kind == Kind.ASSISTED) -1.1 else -1.39
    val ank = ankle(V(0.0, 0.0, ball), rise)
    val len = 0.855 + trunkLength()
    val y = lerp(box + 0.56, box + 0.19, d)
    val sh = V(0.0, y, ank.z + sqrt(len * len - (y - ank.y) * (y - ank.y)))
    trunk(reach(ank, sh, 0.855, len - 0.855, V.UP), sh)
    head(nod = -10.0)
    for (s in SIDES) palm(s, V(s * 0.24, box, 0.0), V.FWD, V(s * 0.7, 0.3, -1.0))
    for (s in SIDES) plant(s, V(s * 0.1, 0.0, ball), rise = rise, knee = V.DOWN)
    when (kind) {
        Kind.WEIGHTED -> disc(this[Bone.CHEST].at(0.0, 0.12, -0.12), this[Bone.CHEST].z, 0.17, 0.04)
        Kind.ASSISTED -> block(0.0, 0.0, 0.7, box, 0.4)
        Kind.PLAIN -> {}
    }
}

/**
 * A handstand press: upside down on straight arms, the body straight from hands to heels, lowering until the head nears the
 * floor between the hands, the elbows travelling forward and out.
 */
private fun handstandPress() = Motion(Tempo(2.0, 0.3, 1.2, 0.6)) { d ->
    val len = trunkLength()
    val sh = V(0.0, lerp(drop(0.6) - 0.06, 0.36, d), lerp(0.0, 0.08, d))
    trunk(sh + V.UP * len, sh)
    head(nod = -25.0)
    for (s in SIDES) palm(s, V(s * 0.3, 0.0, -0.06), -V.FWD, V(s * 0.8, 0.2, -0.6))
    for (s in SIDES) leg(s, raise = 3.0, plane = 0.0, ankle = -40.0)
}

/** Parallel-bar height for dips. */
private const val DIP = 1.2

/** A dip from straight arms down until the upper arms are level, the chest leaning forward as the elbows travel back. */
private fun dip(kind: Kind) = Motion(Tempo(2.0, 0.3, 1.0, 0.6), balance = true) { d ->
    shoulders(up = lerp(-0.02, 0.02, d))
    val sh = V(0.0, lerp(DIP + drop(0.5), DIP + 0.33, d), 0.26 * d)
    val lean = lerp(6.0, 24.0, d) * DEG
    trunk(sh - V(0.0, cos(lean), sin(lean)) * 0.51, sh)
    head(nod = 12.0)
    hold(V(0.0, DIP, 0.0), 0.5, V(-1.0, 0.0, 0.0), V(0.15, 0.0, -1.0))
    for (s in SIDES) leg(s, raise = 20.0, plane = 95.0, knee = 85.0, ankle = -20.0)
    for (s in SIDES) {
        rod(V(s * 0.25, DIP, -0.5), V(s * 0.25, DIP, 0.45), 0.02)
        rod(V(s * 0.25, 0.0, -0.45), V(s * 0.25, DIP, -0.45), 0.03)
        rod(V(s * 0.25, 0.0, 0.4), V(s * 0.25, DIP, 0.4), 0.03)
    }
    when (kind) {
        Kind.WEIGHTED -> belt()
        Kind.ASSISTED -> {
            val k = SIDES.map { this[Bone.shin(it)].at(0.0, 0.1, 0.06) }
            rod(V(0.25, DIP, k[0].z), k[0], 0.008, Tone.RUBBER)
            rod(k[0], k[1], 0.008, Tone.RUBBER)
            rod(k[1], V(-0.25, DIP, k[1].z), 0.008, Tone.RUBBER)
        }
        Kind.PLAIN -> {}
    }
}

/**
 * A strict muscle-up through four positions: a dead hang, a high pull with the chest to the bar, the transition with the
 * chest rolling over it, and the support with straight arms. The hands turn over the bar and the elbows swing from out to back.
 */
private fun muscleUp(kind: Kind) = Motion(Tempo(1.6, 0.6, 2.0, 0.8)) { d ->
    val sh = V(0.0, spline(d, BAR - drop(0.6), BAR - 0.08, BAR + 0.12, BAR + drop(0.6) - 0.04), spline(d, 0.0, -0.12, 0.1, 0.0))
    val lean = spline(d, 0.0, -18.0, 35.0, 14.0) * DEG
    shoulders(up = spline(d, 0.03, 0.0, 0.01, -0.04))
    trunk(sh - V(0.0, cos(lean), sin(lean)) * 0.51, sh)
    head(nod = spline(d, 0.0, -15.0, 20.0, 5.0))
    val turn = lerp(22.0, 180.0, phase(d, 0.3, 0.85)) * DEG
    hold(V(0.0, BAR, 0.0), 0.6, V(0.0, sin(turn), cos(turn)), lerp(V(1.0, -0.2, 0.25), V(0.3, 0.0, -1.0), phase(d, 0.4, 0.7)))
    dangle(kind, spline(d, 0.0, 25.0, 45.0, 5.0))
    pullUpBar()
    when (kind) {
        Kind.WEIGHTED -> belt()
        Kind.ASSISTED -> band(V(0.0, BAR, 0.0))
        Kind.PLAIN -> {}
    }
}

/** The inverted-row bar's height. */
private const val LOW = 0.95

/**
 * An inverted row under a bar, the body a straight plank pivoting on the heels until the chest meets the bar; weighted adds a
 * plate on the stomach, assisted bends the knees with the feet flat so the body pivots at the knees.
 */
private fun invertedRow(kind: Kind) = Motion(Tempo(1.0, 0.4, 1.6, 0.6)) { d ->
    val bar = V(0.0, LOW, 0.0)
    val reach = lerp(0.615, 0.17, d)
    shoulders(fwd = lerp(0.03, -0.04, d))
    if (kind == Kind.ASSISTED) {
        val knee = V(0.0, 0.5, 0.75)
        val sh = reach(bar, knee, reach, 0.94, V.DOWN)
        trunk(knee + (sh - knee).unit() * Build.THIGH, sh)
        for (s in SIDES) plant(s, V(s * 0.12, 0.0, 0.955), knee = V.UP)
    } else {
        // The heel is close to a 2.5 cm circle 5 cm behind and below the ankle, rolling in place on the floor.
        fun ankle(u: V) = V(0.0, 0.025, 1.2) + (V.UP.across(u).unit() + u) * 0.05
        var ankle = ankle(V.BACK)
        repeat(2) { ankle = ankle((reach(bar, ankle, reach, 1.365, V.DOWN) - ankle).unit()) }
        val sh = reach(bar, ankle, reach, 1.365, V.DOWN)
        val u = (sh - ankle).unit()
        trunk(ankle + u * 0.855, sh)
        val toes = V.UP.across(u).unit()
        for (s in SIDES) plant(s, ankle + V(s * 0.1, 0.0, 0.0) + toes * Build.BALL - u * Build.ANKLE, ground = u, ahead = V.UP)
    }
    head(nod = 10.0)
    hold(bar, 0.5, V(0.0, 0.0, 1.0), V(0.5, -1.0, 0.2))
    rod(V(-0.65, LOW, 0.0), V(0.65, LOW, 0.0), 0.016)
    for (s in SIDES) rod(V(s * 0.6, 0.0, 0.0), V(s * 0.6, 1.1, 0.0), 0.03)
    if (kind == Kind.WEIGHTED) this[Bone.WAIST].let { disc(it.at(0.0, 0.05, 0.15), it.z, 0.17, 0.04) }
}

/** A pistol squat on the left leg, the right held straight out in front; weighted holds a goblet dumbbell, assisted holds a post. */
private fun pistol(kind: Kind) = Motion(Tempo(2.0, 0.3, 1.4, 0.8), balance = true) { d ->
    val pitch = lerp(4.0, 38.0, d)
    pelvis(V(0.0, lerp(STAND, 0.32, d), -0.18 * d), pitch = pitch)
    spine(waist = lerp(-6.0, 10.0, d), chest = 4.0)
    head(nod = lerp(5.0, -10.0, d))
    plant(L, V(0.1, 0.0, 0.12), 10.0)
    leg(R, raise = lerp(55.0, 95.0 + pitch, d), knee = lerp(25.0, 0.0, d), ankle = 15.0)
    when (kind) {
        Kind.WEIGHTED -> {
            val c = this[Bone.CHEST].at(0.0, 0.12, 0.22)
            for (s in SIDES) grip(s, c + V(s * 0.03, 0.0, 0.0), V(s * 0.3, -1.0, 0.2), V(-s * 1.0, 0.0, 0.0))
            dumbbell(c, V.UP, 0.07)
            carry(0.15, c)
        }
        Kind.ASSISTED -> {
            rod(V(0.1, 0.0, 0.45), V(0.1, 1.5, 0.45), 0.025)
            for (s in SIDES) grip(s, V(0.1 + s * 0.035, lerp(1.1, 0.85, d), 0.45), V(s * 0.5, -0.8, -0.3), V(-s * 1.0, 0.0, 0.0))
        }
        Kind.PLAIN -> for (s in SIDES) arm(s, raise = lerp(70.0, 100.0 + pitch / 2, d), plane = 85.0, elbow = 10.0, fist = true)
    }
}

internal val BODYWEIGHT: Map<String, () -> Motion> = buildMap {
    fun three(id: String, make: (Kind) -> Motion) {
        put(id) { make(Kind.PLAIN) }
        put("weighted_$id") { make(Kind.WEIGHTED) }
        put("assisted_$id") { make(Kind.ASSISTED) }
    }
    three("pull_up") { pull(it, chin = false) }
    three("chin_up") { pull(it, chin = true) }
    three("push_up") { pushUp(it) }
    three("dip") { dip(it) }
    three("muscle_up") { muscleUp(it) }
    three("inverted_row") { invertedRow(it) }
    three("pistol_squat") { pistol(it) }
    put("handstand_press") { handstandPress() }
}
