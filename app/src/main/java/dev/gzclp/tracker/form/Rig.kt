package dev.gzclp.tracker.form

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal const val DEG = PI / 180

/** A point or direction in metres: y up, the figure faces +z, +x is the figure's left. */
data class V(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V) = V(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V) = V(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = V(x * s, y * s, z * s)
    operator fun unaryMinus() = V(-x, -y, -z)
    infix fun dot(o: V) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V) = V(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val len: Double get() = sqrt(this dot this)
    fun unit(fallback: V = UP): V = len.let { if (it < 1e-9) fallback else this * (1 / it) }

    /** This vector without its part along the unit vector [n]. */
    fun across(n: V) = this - n * (this dot n)

    /** Turned about the unit axis [k] by [deg] degrees, right-handed. */
    fun turn(k: V, deg: Double): V {
        val c = cos(deg * DEG)
        val s = sin(deg * DEG)
        return this * c + (k cross this) * s + k * ((k dot this) * (1 - c))
    }

    companion object {
        val ZERO = V(0.0, 0.0, 0.0)
        val UP = V(0.0, 1.0, 0.0)
        val DOWN = V(0.0, -1.0, 0.0)
        val FWD = V(0.0, 0.0, 1.0)
        val BACK = V(0.0, 0.0, -1.0)
        val LEFT = V(1.0, 0.0, 0.0)
    }
}

fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
fun lerp(a: V, b: V, t: Double) = a + (b - a) * t

/** An orthonormal frame at [o]: [y] runs along the part toward its far end, [z] is its front, [x] = y × z. */
data class Frame(val o: V, val x: V, val y: V, val z: V) {
    fun at(lx: Double, ly: Double, lz: Double) = o + x * lx + y * ly + z * lz
    fun dir(lx: Double, ly: Double, lz: Double) = x * lx + y * ly + z * lz
    fun turn(k: V, deg: Double) = Frame(o, x.turn(k, deg), y.turn(k, deg), z.turn(k, deg))

    /** Tips the far end toward the front. */
    fun bend(deg: Double) = turn(x, deg)

    /** Turns the front toward +x. */
    fun twist(deg: Double) = turn(y, deg)

    /** Tips the far end toward +x. */
    fun tilt(deg: Double) = turn(z, -deg)
    fun moved(to: V) = Frame(to, x, y, z)

    companion object {
        val WORLD = Frame(V.ZERO, V.LEFT, V.UP, V.FWD)

        /** The frame at [o] whose far end lies along [y], its front as near [front] as square to [y] allows ([fallback] when [front] runs along [y]). */
        fun along(o: V, y: V, front: V, fallback: V = V.FWD): Frame {
            val yy = y.unit()
            val f = front.across(yy)
            val z = if (f.len > 1e-6) f.unit() else fallback.across(yy).unit((if (abs(yy.y) < 0.9) V.UP else V.FWD).across(yy).unit())
            return Frame(o, yy cross z, yy, z)
        }
    }
}

const val L = 1
const val R = -1
val SIDES = intArrayOf(L, R)

/** The figure's parts, each posed as a [Frame]: origin at its joint, y toward its far end, z its front (the top of the foot for a foot). */
enum class Bone(val mass: Double, val comY: Double) {
    PELVIS(0.142, 0.0), WAIST(0.139, 0.09), CHEST(0.216, 0.15), NECK(0.0, 0.0), HEAD(0.081, 0.08),
    ARM_L(0.028, 0.13), FOREARM_L(0.016, 0.11), HAND_L(0.006, 0.06),
    ARM_R(0.028, 0.13), FOREARM_R(0.016, 0.11), HAND_R(0.006, 0.06),
    THIGH_L(0.1, 0.18), SHIN_L(0.0465, 0.19), FOOT_L(0.0145, 0.04), TOES_L(0.0, 0.0),
    THIGH_R(0.1, 0.18), SHIN_R(0.0465, 0.19), FOOT_R(0.0145, 0.04), TOES_R(0.0, 0.0);

    companion object {
        val TRUNK = listOf(PELVIS, WAIST, CHEST, NECK, HEAD)
        fun arm(s: Int) = if (s == L) ARM_L else ARM_R
        fun forearm(s: Int) = if (s == L) FOREARM_L else FOREARM_R
        fun hand(s: Int) = if (s == L) HAND_L else HAND_R
        fun thigh(s: Int) = if (s == L) THIGH_L else THIGH_R
        fun shin(s: Int) = if (s == L) SHIN_L else SHIN_R
        fun foot(s: Int) = if (s == L) FOOT_L else FOOT_R
        fun toes(s: Int) = if (s == L) TOES_L else TOES_R
    }
}

