package dev.gzclp.tracker.form

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The figure as one continuous mesh skinned to the 19 bones, drawn in place of [Body]'s pieces while [on]: MakeHuman's CC0
 * base mesh, made by tools/body-mesh.py (see NOTICE). It comes in the rig's own space and is fitted once onto [REST], a pose
 * close to the rig's own: each bone's part of it moves onto that bone and stretches to its length. Each frame every vertex
 * follows its bones (up to four) by linear blend skinning, and a closed hand swaps in a fist.
 */
object Figure {
    /** Draws and solves the figure as this mesh rather than the pieces; off by default while it is on trial. */
    var on = false

    val mesh: Mesh

    /** Each vertex's bone it follows most, for [Poser.skin]. */
    val lead: IntArray

    private val bones: ByteArray
    private val weights: FloatArray
    private val bind: Array<Frame>

    /** Per vertex, its place among the hand vertices (-1 off the hands), and per hand vertex, which side it is on and its fist position and normal. */
    private val slot: IntArray
    private val left: BooleanArray
    private val fist: FloatArray
    private val fistNorm: FloatArray

    /** The rig's bind pose as Poser angles: arms 40° out, elbows bent 47°, palms toward the thighs, legs a little apart. */
    val REST = Poser().apply {
        pelvis(V(0.0, STAND, 0.0))
        for (s in SIDES) {
            arm(s, raise = 39.6, plane = 0.0, twist = 8.6, elbow = 46.9, pronate = 96.4, wrist = 14.0)
            leg(s, raise = 7.0, plane = 22.5, knee = 8.1)
        }
    }

    /** How long each bone is in Poser, from its joint to the next; 0 for an end bone, which keeps its size. */
    private fun length(b: Bone) = when (b) {
        Bone.PELVIS -> Build.PELVIS
        Bone.WAIST -> Build.WAIST
        Bone.CHEST -> Build.CHEST
        Bone.NECK -> Build.NECK
        Bone.ARM_L, Bone.ARM_R -> Build.UPPER_ARM
        Bone.FOREARM_L, Bone.FOREARM_R -> Build.FOREARM
        Bone.THIGH_L, Bone.THIGH_R -> Build.THIGH
        Bone.SHIN_L, Bone.SHIN_R -> Build.SHIN
        else -> 0.0
    }

    init {
        val buf = ByteBuffer.wrap(Figure::class.java.getResourceAsStream("body.bin")!!.use { it.readBytes() }).order(ByteOrder.LITTLE_ENDIAN)
        check(buf.int == 1) { "body.bin version" }
        val nv = buf.int; val nt = buf.int; val nh = buf.int
        fun v() = V(buf.float.toDouble(), buf.float.toDouble(), buf.float.toDouble())
        val src = Array(Bone.entries.size) { Frame(v(), v(), v(), v()) to buf.float.toDouble() }
        val raw = FloatArray(nv * 3) { buf.float }
        bones = ByteArray(nv * 4).also { buf.get(it) }
        weights = FloatArray(nv * 4) { (buf.get().toInt() and 0xFF) / 255f }
        val tri = ShortArray(nt * 3) { buf.short }
        val hand = IntArray(nh) { buf.short.toInt() and 0xFFFF }
        val rawFist = FloatArray(nh * 3) { buf.float }
        bind = Array(Bone.entries.size) { REST.f[it]!! }

        /** Where the rig's point [i] of [from] lands once each of its bones' parts sits on that bone of [REST]. */
        fun fit(from: FloatArray, at: Int, i: Int, out: FloatArray) {
            val p = V(from[at * 3].toDouble(), from[at * 3 + 1].toDouble(), from[at * 3 + 2].toDouble())
            var q = V.ZERO
            for (k in 0..3) {
                val w = weights[i * 4 + k]
                if (w == 0f) continue
                val b = bones[i * 4 + k].toInt()
                val (s, len) = src[b]
                val d = p - s.o
                val y = d dot s.y
                // The stretch runs from the joint to the next one, so what lies past either end keeps its shape.
                val r = if (len > 0) length(Bone.entries[b]) / len else 1.0
                q += bind[b].at(d dot s.x, y + (r - 1) * y.coerceIn(0.0, len), d dot s.z) * w.toDouble()
            }
            out[at * 3] = q.x.toFloat(); out[at * 3 + 1] = q.y.toFloat(); out[at * 3 + 2] = q.z.toFloat()
        }

        val pos = FloatArray(nv * 3)
        for (i in 0 until nv) fit(raw, i, i, pos)
        lead = IntArray(nv) { i -> bones[i * 4 + (0..3).maxBy { weights[i * 4 + it] }].toInt() }
        wear(pos, Bone.TRUNK.take(3), Bone.PELVIS, Body.torso.mesh.profile, -0.03, 0.53)
        for (s in SIDES) {
            // Twice for the thigh: the second pass pulls its front back onto the loft's, which a deadlift's bar clears by a hair.
            wear(pos, listOf(Bone.thigh(s)), Bone.thigh(s), Body.leg.mesh.profile, 0.04, 0.5)
            wear(pos, listOf(Bone.thigh(s)), Bone.thigh(s), Body.leg.mesh.profile, 0.04, 0.5, Onto.FRONT)
            wear(pos, listOf(Bone.shin(s)), Bone.shin(s), Body.leg.mesh.profile, 0.04, 0.4, Onto.SIDES, along = Build.THIGH)
        }
        mesh = Mesh(pos, normals(pos, tri), tri)
        slot = IntArray(nv) { -1 }
        for ((k, i) in hand.withIndex()) slot[i] = k
        left = BooleanArray(nh) { pos[hand[it] * 3] > 0 }
        fist = FloatArray(nh * 3)
        for ((k, i) in hand.withIndex()) fit(rawFist, k, i, fist)
        val closed = pos.copyOf()
        for ((k, i) in hand.withIndex()) for (c in 0..2) closed[i * 3 + c] = fist[k * 3 + c]
        val n = normals(closed, tri)
        fistNorm = FloatArray(nh * 3) { n[hand[it / 3] * 3 + it % 3] }
    }

