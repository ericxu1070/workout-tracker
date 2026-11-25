package dev.gzclp.tracker.form

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** A closed mesh in its part's frame: positions and unit normals (x, y, z per vertex) and triangles wound counter-clockwise seen from outside. */
class Mesh(val pos: FloatArray, val norm: FloatArray, val tri: ShortArray) {
    val verts: Int get() = pos.size / 3
    val tris: Int get() = tri.size / 3

    /** For a [loft]: its cross-sections, to tell a point inside it from one outside. */
    val profile by lazy { Profile(this) }

    /**
     * Each edge once as six ints: one side's two vertices and triangle, then the other side's (-1 for an open edge).
     * Faces that only share a corner's position (a crisp crease) still share the edge.
     */
    val edges: IntArray

    init {
        val weld = HashMap<Long, Int>()
        val id = IntArray(verts) { i ->
            val k = (pos[i * 3] * 1e4).roundToLong() * 4_000_003L * 4_000_003L + (pos[i * 3 + 1] * 1e4).roundToLong() * 4_000_003L + (pos[i * 3 + 2] * 1e4).roundToLong()
            weld.getOrPut(k) { i }
        }
        val slot = HashMap<Long, Int>()
        val out = ArrayList<Int>()
        for (t in 0 until tris) for (e in 0..2) {
            val a = tri[t * 3 + e].toInt()
            val b = tri[t * 3 + (e + 1) % 3].toInt()
            val key = minOf(id[a], id[b]).toLong() * verts + maxOf(id[a], id[b])
            val s = slot[key]
            if (s == null) {
                slot[key] = out.size
                out += listOf(a, b, t, -1, -1, -1)
            } else {
                out[s + 3] = b; out[s + 4] = a; out[s + 5] = t
            }
        }
        edges = out.toIntArray()
    }
}

/** A cross-section at [y]: an ellipse [a] across (half-width along x) and [b] deep (half-depth along z), its centre moved [cz] forward. */
class Ring(val y: Double, val a: Double, val b: Double, val cz: Double = 0.0)

/** A loft's cross-section at each ring height: the polygon its ring's corners trace, around the ellipse they lie on. */
class Profile(mesh: Mesh) {
    private val ys: DoubleArray
    private val ax: DoubleArray
    private val az: DoubleArray
    private val cz: DoubleArray
    private val step: Double
    private val apothem: Double

    init {
        // A loft adds each ring's corners in order around it from +x, so a level's first two give its ellipse.
        val levels = (0 until mesh.verts).groupBy { mesh.pos[it * 3 + 1] }.toSortedMap().values
        step = 2 * PI / levels.maxOf { it.size }
        apothem = cos(step / 2)
        fun c(i: Int, k: Int) = mesh.pos[i * 3 + k].toDouble()
        ys = levels.map { c(it[0], 1) }.toDoubleArray()
        ax = levels.map { c(it[0], 0) }.toDoubleArray()
        cz = levels.map { c(it[0], 2) }.toDoubleArray()
        az = levels.map { if (it.size > 1) (c(it[1], 2) - c(it[0], 2)) / sin(step) else 0.0 }.toDoubleArray()
    }

    /** Below 0 inside the surface, 0 on it and above 0 outside (as a share of its radius there), for a point in the loft's coordinates. */
    fun inside(x: Double, y: Double, z: Double): Double {
        if (y <= ys[0] || y >= ys[ys.size - 1]) return 1.0
        var i = ys.binarySearch(y)
        if (i < 0) i = -i - 2
        val t = (y - ys[i]) / (ys[i + 1] - ys[i])
        val u = x / lerp(ax[i], ax[i + 1], t)
        val w = (z - lerp(cz[i], cz[i + 1], t)) / lerp(az[i], az[i + 1], t)
        val side = (floor(atan2(w, u) / step) + 0.5) * step
        return (u * cos(side) + w * sin(side)) / apothem - 1
    }
}

private class Builder {
    private val p = ArrayList<Double>()
    private val t = ArrayList<Int>()
    val size get() = p.size / 3

    fun v(x: Double, y: Double, z: Double): Int { p += x; p += y; p += z; return size - 1 }
    fun tri(a: Int, b: Int, c: Int) { t += a; t += b; t += c }