/** Proportions of a 1.75 m adult from Drillis and Contini's segment ratios, in metres. */
object Build {
    const val PELVIS = 0.10 // hip joints up to the base of the spine
    const val WAIST = 0.18
    const val CHEST = 0.28 // up to the base of the neck
    const val NECK = 0.10
    const val HIP = 0.09 // each hip joint out from the middle
    const val SHOULDER = 0.18
    const val SHOULDER_DROP = 0.05 // shoulder joints below the base of the neck
    const val UPPER_ARM = 0.30
    const val FOREARM = 0.25
    const val GRIP = 0.075 // wrist to the middle of a closed fist
    const val THIGH = 0.43
    const val SHIN = 0.43
    const val ANKLE = 0.075 // ankle joint above the sole
    const val BALL = 0.14 // ankle to the ball of the foot
    const val TOE = 0.02 // toe joint above the sole, where the foot hinges as the heel lifts
    const val ARM = UPPER_ARM + FOREARM + GRIP // shoulder to the middle of the fist
}

/**
 * The ankle over a sole whose ball rests on [at], toes along [toes] on a surface facing [n], the heel [rise] degrees up. The foot
 * hinges at the toe joint, so the toes stay flat and the forefoot never sinks into the floor.
 */
fun ankle(at: V, rise: Double, toes: V = V.FWD, n: V = V.UP): V {
    val foot = Frame(V.ZERO, toes cross n, toes, n).bend(-rise)
    return at + n * Build.TOE - foot.y * Build.BALL + foot.z * (Build.ANKLE - Build.TOE)
}

/** The knee or elbow of a two-bone limb from [a] reaching for [t], bent toward [pole]; a target out of reach leaves the limb straight. */
fun reach(a: V, t: V, l1: Double, l2: Double, pole: V): V {
    val to = t - a
    val d = to.len.coerceIn(abs(l1 - l2) + 1e-4, l1 + l2 - 1e-6)
    val u = to.unit(V.DOWN)
    val along = (l1 * l1 - l2 * l2 + d * d) / (2 * d)
    val h = sqrt(max(0.0, l1 * l1 - along * along))
    val side = pole.across(u).unit(V.FWD.across(u).unit(V.LEFT))
    return a + u * along + side * h
}

/**
 * Builds one frame of the figure. Call [pelvis] first (it sets a neutral [spine] and [head]), then optionally [spine], [head]
 * and [shoulders], then place each arm ([grip], [palm], [arm]) and leg ([plant], [kneel], [leg]). Positions are world metres;
 * angles are degrees. Limbs left unplaced hang relaxed.
 */
private val TORSO = arrayOf(Bone.PELVIS, Bone.WAIST, Bone.CHEST, Bone.NECK)
private val LEGS = SIDES.map { arrayOf(Bone.thigh(it), Bone.shin(it)) }
private val ARMS = SIDES.map { arrayOf(Bone.arm(it), Bone.forearm(it), Bone.hand(it)) }

class Poser {
    internal val f = arrayOfNulls<Frame>(Bone.entries.size)
    internal val gear = ArrayList<Placed>()
    internal val fist = BooleanArray(2)

    /** Added to the pelvis, so a balance or floor correction moves the body while planted feet stay put. */
    var shift = V.ZERO

    /** 0..1, how far into a breath: lifts the chest and shoulders a little. */
    var breath = 0.0

    /** Extra mass carried (as a share of body weight) and where it sits, for balance. */
    internal var load = 0.0
    internal var loadAt = V.ZERO

    /** The sum and count of the points the feet stand on, for balance. */
    internal var stance = V.ZERO
    internal var feet = 0

    /** The worst distance a limb fell short of its target, in metres; a pose that holds together keeps this near zero. */
    var strain = 0.0
        private set
    private val up = DoubleArray(2)
    private val fwd = DoubleArray(2)

    operator fun get(b: Bone) = f[b.ordinal]!!
    fun has(b: Bone) = f[b.ordinal] != null
    private fun put(b: Bone, fr: Frame) { f[b.ordinal] = fr }
    private fun i(s: Int) = if (s == L) 0 else 1
    fun closed(s: Int) = fist[i(s)]

    fun reset() {
        f.fill(null); gear.clear(); fist.fill(false); up.fill(0.0); fwd.fill(0.0)
        load = 0.0; loadAt = V.ZERO; stance = V.ZERO; feet = 0; strain = 0.0
    }

