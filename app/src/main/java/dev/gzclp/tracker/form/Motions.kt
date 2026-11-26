package dev.gzclp.tracker.form

import dev.gzclp.tracker.stretch.Pacing
import dev.gzclp.tracker.stretch.RepPosition
import dev.gzclp.tracker.stretch.StretchMove
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/** One rep's timing in seconds: [out] from the start position to the far end, a [pause] there, [back] again, then [rest] at the start. */
class Tempo(val out: Double, val pause: Double, val back: Double, val rest: Double, private val from: Double? = null) {
    val period = out + pause + back + rest

    /** True for a flow, which runs straight through its movement and never retraces it. */
    val flows: Boolean get() = from != null

    /** How far through the movement (0 the start position, 1 the far end) at [t] seconds into the loop. */
    fun at(t: Double): Double {
        // A flow never retraces itself: it runs straight through 0..1 once a period, starting at [from].
        if (from != null) return ((t / period + from) % 1 + 1) % 1
        var s = ((t % period) + period) % period
        if (s < out) return jerk(s / out)
        s -= out
        if (s < pause) return 1.0
        s -= pause
        return if (s < back) 1 - jerk(s / back) else 0.0
    }
}

/** The minimum-jerk curve people move along: smooth to start and stop, quickest in the middle; flat outside 0..1. */
fun jerk(x: Double): Double {
    val t = x.coerceIn(0.0, 1.0)
    return t * t * t * (10 - 15 * t + 6 * t * t)
}

/** [jerk] over the part of the movement from [a] to [b]: 0 before it, 1 after. */
fun phase(d: Double, a: Double, b: Double) = jerk((d - a) / (b - a))

/** A smooth curve through [keys] spaced evenly over d = 0..1 (Catmull-Rom), for moves with more than two positions. */
fun spline(d: Double, vararg keys: Double): Double {
    val n = keys.size - 1
    val x = d.coerceIn(0.0, 1.0) * n
    val i = min(x.toInt(), n - 1)
    val t = x - i
    val p0 = keys[maxOf(i - 1, 0)]; val p1 = keys[i]; val p2 = keys[i + 1]; val p3 = keys[min(i + 2, n)]
    return 0.5 * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t * t + (3 * p1 - p0 - 3 * p2 + p3) * t * t * t)
}

/** Every point the camera keeps in view, in world space: the body's skin and the gear it lifts. */
fun Poser.framed(block: (Double, Double, Double) -> Unit) {
    skin(Bone.entries, block = block)
    for (g in gear) if (g.held) g.mesh.each(g.frame, g.sx, g.sy, g.sz, block)
}

/** Where the body's weight rests: over the planted feet, or under the hands when it hangs from (or presses on) them. */
private fun Poser.support(): V? = when {
    feet > 0 -> stance * (1.0 / feet)
    closed(L) && closed(R) -> (hand(L) + hand(R)) * 0.5
    else -> null
}

private const val STEPS = 16

/**
 * One exercise or stretch. [build] poses the figure at a progress d from 0 (the start position) to 1 (the far end of the
 * movement) and [tempo] times a rep. [balance] moves the body so the centre of mass, load included, stays over the feet (or
 * under the hands of a hang); [ground] lowers the body until its lowest point rests on the floor. Both are solved once, here.
 * [yaw] is the camera bearing to open on when gear (a wall, a door frame) would hide the body from the usual front-left view.
 */
class Motion(val tempo: Tempo, val balance: Boolean = false, val ground: Boolean = false, val yaw: Float? = null, private val build: Poser.(Double) -> Unit) {
    private val fixes: Array<V>

    /** Per step, the twist at each of the [Figure]'s twisting joints through the move, unbroken and centred on none (zero for the pieces). */
    private val twists = Array(STEPS + 1) { DoubleArray(Figure.JOINTS.size) }

    /** Where the camera looks and how far out it must sit to see the body and what it lifts in every pose, so the view never bobs. */
    val center = FloatArray(3)
    val radius: Float

    /** Poses [p] at progress [d]; [breath] (0..1) lifts the chest a little. */
    fun pose(p: Poser, d: Double, breath: Double = 0.0) {
        val x = d.coerceIn(0.0, 1.0) * STEPS
        val i = min(x.toInt(), STEPS - 1)
        p.breath = breath
        p.shift = lerp(fixes[i], fixes[i + 1], x - i)
        for (j in p.twist.indices) p.twist[j] = lerp(twists[i][j], twists[i + 1][j], x - i)
        p.reset()
        p.build(d)
        p.finish()
    }