    /** Normals come from the faces around each vertex, weighted by area, so split vertices keep a crisp edge. */
    fun build(): Mesh {
        val n = DoubleArray(p.size)
        for (i in t.indices step 3) {
            val a = t[i] * 3; val b = t[i + 1] * 3; val c = t[i + 2] * 3
            val ux = p[b] - p[a]; val uy = p[b + 1] - p[a + 1]; val uz = p[b + 2] - p[a + 2]
            val vx = p[c] - p[a]; val vy = p[c + 1] - p[a + 1]; val vz = p[c + 2] - p[a + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            for (k in intArrayOf(a, b, c)) { n[k] += nx; n[k + 1] += ny; n[k + 2] += nz }
        }
        val norm = FloatArray(p.size)
        for (i in 0 until size) {
            val l = sqrt(n[i * 3] * n[i * 3] + n[i * 3 + 1] * n[i * 3 + 1] + n[i * 3 + 2] * n[i * 3 + 2]).coerceAtLeast(1e-12)
            for (k in 0..2) norm[i * 3 + k] = (n[i * 3 + k] / l).toFloat()
        }
        return Mesh(FloatArray(p.size) { p[it].toFloat() }, norm, ShortArray(t.size) { t[it].toShort() })
    }
}

/** A smooth closed shape through [rings] (bottom to top), domed [base] metres below the first and [tip] above the last. */
fun loft(sides: Int, base: Double, tip: Double, vararg rings: Ring): Mesh {
    val m = 3
    val first = rings.first()
    val last = rings.last()
    val all = ArrayList<Ring>()
    for (k in m downTo 1) { val f = k * PI / (2 * m + 2); all += Ring(first.y - base * sin(f), first.a * cos(f), first.b * cos(f), first.cz) }
    all.addAll(rings)
    for (k in 1..m) { val f = k * PI / (2 * m + 2); all += Ring(last.y + tip * sin(f), last.a * cos(f), last.b * cos(f), last.cz) }
    val b = Builder()
    val bottom = b.v(0.0, first.y - base, first.cz)
    for (r in all) for (k in 0 until sides) {
        val th = 2 * PI * k / sides
        b.v(r.a * cos(th), r.y, r.cz + r.b * sin(th))
    }
    val top = b.v(0.0, last.y + tip, last.cz)
    fun at(i: Int, k: Int) = 1 + i * sides + k % sides
    for (k in 0 until sides) b.tri(bottom, at(0, k), at(0, k + 1))
    for (i in 0 until all.size - 1) for (k in 0 until sides) {
        b.tri(at(i, k), at(i + 1, k), at(i, k + 1))
        b.tri(at(i, k + 1), at(i + 1, k), at(i + 1, k + 1))
    }
    for (k in 0 until sides) b.tri(top, at(all.size - 1, k + 1), at(all.size - 1, k))
    return b.build()
}

/** Where a [Skin]'s next bone starts with the chain laid straight, and how far either side of it the skin bends. */
class Joint(val at: V, val blend: Double)

/**
 * A loft bent by a chain of bones, so its joints bend without a seam. Its coordinates are the chain's laid straight, with the
 * first bone's origin at [root]. Each ring follows the bone it sits on and, within a joint's blend, turns part of the way to
 * the next bone about that joint, so a bend keeps the limb's thickness.
 */
class Skin(val mesh: Mesh, private val root: V, private vararg val joints: Joint) {
    /** The rings' heights; per height, the bone it follows and how far it turns toward the next ([lead] is the nearer of the two). */
    private val ys = (0 until mesh.verts).map { mesh.pos[it * 3 + 1].toDouble() }.distinct().sorted().toDoubleArray()
    private val bone = IntArray(ys.size)
    private val turn = DoubleArray(ys.size)
    private val level = IntArray(mesh.verts) { ys.binarySearch(mesh.pos[it * 3 + 1].toDouble()) }
    val lead: IntArray
    val size get() = joints.size + 1

    init {
        for ((l, y) in ys.withIndex()) {
            var k = 0
            while (k < joints.size && y >= joints[k].at.y + joints[k].blend) k++
            bone[l] = k
            if (k < joints.size) turn[l] = ((y - joints[k].at.y + joints[k].blend) / (2 * joints[k].blend)).coerceIn(0.0, 1.0)
        }
        lead = IntArray(mesh.verts) { if (turn[level[it]] > 0.5) bone[level[it]] + 1 else bone[level[it]] }
    }

    /** Where the chain's [k]th bone starts, in the skin's coordinates. */
    fun origin(k: Int) = if (k == 0) root else joints[k - 1].at