    /** Places the pelvis between the hip joints at [at]: [pitch] tips the trunk forward (90 lies face down, -90 on the back), [yaw] turns it left, [roll] tips it toward its left side. */
    fun pelvis(at: V, pitch: Double = 0.0, yaw: Double = 0.0, roll: Double = 0.0) = pelvis(Frame.WORLD.moved(at).twist(yaw).bend(pitch).tilt(roll))

    /** Places the pelvis on [f], for turns simpler to chain than to give as the angles above (on the back, rolled about the spine). */
    fun pelvis(f: Frame) {
        put(Bone.PELVIS, f.moved(f.o + shift))
        spine()
    }

    /** Bends the lower ([waist]) and upper ([chest]) spine forward (negative arches it), [side] toward the left, and [twist]s the chest to the left. */
    fun spine(waist: Double = 0.0, chest: Double = 0.0, side: Double = 0.0, twist: Double = 0.0) {
        val p = this[Bone.PELVIS]
        val w = p.moved(p.at(0.0, Build.PELVIS, -0.01)).bend(waist).tilt(side * 0.4).twist(twist * 0.4)
        put(Bone.WAIST, w)
        put(Bone.CHEST, w.moved(w.at(0.0, Build.WAIST, 0.0)).bend(chest - breath * 2).tilt(side * 0.6).twist(twist * 0.6))
        head()
    }

    /** Nods the head ([nod] chin down), [turn]s it left, [tilt]s it toward the left shoulder, and [retract]s it (pulls it back level, as a press passes the face). */
    fun head(nod: Double = 0.0, turn: Double = 0.0, tilt: Double = 0.0, retract: Double = 0.0) {
        val c = this[Bone.CHEST]
        val n = c.moved(c.at(0.0, Build.CHEST, 0.0)).bend(15 + nod * 0.5 - retract).twist(turn * 0.5).tilt(tilt * 0.5)
        put(Bone.NECK, n)
        put(Bone.HEAD, n.moved(n.at(0.0, Build.NECK, 0.0)).bend(-15 + nod * 0.5 + retract + breath * 1.5).twist(turn * 0.5).tilt(tilt * 0.5))
    }

    /** Lifts the shoulder blades [up] (metres, a shrug) and slides them forward [fwd] (negative pulls them back and together). */
    fun shoulders(up: Double = 0.0, fwd: Double = 0.0, side: Int = 0) {
        for (s in SIDES) if (side == 0 || side == s) { this.up[i(s)] = up; this.fwd[i(s)] = fwd }
    }

    /** Moves the trunk and head by [by]; call it before placing the limbs. */
    fun nudge(by: V) { for (b in Bone.TRUNK) if (has(b)) put(b, this[b].moved(this[b].o + by)) }

    fun shoulder(s: Int): V =
        this[Bone.CHEST].at(s * Build.SHOULDER, Build.CHEST - Build.SHOULDER_DROP + up[i(s)] + breath * 0.004, 0.02 + fwd[i(s)])

    fun shoulders(): V = (shoulder(L) + shoulder(R)) * 0.5
    fun hip(s: Int): V = this[Bone.PELVIS].at(s * Build.HIP, 0.0, 0.0)

    /** The middle of the [s] fist, or of the palm of a flat hand. */
    fun hand(s: Int): V = this[Bone.hand(s)].let { if (closed(s)) it.at(0.0, Build.GRIP, 0.0) else it.at(0.0, 0.075, 0.022) }

    private fun strain(target: V, got: V) { strain = max(strain, (target - got).len) }

    /** Closes the [s] hand around [at] (the middle of the fist) with the palm facing [palm]; the elbow points toward [elbow]. */
    fun grip(s: Int, at: V, elbow: V, palm: V) {
        val sh = shoulder(s)
        val el = reach(sh, at, Build.UPPER_ARM, Build.FOREARM + Build.GRIP, elbow)
        strain(at, el + (at - el).unit() * (Build.FOREARM + Build.GRIP))
        put(Bone.arm(s), Frame.along(sh, el - sh, at - sh, -elbow))
        val fore = Frame.along(el, at - el, palm, this[Bone.arm(s)].z)
        put(Bone.forearm(s), fore)
        put(Bone.hand(s), fore.moved(fore.at(0.0, Build.FOREARM, 0.0)))
        fist[i(s)] = true
    }

