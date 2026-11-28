package dev.gzclp.tracker.form

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The figure as one continuous mesh skinned to the 19 bones, drawn in place of [Body]'s pieces while [on]: MakeHuman's CC0
 * base mesh, made by tools/body-mesh.py (see NOTICE). It comes in the rig's own space and is fitted onto [REST], a pose close
 * to the rig's own, when it is baked into figure-<body>.bin (FigureBake): each bone's part of it moves onto that bone and
 * stretches to its length. Each frame every vertex follows its bones (up to four) by linear blend skinning, and a closed hand
 * swaps in a fist. Each body ([shape]) is the same vertices, triangles and weights in another shape.
 */
object Figure {
    /** Draws and solves the figure as this mesh rather than the pieces; on by default. */
    var on = true

    /** figure-<body>.bin's layout, which FigureBake writes. */
    internal const val FIGURE_VERSION = 2

    /** How finely a twist runs down a twisting joint's child: in this many steps, each a bone of its own to the vertices at it. */
    internal const val TWIST_STEPS = 32

    /** Each body's name: a sex, "male" or "female", alone for the athletic build or with "-bodybuilder" or "-normal". */
    val bodies = listOf("male", "female", "male-bodybuilder", "male-normal", "female-bodybuilder", "female-normal")

    /** The body for a sex and build ("athletic", "bodybuilder" or "normal"). */
    fun body(female: Boolean, build: String) = (if (female) "female" else "male") + if (build == "athletic") "" else "-$build"

    /** The body the figure wears, one of [bodies]. */
    var shape = "male"

    /** Each body read the first time it is drawn or solved, so the pieces and the other bodies never wait on it. */
    private val data = bodies.associateWith { lazy { Data("figure-$it.bin") } }
    private val current: Data get() = data.getValue(shape).value

    val mesh: Mesh get() = current.mesh

    /** Each vertex's bone it follows most, for [Poser.skin]. */
    val lead: IntArray get() = current.lead

    /** The body's hair, which moves with the head: in [Bone.HEAD]'s frame, to place at its posed frame. */
    val hair: Mesh? get() = hairs[shape]

    /** Each body's hair (tools/body-mesh.py), read with the first. */
    private val hairs by lazy {
        val buf = ByteBuffer.wrap(Figure::class.java.getResourceAsStream("hair.bin")!!.use { it.readBytes() }).order(ByteOrder.LITTLE_ENDIAN)
        check(buf.int == 1) { "hair.bin version" }
        HashMap<String, Mesh>().apply {
            repeat(buf.int) {
                val name = String(ByteArray(buf.get().toInt()).also { buf.get(it) })
                val hv = buf.int; val ht = buf.int
                val p = FloatArray(hv * 3) { buf.float }
                val t = ShortArray(ht * 3) { buf.short }
                this[name] = Mesh(p, normals(p, t), t)
            }
        }
    }

    /**
     * The hair placed at the head's posed frame [f] into [out]: rigid, except that what would reach under the floor lies
     * on it, as a bun does under a head lying on its back.
     */
    fun placeHair(f: Frame, out: FloatArray) {
        val p = hair!!.pos
        for (i in 0 until p.size / 3) {
            val x = p[i * 3].toDouble(); val y = p[i * 3 + 1].toDouble(); val z = p[i * 3 + 2].toDouble()
            out[i * 3] = (f.o.x + f.x.x * x + f.y.x * y + f.z.x * z).toFloat()
            out[i * 3 + 1] = maxOf(0.0, f.o.y + f.x.y * x + f.y.y * y + f.z.y * z).toFloat()
            out[i * 3 + 2] = (f.o.z + f.x.z * x + f.y.z * y + f.z.z * z).toFloat()
        }
    }

    /** Each vertex's normal from the faces around it, weighted by their area. */
    internal fun normals(pos: FloatArray, tri: ShortArray): FloatArray {
        val n = FloatArray(pos.size)
        for (t in tri.indices step 3) {
            val a = tri[t] * 3; val b = tri[t + 1] * 3; val c = tri[t + 2] * 3
            val ux = pos[b] - pos[a]; val uy = pos[b + 1] - pos[a + 1]; val uz = pos[b + 2] - pos[a + 2]
            val vx = pos[c] - pos[a]; val vy = pos[c + 1] - pos[a + 1]; val vz = pos[c + 2] - pos[a + 2]
            val nx = uy * vz - uz * vy; val ny = uz * vx - ux * vz; val nz = ux * vy - uy * vx
            for (k in intArrayOf(a, b, c)) { n[k] += nx; n[k + 1] += ny; n[k + 2] += nz }
        }
        for (i in 0 until n.size / 3) {
            val l = kotlin.math.sqrt(n[i * 3] * n[i * 3] + n[i * 3 + 1] * n[i * 3 + 1] + n[i * 3 + 2] * n[i * 3 + 2]).coerceAtLeast(1e-12f)
            for (k in 0..2) n[i * 3 + k] /= l
        }
        return n
    }