    /** Bends the skin to [p]'s [bones] (one per joint and one more): each vertex's world position into [pos] and, when given, its normal into [norm]. */
    fun pose(p: Poser, bones: Array<Bone>, pos: FloatArray, norm: FloatArray? = null) {
        val placed = Array(ys.size) { l ->
            val k = bone[l]
            val a = p[bones[k]]
            val o = origin(k)
            if (turn[l] == 0.0) return@Array a.moved(a.at(-o.x, -o.y, -o.z))
            val b = p[bones[k + 1]]
            val j = joints[k].at
            // The turn from a's axes to b's as an axis and angle; this ring takes its share of it about the joint.
            val v = ((a.x cross b.x) + (a.y cross b.y) + (a.z cross b.z)) * 0.5
            val f = a.turn(v.unit(a.x), turn[l] * atan2(v.len, ((a.x dot b.x) + (a.y dot b.y) + (a.z dot b.z) - 1) / 2) / DEG)
            val pivot = lerp(a.at(j.x - o.x, j.y - o.y, j.z - o.z), b.o, turn[l])
            f.moved(pivot - f.dir(j.x, j.y, j.z))
        }
        val v = mesh.pos; val n = mesh.norm
        for (i in 0 until mesh.verts) {
            val f = placed[level[i]]
            val x = v[i * 3]; val y = v[i * 3 + 1]; val z = v[i * 3 + 2]
            pos[i * 3] = (f.o.x + f.x.x * x + f.y.x * y + f.z.x * z).toFloat()
            pos[i * 3 + 1] = (f.o.y + f.x.y * x + f.y.y * y + f.z.y * z).toFloat()
            pos[i * 3 + 2] = (f.o.z + f.x.z * x + f.y.z * y + f.z.z * z).toFloat()
            if (norm == null) continue
            val nx = n[i * 3]; val ny = n[i * 3 + 1]; val nz = n[i * 3 + 2]
            norm[i * 3] = (f.x.x * nx + f.y.x * ny + f.z.x * nz).toFloat()
            norm[i * 3 + 1] = (f.x.y * nx + f.y.y * ny + f.z.y * nz).toFloat()
            norm[i * 3 + 2] = (f.x.z * nx + f.y.z * ny + f.z.z * nz).toFloat()
        }
    }
}

/** The figure's shapes, sized for the 1.75 m build in [Build]: the bent chains as [Skin]s, the rest in each bone's frame. */
object Body {
    /** The pelvis, waist, chest and neck, in the pelvis's coordinates with the spine straight. */
    val torso = Skin(
        loft(
            16, 0.045, 0.02,
            Ring(-0.07, 0.13, 0.095, -0.03), Ring(-0.03, 0.165, 0.11, -0.025), Ring(0.0, 0.1625, 0.1075, -0.02), Ring(0.03, 0.16, 0.105, -0.015),
            Ring(0.06, 0.152, 0.1, -0.01), Ring(0.085, 0.145, 0.095, -0.009), Ring(0.1, 0.141, 0.093, -0.008),
            Ring(0.115, 0.138, 0.092, -0.007), Ring(0.14, 0.136, 0.091, -0.006), Ring(0.17, 0.134, 0.09, -0.005),
            Ring(0.21, 0.138, 0.093, -0.003), Ring(0.24, 0.143, 0.097, -0.001), Ring(0.26, 0.146, 0.099, 0.0),
            Ring(0.28, 0.149, 0.101, 0.001), Ring(0.3, 0.153, 0.105, 0.003), Ring(0.32, 0.156, 0.108, 0.005),
            Ring(0.35, 0.16, 0.112, 0.008), Ring(0.43, 0.172, 0.12, 0.01), Ring(0.49, 0.168, 0.11, 0.0),
            Ring(0.53, 0.142, 0.09, -0.016), Ring(0.55, 0.125, 0.08, -0.02), Ring(0.565, 0.1, 0.072, -0.02),
            Ring(0.58, 0.07, 0.064, -0.017), Ring(0.6, 0.054, 0.058, -0.014), Ring(0.64, 0.05, 0.055, -0.01), Ring(0.67, 0.05, 0.052, 0.0),
        ),
        V.ZERO,
        Joint(V(0.0, Build.PELVIS, -0.01), 0.05), Joint(V(0.0, Build.PELVIS + Build.WAIST, -0.01), 0.05),
        Joint(V(0.0, Build.PELVIS + Build.WAIST + Build.CHEST, -0.01), 0.03),
    )