    /** Lays the [s] hand flat with its palm on [at], fingers toward [fingers], on a surface facing [ground]; the elbow points toward [elbow]. */
    fun palm(s: Int, at: V, fingers: V, elbow: V, ground: V = V.UP) {
        val n = ground.unit()
        val y = fingers.across(n).unit()
        val wrist = at - y * 0.075 + n * 0.022
        val sh = shoulder(s)
        val el = reach(sh, wrist, Build.UPPER_ARM, Build.FOREARM, elbow)
        strain(wrist, el + (wrist - el).unit() * Build.FOREARM)
        put(Bone.arm(s), Frame.along(sh, el - sh, wrist - sh, -elbow))
        put(Bone.forearm(s), Frame.along(el, wrist - el, -n, y))
        put(Bone.hand(s), Frame(wrist, y cross -n, y, -n))
        fist[i(s)] = false
    }

    /**
     * Swings the [s] arm out of a relaxed hang: [raise] lifts it (90 level, 180 overhead) in the [plane] (0 straight out to the side,
     * 90 straight ahead, negative behind), [twist] turns it outward, [elbow] bends it, [pronate] turns the palm in (90 faces the thigh).
     */
    fun arm(s: Int, raise: Double = 0.0, plane: Double = 90.0, twist: Double = 0.0, elbow: Double = 0.0, pronate: Double = 90.0, wrist: Double = 0.0, fist: Boolean = false) {
        val upper = swing(this[Bone.CHEST], shoulder(s), s, raise, plane, twist)
        put(Bone.arm(s), upper)
        val fore = upper.moved(upper.at(0.0, Build.UPPER_ARM, 0.0)).bend(elbow).twist(s * pronate)
        put(Bone.forearm(s), fore)
        put(Bone.hand(s), fore.moved(fore.at(0.0, Build.FOREARM, 0.0)).bend(wrist))
        this.fist[i(s)] = fist
    }

    /**
     * Stands the [s] foot with the ball of the foot on [at], toes turned out [out] degrees and the heel lifted [rise] degrees,
     * on a surface facing [ground] with [ahead] its forward; the knee tracks over the toes unless [knee] says where it points.
     * On an [edge] (a step) the heel hangs free, so the weight rests on the ball of the foot.
     */
    fun plant(s: Int, at: V, out: Double = 0.0, rise: Double = 0.0, knee: V? = null, ground: V = V.UP, ahead: V = V.FWD, edge: Boolean = false) {
        val n = ground.unit()
        val toes = ahead.across(n).unit().turn(n, s * out)
        val flat = Frame(V.ZERO, toes cross n, toes, n)
        val ankle = ankle(at, rise, toes, n)
        put(Bone.foot(s), flat.bend(-rise).moved(ankle))
        put(Bone.toes(s), flat.moved(at + n * Build.TOE))
        val hip = hip(s)
        val pole = knee ?: (toes + flat.x * (-s * 0.12))
        val kn = reach(hip, ankle, Build.THIGH, Build.SHIN, pole)
        strain(ankle, kn + (ankle - kn).unit() * Build.SHIN)
        val thigh = Frame.along(hip, kn - hip, -(ankle - hip), pole)
        put(Bone.thigh(s), thigh)
        put(Bone.shin(s), Frame.along(kn, ankle - kn, thigh.y, thigh.z))
        if (n.y > 0.7) {
            stance += if (edge) at else lerp(at - toes * 0.09, at, (rise / 40).coerceIn(0.0, 1.0))
            feet++
        }
    }

    /**
     * Kneels the [s] leg with the knee toward [knee] (it lands a thigh's length from the hip) and the shin lying back along
     * the floor (or a bench top at height [floor]) away from [ahead]: on the top of the foot, or on the toes when [tuck] is set.
     */
    fun kneel(s: Int, knee: V, ahead: V = V.FWD, tuck: Boolean = false, floor: Double = 0.0) {
        val hip = hip(s)
        val kn = hip + (knee - hip).unit() * Build.THIGH
        strain(knee, kn)
        val fwd = ahead.across(V.UP).unit()
        val back = -fwd
        val rise = floor + (if (tuck) 0.15 else 0.045) - kn.y
        val ankle = kn + back * sqrt(Build.SHIN * Build.SHIN - rise * rise) + V.UP * rise
        val thigh = Frame.along(hip, kn - hip, fwd)
        put(Bone.thigh(s), thigh)
        put(Bone.shin(s), Frame.along(kn, ankle - kn, V.DOWN, back))
        val foot = if (tuck) Frame.along(ankle, V.DOWN * 0.96 + fwd * 0.28, fwd) else Frame.along(ankle, back * 0.95 + V.DOWN * 0.31, V.DOWN)
        put(Bone.foot(s), foot)
        val ball = foot.at(0.0, Build.BALL, -Build.ANKLE)
        put(Bone.toes(s), if (tuck) Frame.along(ball + V.UP * 0.02, fwd, V.UP) else foot.moved(foot.at(0.0, Build.BALL, 0.02 - Build.ANKLE)))
    }