    private fun solve(p: Poser, d: Double): V {
        var shift = V.ZERO
        fun at(s: V) { p.breath = 0.0; p.shift = s; p.reset(); p.build(d); p.finish() }
        at(shift)
        if (balance) repeat(8) {
            val over = p.support() ?: return@repeat
            val c = p.com()
            shift += V(over.x - c.x, 0.0, over.z - c.z)
            at(shift)
        }
        if (ground) shift += V(0.0, -p.lowest(), 0.0)
        // The moves were set on the pieces, and a mesh is fuller: where its skin dips into the floor, the whole pose rises out
        // of it, the limbs still reaching for what they hold. Hands and feet are left out, as they are set on the floor, and
        // so is a pose the limbs hold up, which a rise would leave hanging from them. A grounded move already rests its skin
        // on the floor, and a dip under a millimetre is not worth solving the pose again.
        if (Figure.on && !ground) for (k in 0..1) {
            val near = SETTLES.filter { p.near(it) }
            if (near.isEmpty()) break
            val sink = -p.lowest(near)
            if (sink <= 0.001) break
            val held = p.strain
            at(shift + V(0.0, sink, 0.0))
            if (p.strain > held + 0.001) { at(shift); break }
            shift += V(0.0, sink, 0.0)
        }
        return shift
    }

    init {
        val p = Poser()
        fixes = Array(STEPS + 1) { k ->
            solve(p, k.toDouble() / STEPS).also { if (Figure.on) for (j in p.twist.indices) twists[k][j] = Figure.twist(p, j)?.twist ?: 0.0 }
        }
        for (j in p.twist.indices) {
            for (k in 1..STEPS) twists[k][j] += 360 * kotlin.math.round((twists[k - 1][j] - twists[k][j]) / 360)
            val mid = (twists.minOf { it[j] } + twists.maxOf { it[j] }) / 2
            for (t in twists) t[j] -= 360 * kotlin.math.round(mid / 360)
        }
        // Five poses through the move, posed once: their box centres the view, and their point farthest from it sizes it.
        var pts = DoubleArray(3 shl 14)
        var n = 0
        for (k in 0..4) {
            pose(p, k / 4.0)
            p.framed { x, y, z ->
                if (n + 3 > pts.size) pts = pts.copyOf(pts.size * 2)
                pts[n++] = x; pts[n++] = y; pts[n++] = z
            }
        }
        val lo = DoubleArray(3) { Double.MAX_VALUE }
        val hi = DoubleArray(3) { -Double.MAX_VALUE }
        for (i in 0 until n) { val k = i % 3; lo[k] = minOf(lo[k], pts[i]); hi[k] = maxOf(hi[k], pts[i]) }
        for (k in 0..2) center[k] = ((lo[k] + hi[k]) / 2).toFloat()
        var far = 0.0
        for (i in 0 until n step 3) {
            val dx = pts[i] - center[0]; val dy = pts[i + 1] - center[1]; val dz = pts[i + 2] - center[2]
            far = maxOf(far, sqrt(dx * dx + dy * dy + dz * dz))
        }
        radius = (far + 0.04).toFloat()
    }
}

/** The hip height standing tall with the knees just short of locked. */
internal const val STAND = 0.93

/** Both feet flat, the balls of the feet [width] apart and [ahead] of the hips, toes turned out [out] degrees. */
internal fun Poser.feet(width: Double = 0.26, ahead: Double = 0.1, out: Double = 8.0, rise: Double = 0.0) {
    for (s in SIDES) plant(s, V(s * width / 2, 0.0, ahead), out, rise)
}

/** A bar level across the body at [bar], held [width] apart (between the fists); [palm] and [elbow] are given for the left arm and mirrored. */
internal fun Poser.hold(bar: V, width: Double, palm: V, elbow: V) {
    for (s in SIDES) grip(s, bar + V(s * width / 2, 0.0, 0.0), V(s * elbow.x, elbow.y, elbow.z), V(s * palm.x, palm.y, palm.z))
}

/** How far below the shoulders straight arms hold a bar [width] wide that sits [ahead] of them. */
internal fun drop(width: Double, ahead: Double = 0.0): Double {
    val out = width / 2 - Build.SHOULDER
    return sqrt(Build.ARM * Build.ARM - out * out - ahead * ahead) - 0.003
}

/**
 * Pitches the trunk so that, with the hips at [hip], the shoulders land on [sh] (both before any balance shift), the spine
 * bent as given. Distances that don't match the trunk's length are taken up at the hips, which stay on the line toward [hip].
 */