    /** Which of a loft's surfaces [wear] moves a part's flesh onto. */
    private enum class Onto {
        FRONT_AND_BACK,
        /** The front only, where the back is flesh of its own (the glutes over the top of a thigh). */
        FRONT,
        SIDES,
    }

    /**
     * Moves the flesh of [group] in [pos] [onto] those surfaces of [loft] (the pieces' part in [frame]'s rest coordinates),
     * level by level from [from] to [to] up it ([along] further up [loft]), fading in and out over [FADE] at the ends.
     * The motions were tuned on the pieces, so a bar across the back or a bench under it has to find the mesh where it found them.
     */
    private fun wear(pos: FloatArray, group: List<Bone>, frame: Bone, loft: Profile, from: Double, to: Double, onto: Onto = Onto.FRONT_AND_BACK, along: Double = 0.0) {
        val f = REST[frame]
        val ids = group.map { it.ordinal }
        val sides = onto == Onto.SIDES
        val local = Array(pos.size / 3) { val d = V(pos[it * 3].toDouble(), pos[it * 3 + 1].toDouble(), pos[it * 3 + 2].toDouble()) - f.o; V(d dot f.x, d dot f.y, d dot f.z) }
        fun across(p: V) = if (sides) p.x else p.z
        // The mesh's own two extremes at each level, from the vertices that follow the group most.
        val n = ((to - from) / STEP).roundToInt() + 1
        val low = DoubleArray(n) { Double.MAX_VALUE }
        val high = DoubleArray(n) { -Double.MAX_VALUE }
        for ((i, p) in local.withIndex()) if (lead[i] in ids) for (k in 0 until n) if (abs(p.y - from - k * STEP) < STEP) {
            low[k] = min(low[k], across(p)); high[k] = max(high[k], across(p))
        }
        for ((i, p) in local.withIndex()) {
            val share = (0..3).sumOf { if (bones[i * 4 + it].toInt() in ids) weights[i * 4 + it].toDouble() else 0.0 } *
                ((p.y - from) / FADE).coerceIn(0.0, 1.0) * ((to - p.y) / FADE).coerceIn(0.0, 1.0)
            if (share == 0.0) continue
            val u = (p.y - from) / STEP
            val k = u.toInt().coerceIn(0, n - 2)
            if (low[k] > high[k] || low[k + 1] > high[k + 1]) continue // a level past the group's own flesh
            val lo = lerp(low[k], low[k + 1], u - k); val hi = lerp(high[k], high[k + 1], u - k)
            val (a, b, cz) = loft.ellipse(p.y + along)
            val t = (across(p) - lo) / (hi - lo)
            val c = when (onto) { Onto.FRONT_AND_BACK -> lerp(cz - b, cz + b, t); Onto.FRONT -> lerp(lo, cz + b, t); Onto.SIDES -> lerp(-a, a, t) }
            val q = if (sides) f.at(p.x + share * (c - p.x), p.y, p.z) else f.at(p.x, p.y, p.z + share * (c - p.z))
            pos[i * 3] = q.x.toFloat(); pos[i * 3 + 1] = q.y.toFloat(); pos[i * 3 + 2] = q.z.toFloat()
        }
    }

    private const val STEP = 0.02
    private const val FADE = 0.06