    /** Swings the [s] leg like [arm]: [raise] in the [plane] (90 ahead, 0 out to the side), [twist] outward, then bends the [knee], [ankle] (toes up) and [toes]. */
    fun leg(s: Int, raise: Double = 0.0, plane: Double = 90.0, twist: Double = 0.0, knee: Double = 0.0, ankle: Double = 0.0, toes: Double = 0.0) {
        val thigh = swing(this[Bone.PELVIS], hip(s), s, raise, plane, twist)
        put(Bone.thigh(s), thigh)
        val shin = thigh.moved(thigh.at(0.0, Build.THIGH, 0.0)).bend(-knee)
        put(Bone.shin(s), shin)
        val foot = shin.moved(shin.at(0.0, Build.SHIN, 0.0)).bend(90 + ankle)
        put(Bone.foot(s), foot)
        put(Bone.toes(s), foot.moved(foot.at(0.0, Build.BALL, 0.02 - Build.ANKLE)).bend(toes))
    }

    /** A limb's upper frame hanging from [root] and swung out of the [body] frame's downward direction. */
    private fun swing(body: Frame, root: V, s: Int, raise: Double, plane: Double, twist: Double): Frame {
        val e = raise * DEG
        val p = plane * DEG
        val d = body.dir(s * sin(e) * cos(p), -cos(e), sin(e) * sin(p))
        val axis = ((-body.y) cross body.dir(s * cos(p), 0.0, sin(p))).unit(body.x)
        val z = body.z.turn(axis, raise).turn(d, -s * twist)
        return Frame.along(root, d, z)
    }

    /** Carries a load of [share] times body weight at [at], for balance. */
    fun carry(share: Double, at: V) { load = share; loadAt = at }

    /** Relaxed arms and straight legs for any the pose left out. */
    internal fun finish() {
        for (s in SIDES) if (!has(Bone.arm(s))) arm(s, raise = 4.0, plane = 20.0, elbow = 10.0)
        for (s in SIDES) if (!has(Bone.thigh(s))) leg(s)
    }

    /** The centre of mass of the body and whatever it carries. */
    fun com(): V {
        var m = 0.0
        var c = V.ZERO
        for (b in Bone.entries) if (b.mass > 0) {
            val fr = this[b]
            val p = if (b == Bone.FOOT_L || b == Bone.FOOT_R) fr.at(0.0, 0.04, -0.04) else fr.at(0.0, b.comY, 0.0)
            c += p * b.mass
            m += b.mass
        }
        if (load > 0) { c += loadAt * load; m += load }
        return c * (1 / m)
    }

    /** Each bent surface with the bones that bend it: the torso and neck, each leg, and each arm with its hand open or closed. */
    internal fun chains() = listOf(
        Body.torso to TORSO, Body.leg to LEGS[0], Body.leg to LEGS[1],
        (if (closed(L)) Body.fist else Body.flat) to ARMS[0], (if (closed(R)) Body.fist else Body.flat) to ARMS[1],
    )

    internal val bent = FloatArray(maxOf(Body.torso.mesh.verts, Body.leg.mesh.verts, Body.fist.mesh.verts, Body.flat.mesh.verts) * 3)

    /** Every point of the skin of [bones] in world space; a bone in a [Skin] gives the part of it that follows that bone most. */
    internal inline fun skin(bones: List<Bone>, block: (Double, Double, Double) -> Unit) {
        for (b in bones) if (has(b)) Body.of(b)?.each(this[b], block = block)
        for ((s, chain) in chains()) {
            if (chain.none { it in bones } || !chain.all { has(it) }) continue
            s.pose(this, chain, bent)
            for (i in 0 until s.mesh.verts) if (chain[s.lead[i]] in bones) block(bent[i * 3].toDouble(), bent[i * 3 + 1].toDouble(), bent[i * 3 + 2].toDouble())
        }
    }

    /** The lowest point of the skin of [bones]. */
    fun lowest(bones: List<Bone> = Bone.entries): Double {
        var lo = Double.MAX_VALUE
        skin(bones) { _, y, _ -> lo = min(lo, y) }
        return lo
    }
}
