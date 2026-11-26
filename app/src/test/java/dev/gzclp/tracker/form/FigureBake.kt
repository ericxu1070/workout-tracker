package dev.gzclp.tracker.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fits each body tools/body-mesh.py made (the man in body.bin, the others in shapes.bin) onto the pieces and writes all
 * [Figure] reads at runtime to figure-<body>.bin, so the app loads a body with bulk reads alone. Rebake after changing
 * body.bin or shapes.bin, this fit, or the pieces it is fitted to:
 * BAKE=1 ./gradlew testDebugUnitTest --tests 'dev.gzclp.tracker.form.FigureBake'
 */
class FigureBake {
    @Test fun theAppsFiguresAreTheMeshesFittedNow() {
        val bodies = read()
        assertEquals(Figure.bodies, bodies.keys.toList())
        for ((name, body) in bodies) {
            val baked = bake(body.first, body.second, body.third)
            val file = "figure-$name.bin"
            if (System.getenv("BAKE") != null) { File("src/main/resources/dev/gzclp/tracker/form/$file").writeBytes(baked); continue }
            val shipped = Figure::class.java.getResourceAsStream(file)!!.use { it.readBytes() }
            assertTrue("$file is stale: rebake it (see FigureBake)", shipped.contentEquals(baked))
        }
    }

    /** Which of a loft's surfaces [wear] moves a part's flesh onto. */
    private enum class Onto {
        FRONT_AND_BACK,
        /** The front only, where the back is flesh of its own (the glutes over the top of a thigh). */
        FRONT,
        /** The back only, where the front is the body's own (its chest and belly). */
        BACK,
        SIDES,
    }

    private lateinit var bones: ByteArray
    private lateinit var weights: FloatArray
    private lateinit var lead: IntArray
    private lateinit var tri: ShortArray
    private lateinit var hand: IntArray

    /** Each body by name: the rig's frames and bone lengths in its space, its positions, and its fist positions. */
    private fun read(): Map<String, Triple<Array<Pair<Frame, Double>>, FloatArray, FloatArray>> {
        fun open(name: String) = ByteBuffer.wrap(Figure::class.java.getResourceAsStream(name)!!.use { it.readBytes() }).order(ByteOrder.LITTLE_ENDIAN)
        fun ByteBuffer.frames() = Array(Bone.entries.size) {
            fun v() = V(float.toDouble(), float.toDouble(), float.toDouble())
            Frame(v(), v(), v(), v()) to float.toDouble()
        }
        val buf = open("body.bin")
        check(buf.int == 1) { "body.bin version" }
        val nv = buf.int; val nt = buf.int; val nh = buf.int
        val bodies = LinkedHashMap<String, Triple<Array<Pair<Frame, Double>>, FloatArray, FloatArray>>()
        val src = buf.frames()
        val raw = FloatArray(nv * 3) { buf.float }
        bones = ByteArray(nv * 4).also { buf.get(it) }
        weights = FloatArray(nv * 4) { (buf.get().toInt() and 0xFF) / 255f }
        tri = ShortArray(nt * 3) { buf.short }
        hand = IntArray(nh) { buf.short.toInt() and 0xFFFF }
        bodies["male"] = Triple(src, raw, FloatArray(nh * 3) { buf.float })
        val more = open("shapes.bin")
        check(more.int == 1) { "shapes.bin version" }
        repeat(more.int) {
            val name = String(ByteArray(more.get().toInt()).also { more.get(it) })
            val s = more.frames()
            bodies[name] = Triple(s, FloatArray(nv * 3) { more.float }, FloatArray(nh * 3) { more.float })
        }
        return bodies
    }