    /** Each vertex's normal from the faces around it, weighted by their area. */
    private fun normals(pos: FloatArray, tri: ShortArray): FloatArray {
        val n = FloatArray(pos.size)
        for (t in tri.indices step 3) {
            val a = tri[t] * 3; val b = tri[t + 1] * 3; val c = tri[t + 2] * 3
            val ux = pos[b] - pos[a]; val uy = pos[b + 1] - pos[a + 1]; val uz = pos[b + 2] - pos[a + 2]
            val vx = pos[c] - pos[a]; val vy = pos[c + 1] - pos[a + 1]; val vz = pos[c + 2] - pos[a + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            for (k in intArrayOf(a, b, c)) { n[k] += nx; n[k + 1] += ny; n[k + 2] += nz }
        }
        for (i in 0 until n.size / 3) {
            val l = sqrt(n[i * 3] * n[i * 3] + n[i * 3 + 1] * n[i * 3 + 1] + n[i * 3 + 2] * n[i * 3 + 2]).coerceAtLeast(1e-12f)
            for (k in 0..2) n[i * 3 + k] /= l
        }
        return n
    }

    /**
     * Bends the mesh to [p]: each vertex's world position into [pos] and, when given, its normal (any length) into [norm]. Part
     * way through a pose, a vertex follows only the bones [p] has placed so far, and gets NaN when it has none of them. Only
     * the vertices that follow [only] most are written; the rest keep what they had.
     */
    fun pose(p: Poser, pos: FloatArray, norm: FloatArray? = null, only: List<Bone> = Bone.entries) {
        // Per bone, the map from the rest mesh to this pose: its frame now after the inverse of its frame at rest, as a 3×4 matrix.
        val m = FloatArray(Bone.entries.size * 12)
        val placed = BooleanArray(Bone.entries.size) { p.has(Bone.entries[it]) }
        for (b in Bone.entries) if (placed[b.ordinal]) {
            val f = p[b]; val r = bind[b.ordinal]; val o = b.ordinal * 12
            val fx = doubleArrayOf(f.x.x, f.x.y, f.x.z); val fy = doubleArrayOf(f.y.x, f.y.y, f.y.z); val fz = doubleArrayOf(f.z.x, f.z.y, f.z.z)
            val rx = doubleArrayOf(r.x.x, r.x.y, r.x.z); val ry = doubleArrayOf(r.y.x, r.y.y, r.y.z); val rz = doubleArrayOf(r.z.x, r.z.y, r.z.z)
            val ro = doubleArrayOf(r.o.x, r.o.y, r.o.z); val fo = doubleArrayOf(f.o.x, f.o.y, f.o.z)
            for (i in 0..2) {
                var t = fo[i]
                for (j in 0..2) {
                    val e = fx[i] * rx[j] + fy[i] * ry[j] + fz[i] * rz[j]
                    m[o + i * 4 + j] = e.toFloat()
                    t -= e * ro[j]
                }
                m[o + i * 4 + 3] = t.toFloat()
            }
        }
        val closedL = p.closed(L); val closedR = p.closed(R)
        val rest = mesh.pos; val restNorm = mesh.norm
        val a = FloatArray(12)
        val wanted = BooleanArray(Bone.entries.size) { Bone.entries[it] in only }
        for (i in 0 until mesh.verts) {
            if (!wanted[lead[i]]) continue
            a.fill(0f)
            var sum = 0f
            for (k in 0..3) {
                val w = weights[i * 4 + k]
                val b = bones[i * 4 + k].toInt()
                if (w == 0f || !placed[b]) continue
                sum += w
                for (e in 0..11) a[e] += m[b * 12 + e] * w
            }
            if (sum < 0.999f) for (e in 0..11) a[e] /= sum
            val h = slot[i]
            val fisted = h >= 0 && (if (left[h]) closedL else closedR)
            val vs = if (fisted) fist else rest; val ns = if (fisted) fistNorm else restNorm
            val at = if (fisted) h * 3 else i * 3
            val x = vs[at]; val y = vs[at + 1]; val z = vs[at + 2]
            pos[i * 3] = a[0] * x + a[1] * y + a[2] * z + a[3]
            pos[i * 3 + 1] = a[4] * x + a[5] * y + a[6] * z + a[7]
            pos[i * 3 + 2] = a[8] * x + a[9] * y + a[10] * z + a[11]
            if (norm == null) continue
            val nx = ns[at]; val ny = ns[at + 1]; val nz = ns[at + 2]
            norm[i * 3] = a[0] * nx + a[1] * ny + a[2] * nz
            norm[i * 3 + 1] = a[4] * nx + a[5] * ny + a[6] * nz
            norm[i * 3 + 2] = a[8] * nx + a[9] * ny + a[10] * nz
        }
    }
}