    /** The thigh and shin, from the hip down. */
    val leg = Skin(
        loft(
            12, 0.04, 0.02,
            Ring(0.02, 0.055, 0.057), Ring(0.04, 0.07, 0.074, 0.003), Ring(0.06, 0.08, 0.085, 0.005), Ring(0.09, 0.0775, 0.0825, 0.006),
            Ring(0.12, 0.075, 0.08, 0.0065), Ring(0.15, 0.0725, 0.0775, 0.007), Ring(0.18, 0.07, 0.075, 0.008), Ring(0.31, 0.058, 0.06, 0.006),
            Ring(0.38, 0.052, 0.052, 0.004), Ring(0.405, 0.05, 0.05, 0.003), Ring(0.43, 0.05, 0.05, 0.002), Ring(0.455, 0.049, 0.05, 0.0),
            Ring(0.48, 0.048, 0.052, -0.004), Ring(0.56, 0.05, 0.058, -0.014), Ring(0.68, 0.04, 0.043, -0.008), Ring(0.8, 0.03, 0.028, -0.002),
            Ring(0.855, 0.034, 0.03),
        ),
        V.ZERO, Joint(V(0.0, Build.THIGH, 0.0), 0.05),
    )

    private val upper = arrayOf(
        Ring(-0.02, 0.052, 0.05), Ring(0.05, 0.056, 0.052), Ring(0.13, 0.046, 0.048, 0.006), Ring(0.22, 0.041, 0.042, 0.004),
        Ring(0.26, 0.039, 0.039, 0.002), Ring(0.28, 0.038, 0.037), Ring(0.3, 0.037, 0.036, -0.002), Ring(0.32, 0.039, 0.036, -0.002),
        Ring(0.34, 0.041, 0.036), Ring(0.36, 0.043, 0.036, 0.003), Ring(0.45, 0.035, 0.028, 0.002), Ring(0.53, 0.03, 0.021),
    )

    private fun arm(vararg hand: Ring) =
        Skin(loft(12, 0.045, 0.02, *upper, *hand), V.ZERO, Joint(V(0.0, Build.UPPER_ARM, 0.0), 0.04), Joint(V(0.0, Build.UPPER_ARM + Build.FOREARM, 0.0), 0.02))

    /** The upper arm, forearm and a closed hand, from the shoulder down. */
    val fist = arm(Ring(0.55, 0.028, 0.02), Ring(0.565, 0.035, 0.023, 0.003), Ring(0.585, 0.043, 0.027, 0.006), Ring(0.625, 0.046, 0.032, 0.01), Ring(0.65, 0.042, 0.03, 0.012))

    /** The upper arm, forearm and an open hand, palm toward its frame's z. */
    val flat = arm(Ring(0.55, 0.028, 0.018, 0.005), Ring(0.565, 0.033, 0.017, 0.005), Ring(0.6, 0.043, 0.016, 0.005), Ring(0.65, 0.044, 0.012, 0.005), Ring(0.715, 0.035, 0.008, 0.005))

    private val head = loft(14, 0.02, 0.055, Ring(-0.045, 0.035, 0.03, 0.075), Ring(-0.02, 0.058, 0.062, 0.045), Ring(0.03, 0.072, 0.092, 0.02), Ring(0.08, 0.077, 0.1, 0.008), Ring(0.12, 0.072, 0.095, -0.004))
    private val foot = loft(10, 0.02, 0.012, Ring(-0.055, 0.03, 0.03, -0.045), Ring(-0.025, 0.033, 0.04, -0.035), Ring(0.035, 0.038, 0.045, -0.03), Ring(0.1, 0.045, 0.028, -0.047), Ring(0.145, 0.047, 0.02, -0.055))
    private val toes = loft(10, 0.008, 0.015, Ring(-0.01, 0.046, 0.017), Ring(0.05, 0.042, 0.013, -0.003))

    /** A rod of radius 1 from y = 0 to y = 1 with flat ends. */
    val rod: Mesh = Builder().run {
        val n = 16
        fun ring(y: Double) = (0 until n).map { v(cos(2 * PI * it / n), y, sin(2 * PI * it / n)) }
        val lo = ring(0.0); val hi = ring(1.0)
        for (k in 0 until n) { val j = (k + 1) % n; tri(lo[k], hi[k], lo[j]); tri(lo[j], hi[k], hi[j]) }
        val b = v(0.0, 0.0, 0.0); val capLo = ring(0.0)
        val t = v(0.0, 1.0, 0.0); val capHi = ring(1.0)
        for (k in 0 until n) { val j = (k + 1) % n; tri(b, capLo[k], capLo[j]); tri(t, capHi[j], capHi[k]) }
        build()
    }