    /**
     * The joints whose child's twist runs down it rather than blending linearly, parent then child: the shoulders, which twist
     * furthest. Their twist is split from their swing, and follows the move's own path ([Poser.twist]).
     */
    internal val JOINTS = SIDES.map { Bone.CHEST to Bone.arm(it) }

    /**
     * The elbows, whose child turns about its own length too, as the forearm turns the palm: blended linearly, the forearm
     * thins to a strap at the elbow in every grip that is not the rest pose's. An elbow only hinges, so its twist is read
     * off the hinge, which holds however flat it folds (where a split from the swing breaks down). It is read each frame
     * as the turn nearest none, with no path to follow, so past [HINGE_FULL] degrees it runs down the forearm less and
     * less, and not at all at half a turn, where the nearest turn flips to the other way round: the few grips that turn a
     * forearm that far blend it linearly, as every grip did.
     */
    internal val HINGES = SIDES.map { Bone.arm(it) to Bone.forearm(it) }

    /** Up to this many degrees from rest, all of a hinge's twist runs down its child. */
    internal const val HINGE_FULL = 120.0

    /** Every joint whose child's twist runs down it: [JOINTS], then [HINGES]. */
    internal val TWISTING = JOINTS + HINGES

    /** The rig's bind pose as Poser angles: arms 40° out, elbows bent 47°, palms toward the thighs, legs a little apart. */
    val REST = Poser().apply {
        pelvis(V(0.0, STAND, 0.0))
        for (s in SIDES) {
            arm(s, raise = 39.6, plane = 0.0, twist = 8.6, elbow = 46.9, pronate = 96.4, wrist = 14.0)
            leg(s, raise = 7.0, plane = 22.5, knee = 8.1)
        }
    }