internal fun Poser.trunk(hip: V, sh: V, waist: Double = 0.0, chest: Double = 0.0, side: Double = 0.0, twist: Double = 0.0) {
    pelvis(hip)
    spine(waist, chest, side, twist)
    val q = shoulders() - this[Bone.PELVIS].o
    val w = sh - hip
    pelvis(hip, (atan2(w.z, w.y) - atan2(q.z, q.y)) / DEG)
    spine(waist, chest, side, twist)
    nudge(sh + shift - shoulders())
}

/** How far the hips sit from the shoulders with the spine bent as given. */
internal fun Poser.trunkLength(waist: Double = 0.0, chest: Double = 0.0): Double {
    pelvis(V.ZERO)
    spine(waist, chest)
    return (shoulders() - this[Bone.PELVIS].o).len
}

/** Lowers or lifts the trunk so the lowest point of [bones] rests at height [y] (a bench or seat top). */
internal fun Poser.rest(y: Double, bones: List<Bone> = Bone.TRUNK) = nudge(V(0.0, y + shift.y - lowest(bones), 0.0))

/** The bones whose skin a [Motion] lifts out of the floor: all but the hands and feet, which the moves set on it. */
private val SETTLES = Bone.entries.filter { b -> SIDES.none { b == Bone.hand(it) || b == Bone.foot(it) || b == Bone.toes(it) } }

/** Whether [b]'s skin might reach the floor: bent flesh strays a little past where it reaches at rest. */
private fun Poser.near(b: Bone) = has(b) && this[b].let { f -> minOf(f.o.y, f.at(0.0, Figure.length(b), 0.0).y) } < Figure.reach(b) + 0.05

object Motions {
    private val defs: Map<String, () -> Motion> = LIFTS + ACCESSORIES + BODYWEIGHT + STRETCHES

    private fun generic() = Motion(Tempo(1.4, 0.6, 1.4, 0.8), balance = true) { d ->
        pelvis(V(0.0, STAND, 0.0), pitch = 4.0)
        spine(waist = -8.0, chest = 5.0)
        for (s in SIDES) arm(s, raise = 10 + 70 * d, plane = 90.0, elbow = 10.0)
        feet()
    }

    /** Solved per figure and body, since the [Figure] mesh rests on the floor and fills the camera's view differently from the pieces. */
    private val made = java.util.concurrent.ConcurrentHashMap<Pair<String, String?>, Motion>()

    /** True when [id] has its own motion rather than the generic one. */
    fun known(id: String) = id in defs

    /** [id] with case, separators and a plural "s" on each word dropped, so "pull_ups", "Pull-ups" and "pullup" all meet. */
    private fun fold(id: String) = id.lowercase().split(Regex("[^a-z0-9]+")).joinToString("") { it.removeSuffix("s") }

    private val folded = defs.keys.associateBy(::fold)

    /** The id of [id]'s own motion, or of the known exercise a custom id spells differently (a custom "pull_ups" is "pull_up"); null when there is none. */
    fun resolve(id: String): String? = id.takeIf { it in defs } ?: folded[fold(id)]

    /** The motion for an exercise or stretch id; an unknown id gets a generic arm raise. Safe off the main thread: two callers may both solve one, and the first kept wins. */
    fun of(id: String): Motion = (id to Figure.shape.takeIf { Figure.on }).let { k -> made[k] ?: (defs[id]?.invoke() ?: generic()).let { made.putIfAbsent(k, it) ?: it } }

    /** The motion for a stretch. */
    fun of(move: StretchMove): Motion = of(move.id)

    /**
     * A dynamic stretch's rep: the catalog's own [StretchMove.pacing], else its motion's: easing out is the stretch, the pause
     * at the far end the hold, and easing back with the rest at the start the release. A flow is one phase. A still stretch's
     * motion is slowed to follow while paused, so played as reps it takes [Pacing.DYNAMIC].
     */
    fun pacing(move: StretchMove): Pacing = move.pacing ?: of(move).tempo.let {
        when {
            it === HOLD_TEMPO -> Pacing.DYNAMIC
            it.flows -> Pacing(it.period, 0.0, 0.0)
            else -> Pacing(it.out, it.pause, it.back + it.rest)
        }
    }

    /**
     * Seconds into [move]'s motion at [at] in its reps, phase by phase: the stretch eases out, the hold stays at the far end
     * and the release eases back, however long the pacing makes each.
     */
    fun motionSec(move: StretchMove, at: RepPosition): Double {
        val t = of(move).tempo
        val parts = if (t.flows) listOf(t.period, 0.0, 0.0) else listOf(t.out, t.pause, t.back + t.rest)
        return at.rep * t.period + parts.take(at.phase.ordinal).sum() + at.phaseFraction * parts[at.phase.ordinal]
    }
}
