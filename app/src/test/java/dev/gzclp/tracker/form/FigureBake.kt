package dev.gzclp.tracker.form

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Fits tools/body-mesh.py's body.bin onto the pieces and writes all [Figure] reads at runtime to figure.bin, so the app
 * loads the mesh with bulk reads alone. Rebake after changing body.bin, this fit, or the pieces it is fitted to:
 * BAKE=1 ./gradlew testDebugUnitTest --tests 'dev.gzclp.tracker.form.FigureBake'
 */
class FigureBake {
    @Test fun theAppsFigureIsTheMeshFittedNow() {
        val baked = bake()
        val out = File("src/main/resources/dev/gzclp/tracker/form/figure.bin")
        if (System.getenv("BAKE") != null) return out.writeBytes(baked)
        val shipped = Figure::class.java.getResourceAsStream("figure.bin")!!.use { it.readBytes() }
        assertTrue("figure.bin is stale: rebake it (see FigureBake)", shipped.contentEquals(baked))
    }

    /** Which of a loft's surfaces [wear] moves a part's flesh onto. */
    private enum class Onto {
        FRONT_AND_BACK,
        /** The front only, where the back is flesh of its own (the glutes over the top of a thigh). */
        FRONT,
        SIDES,
    }

    private lateinit var bones: ByteArray
    private lateinit var weights: FloatArray
    private lateinit var lead: IntArray

    private fun bake(): ByteArray {
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
        val bind = Array(Bone.entries.size) { Figure.REST.f[it]!! }

        /** Where the rig's point [i] of [from] lands once each of its bones' parts sits on that bone of [Figure.REST]. */
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
                val r = if (len > 0) Figure.length(Bone.entries[b]) / len else 1.0
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
        val mesh = Mesh(pos, normals(pos, tri), tri)
        fun at(i: Int) = V(pos[i * 3].toDouble(), pos[i * 3 + 1].toDouble(), pos[i * 3 + 2].toDouble())
        val reach = DoubleArray(Bone.entries.size)
        for (i in 0 until nv) {
            val b = Bone.entries[lead[i]]; val f = bind[b.ordinal]; val d = at(i) - f.o
            reach[b.ordinal] = max(reach[b.ordinal], (d - f.y * (d dot f.y).coerceIn(0.0, Figure.length(b))).len)
        }
        fun follows(i: Int, b: Bone) = (0..3).any { bones[i * 4 + it].toInt() == b.ordinal && weights[i * 4 + it] > 0f }
        val joint = IntArray(nv) { i -> Figure.JOINTS.indices.firstOrNull { follows(i, Figure.JOINTS[it].second) } ?: -1 }
        val along = FloatArray(nv) { i ->
            if (joint[i] < 0) 0f else Figure.JOINTS[joint[i]].second.let { b ->
                val f = bind[b.ordinal]
                (((at(i) - f.o) dot f.y) / Figure.length(b)).coerceIn(0.0, 1.0).toFloat()
            }
        }
        val left = BooleanArray(nh) { pos[hand[it] * 3] > 0 }
        val fist = FloatArray(nh * 3)
        for ((k, i) in hand.withIndex()) fit(rawFist, k, i, fist)
        val closed = pos.copyOf()
        for ((k, i) in hand.withIndex()) for (c in 0..2) closed[i * 3 + c] = fist[k * 3 + c]
        val n = normals(closed, tri)
        val fistNorm = FloatArray(nh * 3) { n[hand[it / 3] * 3 + it % 3] }

        // Each step a twisting joint's vertices sit at down its child becomes a bone of its own, which they follow in place of the child.
        val step = IntArray(nv) { (along[it] * Figure.TWIST_STEPS).roundToInt() }
        val steps = (0 until nv).filter { joint[it] >= 0 }.map { joint[it] to step[it] }.distinct().sortedWith(compareBy({ it.first }, { it.second }))
        val nb = Bone.entries.size
        check(nb + steps.size <= 256) { "more twist steps than a byte holds" }
        val skinned = bones.copyOf()
        for (i in 0 until nv) if (joint[i] >= 0) for (k in 0..3) if (bones[i * 4 + k].toInt() == Figure.JOINTS[joint[i]].second.ordinal) {
            skinned[i * 4 + k] = (nb + steps.indexOf(joint[i] to step[i])).toByte()
        }

        // In the order Figure reads it.
        val edges = mesh.edges
        check(edges.all { it in -1..Short.MAX_VALUE }) { "an edge's vertex or triangle past a short" }
        val out = ByteBuffer.allocate(4 * 6 + 4 * nv * 3 * 2 + 2 * nt * 3 + 2 * edges.size + nv * 9 + 5 * steps.size + 8 * reach.size + nh * 3 + 4 * nh * 6)
            .order(ByteOrder.LITTLE_ENDIAN)
        out.putInt(Figure.FIGURE_VERSION).putInt(nv).putInt(nt).putInt(nh).putInt(edges.size).putInt(steps.size)
        for (x in mesh.pos) out.putFloat(x)
        for (x in mesh.norm) out.putFloat(x)
        for (x in tri) out.putShort(x)
        for (x in edges) out.putShort(x.toShort())
        for (x in lead) out.put(x.toByte())
        out.put(skinned)
        for ((j, _) in steps) out.put(j.toByte())
        for ((_, k) in steps) out.putFloat(k.toFloat() / Figure.TWIST_STEPS)
        for (x in weights) out.put((x * 255).roundToInt().toByte())
        for (x in reach) out.putDouble(x)
        for (x in hand) out.putShort(x.toShort())
        for (x in left) out.put(if (x) 1 else 0)
        for (x in fist) out.putFloat(x)
        for (x in fistNorm) out.putFloat(x)
        check(!out.hasRemaining())
        return out.array()
    }

    /**
     * Moves the flesh of [group] in [pos] [onto] those surfaces of [loft] (the pieces' part in [frame]'s rest coordinates),
     * level by level from [from] to [to] up it ([along] further up [loft]), fading in and out over [FADE] at the ends.
     * The motions were tuned on the pieces, so a bar across the back or a bench under it has to find the mesh where it found them.
     */
    private fun wear(pos: FloatArray, group: List<Bone>, frame: Bone, loft: Profile, from: Double, to: Double, onto: Onto = Onto.FRONT_AND_BACK, along: Double = 0.0) {
        val f = Figure.REST[frame]
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

    private companion object {
        const val STEP = 0.02
        const val FADE = 0.06
    }
}