    /** How long each bone is in Poser, from its joint to the next; 0 for an end bone, which keeps its size. */
    internal fun length(b: Bone) = when (b) {
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

    /**
     * How far (degrees) the child of [JOINTS]' joint [j] has twisted about its own length [a] since rest, apart from how it
     * swings out from its parent; [at] is the joint at rest. Null until both are placed.
     */
    internal class Twist(val a: V, val twist: Double, val at: V)

    internal fun twist(p: Poser, j: Int): Twist? = current.twist(p, j)

    /** [twist] for [TWISTING]'s hinge [j]: how far its child has turned about its own length since rest, from where the hinge alone leaves it. */
    internal fun hinge(p: Poser, j: Int): Twist? = current.hinge(p, j)

    /** How far the skin that follows [b] most reaches from it at rest: from the line between its joint and the next. */
    internal fun reach(b: Bone) = current.reach[b.ordinal]

    /** How many bones the mesh's vertices follow: the 19, then every step down each twisting joint's child, the same for every body. */
    internal val blends get() = Bone.entries.size + TWISTING.size * (TWIST_STEPS + 1)

    /** Writes the 3×3 turn by [deg] degrees about the unit axis [k] into [out]. */
    private fun turn(k: V, deg: Double, out: FloatArray) {
        val c = kotlin.math.cos(deg * DEG); val sn = kotlin.math.sin(deg * DEG); val t = 1 - c
        out[0] = (c + t * k.x * k.x).toFloat(); out[1] = (t * k.x * k.y - sn * k.z).toFloat(); out[2] = (t * k.x * k.z + sn * k.y).toFloat()
        out[3] = (t * k.x * k.y + sn * k.z).toFloat(); out[4] = (c + t * k.y * k.y).toFloat(); out[5] = (t * k.y * k.z - sn * k.x).toFloat()
        out[6] = (t * k.x * k.z - sn * k.y).toFloat(); out[7] = (t * k.y * k.z + sn * k.x).toFloat(); out[8] = (c + t * k.z * k.z).toFloat()
    }

    /** [out] = [x] × [y], 3×3 matrices in rows, [x] read from [xo] with a row stride of [xs]. */
    private fun times(x: FloatArray, xo: Int, xs: Int, y: FloatArray, out: FloatArray) {
        for (i in 0..2) for (j in 0..2) out[i * 3 + j] = x[xo + i * xs] * y[j] + x[xo + i * xs + 1] * y[3 + j] + x[xo + i * xs + 2] * y[6 + j]
    }

    /**
     * Bends the mesh to [p]: each vertex's world position into [pos] and, when given, its normal (any length) into [norm]. Part
     * way through a pose, a vertex follows only the bones [p] has placed so far, and gets NaN when it has none of them. Only
     * the vertices that follow [only] most are written; the rest keep what they had.
     */
    fun pose(p: Poser, pos: FloatArray, norm: FloatArray? = null, only: List<Bone> = Bone.entries, height: Boolean = false) = current.pose(p, pos, norm, only, height)

    /** A body's mesh and how it bends, from its baked [file]. */
    private class Data(file: String) {
        val mesh: Mesh
        val lead: IntArray
        val reach = DoubleArray(Bone.entries.size)
        private val bones: ByteArray
        private val weights: FloatArray
        private val bind = Array(Bone.entries.size) { REST.f[it]!! }

        /**
         * Past the 19 bones, every step down the children of [TWISTING]: which joint each is on, and how far
         * down its child at rest, from the joint (0) to the far end (1). A vertex follows its step in place of the child.
         */
        val stepJoint: ByteArray
        private val stepAlong: FloatArray

        /** Per vertex, its place among the hand vertices (-1 off the hands), and per hand vertex, which side it is on and its fist position and normal. */
        private val slot: IntArray
        private val left: ByteArray
        private val fist: FloatArray
        private val fistNorm: FloatArray

        init {
            // Already fitted (FigureBake), so all there is to do is copy it out.
            val buf = ByteBuffer.wrap(Figure::class.java.getResourceAsStream(file)!!.use { it.readBytes() }).order(ByteOrder.LITTLE_ENDIAN)
            check(buf.int == FIGURE_VERSION) { "$file version" }
            val nv = buf.int; val nt = buf.int; val nh = buf.int; val ne = buf.int; val ns = buf.int
            check(Bone.entries.size + ns == blends) { "$file twist steps" }
            fun floats(n: Int) = FloatArray(n).also { buf.asFloatBuffer().get(it); buf.position(buf.position() + 4 * n) }
            fun bytes(n: Int) = ByteArray(n).also { buf.get(it) }
            val pos = floats(nv * 3); val norm = floats(nv * 3)
            val tri = ShortArray(nt * 3).also { buf.asShortBuffer().get(it); buf.position(buf.position() + 2 * it.size) }
            val edges = ShortArray(ne).also { buf.asShortBuffer().get(it); buf.position(buf.position() + 2 * ne) }
            mesh = Mesh(pos, norm, tri, IntArray(ne) { edges[it].toInt() })
            val leads = bytes(nv)
            lead = IntArray(nv) { leads[it].toInt() }
            bones = bytes(nv * 4)
            stepJoint = bytes(ns)
            stepAlong = floats(ns)
            weights = bytes(nv * 4).let { w -> FloatArray(w.size) { (w[it].toInt() and 0xFF) / 255f } }
            buf.asDoubleBuffer().get(reach); buf.position(buf.position() + 8 * reach.size)
            val hand = ShortArray(nh).also { buf.asShortBuffer().get(it); buf.position(buf.position() + 2 * nh) }
            slot = IntArray(nv) { -1 }
            for (k in 0 until nh) slot[hand[k].toInt() and 0xFFFF] = k
            left = bytes(nh)
            fist = floats(nh * 3)
            fistNorm = floats(nh * 3)
        }

        internal fun twist(p: Poser, j: Int): Twist? {
            val (parent, child) = JOINTS[j]
            if (!p.has(child) || !p.has(parent)) return null
            val c = p[parent]; val cb = bind[parent.ordinal]; val f = p[child]; val fb = bind[child.ordinal]
            // Rest child space, to the child now, into parent space, back to rest.
            fun q(v: V): V { val w = f.dir(v dot fb.x, v dot fb.y, v dot fb.z); return cb.dir(w dot c.x, w dot c.y, w dot c.z) }
            val a = fb.y
            val b = q(a)
            val swing = kotlin.math.acos((a dot b).coerceIn(-1.0, 1.0)) / DEG
            val k = (a cross b).unit(fb.x)
            val u = fb.x
            val tu = q(u).turn(k, -swing)
            val turn = kotlin.math.atan2(a dot (u cross tu), u dot tu) / DEG
            // A turn and that turn less a whole one bend the joint the same, but not the flesh along the bone: take the one nearest the move's own.
            val twist = turn + 360 * kotlin.math.round((p.twist[j] - turn) / 360)
            return Twist(a, twist, fb.o)
        }

        internal fun hinge(p: Poser, j: Int): Twist? {
            val (parent, child) = TWISTING[j]
            if (!p.has(child) || !p.has(parent)) return null
            fun turned(a: Frame, f: Frame): Double { val u = a.x.across(f.y); return kotlin.math.atan2((u cross f.x) dot f.y, u dot f.x) / DEG }
            val fb = bind[child.ordinal]
            val t = (turned(p[parent], p[child]) - turned(bind[parent.ordinal], fb)).let { it - 360 * kotlin.math.round(it / 360) }
            val run = ((180 - kotlin.math.abs(t)) / (180 - HINGE_FULL)).coerceIn(0.0, 1.0)
            return Twist(fb.y, t * run * run * (3 - 2 * run), fb.o)
        }

        fun pose(p: Poser, pos: FloatArray, norm: FloatArray?, only: List<Bone>, height: Boolean) {
            // Per bone, the map from the rest mesh to this pose: its frame now after the inverse of its frame at rest, as a 3×4 matrix.
            val m = p.blend
            val placed = BooleanArray(m.size / 12) { it < Bone.entries.size && p.has(Bone.entries[it]) }
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
            // Linear blending collapses a joint twisted far. The child's twist runs down it as it does in flesh instead: none at the
            // joint, where it blends with the parent as only swung, and all at the far end, a step at a time.
            val twists = Array(TWISTING.size) { if (it < JOINTS.size) twist(p, it) else hinge(p, it) }
            val r1 = FloatArray(9); val r = FloatArray(9)
            for (s in stepJoint.indices) {
                val j = stepJoint[s].toInt(); val c = TWISTING[j].second.ordinal * 12; val o = (Bone.entries.size + s) * 12
                placed[Bone.entries.size + s] = placed[c / 12]
                if (!placed[c / 12]) continue
                val tw = twists[j]
                if (tw == null) { m.copyInto(m, o, c, c + 12); continue }
                turn(tw.a, -tw.twist * (1 - stepAlong[s]), r1)
                times(m, c, 4, r1, r)
                val qx = tw.at.x.toFloat() - (r1[0] * tw.at.x + r1[1] * tw.at.y + r1[2] * tw.at.z).toFloat()
                val qy = tw.at.y.toFloat() - (r1[3] * tw.at.x + r1[4] * tw.at.y + r1[5] * tw.at.z).toFloat()
                val qz = tw.at.z.toFloat() - (r1[6] * tw.at.x + r1[7] * tw.at.y + r1[8] * tw.at.z).toFloat()
                for (row in 0..2) {
                    m[o + row * 4] = r[row * 3]; m[o + row * 4 + 1] = r[row * 3 + 1]; m[o + row * 4 + 2] = r[row * 3 + 2]
                    m[o + row * 4 + 3] = m[c + row * 4] * qx + m[c + row * 4 + 1] * qy + m[c + row * 4 + 2] * qz + m[c + row * 4 + 3]
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
                    val b = bones[i * 4 + k].toInt() and 0xFF
                    if (w == 0f || !placed[b]) continue
                    sum += w
                    // Only the height row of each vertex's blend, when that is all that is asked for.
                    if (height) for (e in 4..7) a[e] += m[b * 12 + e] * w else for (e in 0..11) a[e] += m[b * 12 + e] * w
                }
                if (sum < 0.999f) for (e in 0..11) a[e] /= sum
                val h = slot[i]
                val fisted = h >= 0 && (if (left[h].toInt() != 0) closedL else closedR)
                val vs = if (fisted) fist else rest; val ns = if (fisted) fistNorm else restNorm
                val at = if (fisted) h * 3 else i * 3
                val x = vs[at]; val y = vs[at + 1]; val z = vs[at + 2]
                if (height) { pos[i * 3 + 1] = a[4] * x + a[5] * y + a[6] * z + a[7]; pos[i * 3] = if (sum == 0f) Float.NaN else 0f; continue }
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
}