    /** A cube of side 1 centred on its frame, each face with its own corners so the edges stay crisp. */
    val box: Mesh = Builder().run {
        val faces = listOf(
            doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0), doubleArrayOf(-1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0), doubleArrayOf(0.0, -1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0),
            doubleArrayOf(0.0, 0.0, 1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0), doubleArrayOf(0.0, 0.0, -1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0),
        )
        for (f in faces) {
            val c = IntArray(4) { k ->
                val su = if (k == 1 || k == 2) 0.5 else -0.5
                val sv = if (k >= 2) 0.5 else -0.5
                v(f[0] / 2 + f[3] * su + f[6] * sv, f[1] / 2 + f[4] * su + f[7] * sv, f[2] / 2 + f[5] * su + f[8] * sv)
            }
            tri(c[0], c[1], c[2]); tri(c[0], c[2], c[3])
        }
        build()
    }

    /** A ball of radius 1. */
    val ball = loft(12, 1.0, 1.0, Ring(0.0, 1.0, 1.0))

    /** The shape of a bone that doesn't bend with a [Skin] (the head, a foot or its toes); null for one that does. */
    fun of(b: Bone): Mesh? = when (b) {
        Bone.HEAD -> head
        Bone.FOOT_L, Bone.FOOT_R -> foot
        Bone.TOES_L, Bone.TOES_R -> toes
        else -> null
    }
}

/**
 * The part of a surface that follows [bone]: its points between rest heights [near] may run into another surface, and its
 * shape between [solid] (where it moves rigidly with the bone) is what another surface is cut against.
 */
class Side(val bone: Bone, near: ClosedFloatingPointRange<Double>, solid: ClosedFloatingPointRange<Double>) {
    val lo = near.start
    val hi = near.endInclusive
    val from = solid.start
    val to = solid.endInclusive
}

/** Two surfaces that run into each other at a joint; each is cut where it enters the other, so they meet in one clean crease. */
class Seam(val a: Side, val b: Side)

val SEAMS = SIDES.flatMap { s ->
    listOf(
        Seam(Side(Bone.thigh(s), -1.0..0.3, -1.0..0.36), Side(Bone.PELVIS, -1.0..0.14, -1.0..0.1)),
        Seam(Side(Bone.thigh(s), -1.0..0.3, -1.0..0.36), Side(Bone.WAIST, 0.1..0.33, 0.1..0.28)),
        Seam(Side(Bone.thigh(s), -1.0..0.38, -1.0..0.38), Side(Bone.shin(s), 0.48..1.0, 0.48..1.0)),
        Seam(Side(Bone.CHEST, 0.36..0.66, 0.33..0.6), Side(Bone.arm(s), -1.0..0.18, -1.0..0.24)),
        Seam(Side(Bone.shin(s), 0.7..1.0, 0.48..1.0), Side(Bone.foot(s), -1.0..1.0, -1.0..1.0)),
        Seam(Side(Bone.toes(s), -1.0..1.0, -1.0..1.0), Side(Bone.foot(s), -1.0..1.0, -1.0..1.0)),
    )
} + Seam(Side(Bone.NECK, 0.56..1.0, 0.59..1.0), Side(Bone.HEAD, -1.0..1.0, -1.0..1.0))

/** What a piece of equipment is made of, which sets its shade. */
enum class Tone { METAL, RUBBER, PAD }

/** A piece of equipment: [mesh] stretched by [sx], [sy], [sz] along [frame]'s axes; [held] when the figure lifts it, so the camera keeps it in view. */
class Placed(val mesh: Mesh, val frame: Frame, val sx: Double, val sy: Double, val sz: Double, val tone: Tone) {
    var held = false

    /** Which length of its item this is, from 0: a long rod or box is cut into several. */
    var piece = 0
}

/** Marks the gear [place] adds as held. */
private inline fun Poser.held(place: () -> Unit) {
    val from = gear.size
    place()
    for (i in from until gear.size) gear[i].held = true
}