    /** One body, from the rig's frames [src], its positions [raw] and its fist positions [rawFist]. */
    private fun bake(src: Array<Pair<Frame, Double>>, raw: FloatArray, rawFist: FloatArray): ByteArray {
        val nv = raw.size / 3; val nt = tri.size / 3; val nh = hand.size
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
        val fitted = pos.copyOf()
        // The back only: the chest, belly and glutes keep the body's own shape.
        wear(pos, Bone.TRUNK.take(3), Bone.PELVIS, Body.torso.mesh.profile, -0.03, 0.53, Onto.BACK)
        for (s in SIDES) {
            // Twice for the thigh: the second pass pulls its front back onto the loft's, which a deadlift's bar clears by a hair.
            wear(pos, listOf(Bone.thigh(s)), Bone.thigh(s), Body.leg.mesh.profile, 0.04, 0.5)
            wear(pos, listOf(Bone.thigh(s)), Bone.thigh(s), Body.leg.mesh.profile, 0.04, 0.5, Onto.FRONT)
            wear(pos, listOf(Bone.shin(s)), Bone.shin(s), Body.leg.mesh.profile, 0.04, 0.4, Onto.SIDES, along = Build.THIGH)
        }
        relax(pos, fitted)
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

        // Each step down a twisting joint's child is a bone of its own, which the vertices at it follow in place of the child;
        // every step, whether or not a vertex sits at it, so every body has the same bones.
        val step = IntArray(nv) { (along[it] * Figure.TWIST_STEPS).roundToInt() }
        val steps = Figure.JOINTS.indices.flatMap { j -> (0..Figure.TWIST_STEPS).map { j to it } }
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
        // Each level's extremes come from a few vertices and jump from one level to the next; evened out along the part,
        // so the flesh moved onto the loft does not come out in ridges.
        repeat(2) { for (e in listOf(low, high)) { val c = e.copyOf(); for (k in 1 until n - 1) if (abs(c[k - 1]) < 1e9 && abs(c[k]) < 1e9 && abs(c[k + 1]) < 1e9) e[k] = (c[k - 1] + 2 * c[k] + c[k + 1]) / 4 } }
        for ((i, p) in local.withIndex()) {
            val share = (0..3).sumOf { if (bones[i * 4 + it].toInt() in ids) weights[i * 4 + it].toDouble() else 0.0 } *
                ((p.y - from) / FADE).coerceIn(0.0, 1.0) * ((to - p.y) / FADE).coerceIn(0.0, 1.0)
            if (share == 0.0) continue
            val u = (p.y - from) / STEP
            val k = u.toInt().coerceIn(0, n - 2)
            if (low[k] > high[k] || low[k + 1] > high[k + 1]) continue // a level past the group's own flesh
            val lo = lerp(low[k], low[k + 1], u - k); val hi = lerp(high[k], high[k + 1], u - k)
            val (a, b, cz) = loft.ellipse(p.y + along)
            // Held to the level's extremes: where few vertices lead the group (its lowest level on the woman) they span less
            // than the flesh around them, and mapping past them would throw that flesh out beyond the loft.
            val t = ((across(p) - lo) / (hi - lo)).coerceIn(0.0, 1.0)
            val c = when (onto) { Onto.FRONT_AND_BACK -> lerp(cz - b, cz + b, t); Onto.FRONT -> lerp(lo, cz + b, t); Onto.BACK -> lerp(cz - b, hi, t); Onto.SIDES -> lerp(-a, a, t) }
            val q = if (sides) f.at(p.x + share * (c - p.x), p.y, p.z) else f.at(p.x, p.y, p.z + share * (c - p.z))
            pos[i * 3] = q.x.toFloat(); pos[i * 3 + 1] = q.y.toFloat(); pos[i * 3 + 2] = q.z.toFloat()
        }
    }

    /** Evens out how far [wear] moved each vertex of [pos] from [fitted] over [RELAX] rounds with its neighbours, so the move leaves no ripples. */
    private fun relax(pos: FloatArray, fitted: FloatArray) {
        val nv = pos.size / 3
        val near = Array(nv) { HashSet<Int>() }
        for (t in tri.indices step 3) for (a in 0..2) for (b in 0..2) if (a != b) near[tri[t + a].toInt()] += tri[t + b].toInt()
        var d = FloatArray(pos.size) { pos[it] - fitted[it] }
        repeat(RELAX) {
            val e = FloatArray(d.size)
            for (i in 0 until nv) for (c in 0..2) e[i * 3 + c] = (d[i * 3 + c] + near[i].sumOf { d[it * 3 + c].toDouble() }.toFloat() / near[i].size) / 2
            d = e
        }
        for (k in pos.indices) pos[k] = fitted[k] + d[k]
    }

    private fun normals(pos: FloatArray, tri: ShortArray) = Figure.normals(pos, tri)

    private companion object {
        const val STEP = 0.02
        const val FADE = 0.06
        const val RELAX = 8
    }
}