/** Every point of [m] placed by [f] and the stretch, in world space. */
inline fun Mesh.each(f: Frame, sx: Double = 1.0, sy: Double = 1.0, sz: Double = 1.0, block: (Double, Double, Double) -> Unit) {
    for (i in 0 until verts) {
        val x = pos[i * 3] * sx; val y = pos[i * 3 + 1] * sy; val z = pos[i * 3 + 2] * sz
        block(f.o.x + f.x.x * x + f.y.x * y + f.z.x * z, f.o.y + f.x.y * x + f.y.y * y + f.z.y * z, f.o.z + f.x.z * x + f.y.z * y + f.z.z * z)
    }
}

/** Long pieces are cut into lengths of at most this, so each sorts by its own depth. */
private const val PIECE = 0.42

/** A rod of radius [r] from [a] to [b]. */
fun Poser.rod(a: V, b: V, r: Double, tone: Tone = Tone.METAL) {
    val len = (b - a).len
    val n = max(1, ceil(len / PIECE).toInt())
    val f = Frame.along(a, b - a, V.FWD, V.UP)
    for (i in 0 until n) gear += Placed(Body.rod, f.moved(lerp(a, b, i.toDouble() / n)), r, len / n, r, tone).also { it.piece = i }
}

/** A disc (a plate, a pulley) of radius [r] and [thick]ness centred on [c], its face toward [axis]. */
fun Poser.disc(c: V, axis: V, r: Double, thick: Double, tone: Tone = Tone.RUBBER) {
    val u = axis.unit()
    gear += Placed(Body.rod, Frame.along(c - u * (thick / 2), u, V.FWD, V.UP), r, thick, r, tone)
}

/** A box [w] by [h] by [d] along [f]'s x, y and z, centred on [f]'s origin; long boxes are cut along their length. */
fun Poser.block(f: Frame, w: Double, h: Double, d: Double, tone: Tone = Tone.PAD) {
    val n = max(1, ceil(maxOf(w, h, d) / PIECE).toInt())
    val (axis, len) = when (maxOf(w, h, d)) { w -> f.x to w; h -> f.y to h; else -> f.z to d }
    for (i in 0 until n) {
        val c = f.o + axis * (len * ((i + 0.5) / n - 0.5))
        gear += Placed(Body.box, f.moved(c), if (axis == f.x) w / n else w, if (axis == f.y) h / n else h, if (axis == f.z) d / n else d, tone).also { it.piece = i }
    }
}

/** An upright box on the floor: [w] across, [h] tall, [d] deep, its top centre at ([x], [h], [z]). */
fun Poser.block(x: Double, z: Double, w: Double, h: Double, d: Double, tone: Tone = Tone.PAD) =
    block(Frame.WORLD.moved(V(x, h / 2, z)), w, h, d, tone)

fun Poser.ball(c: V, r: Double, tone: Tone = Tone.METAL) { gear += Placed(Body.ball, Frame.WORLD.moved(c), r, r, r, tone) }

/** A barbell level across the figure centred on [mid]: by default an Olympic bar (2.2 m, a 28 mm shaft), [half] the shaft and [sleeve] each end, loaded with plates of radius [plate]. */
fun Poser.barbell(mid: V, plate: Double = 0.225, half: Double = 0.655, sleeve: Double = 0.445) = held {
    rod(mid + V(-half, 0.0, 0.0), mid + V(half, 0.0, 0.0), 0.014)
    for (s in intArrayOf(L, R)) {
        val o = mid + V(s * half, 0.0, 0.0)
        disc(o + V(s * 0.012, 0.0, 0.0), V.LEFT, 0.032, 0.024, Tone.METAL)
        rod(o, o + V(s * sleeve, 0.0, 0.0), 0.025)
        disc(o + V(s * 0.075, 0.0, 0.0), V.LEFT, plate, 0.09)
        disc(o + V(s * 0.135, 0.0, 0.0), V.LEFT, 0.035, 0.03, Tone.METAL)
    }
}

/** A dumbbell whose handle runs through [c] along [axis]. */
fun Poser.dumbbell(c: V, axis: V, head: Double = 0.058) = held {
    val u = axis.unit()
    rod(c - u * 0.07, c + u * 0.07, 0.016)
    disc(c + u * 0.105, u, head, 0.07); disc(c - u * 0.105, u, head, 0.07)
}

/** A cable from [a] to [b]. */
fun Poser.cable(a: V, b: V) = rod(a, b, 0.005)
