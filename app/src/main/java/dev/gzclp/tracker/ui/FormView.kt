package dev.gzclp.tracker.ui

import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.form.Body
import dev.gzclp.tracker.form.Figure
import dev.gzclp.tracker.form.Bone
import dev.gzclp.tracker.form.Focus
import dev.gzclp.tracker.form.Frame
import dev.gzclp.tracker.form.Mesh
import dev.gzclp.tracker.form.Motion
import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.form.Poser
import dev.gzclp.tracker.form.SEAMS
import dev.gzclp.tracker.form.Side
import dev.gzclp.tracker.form.Tone
import dev.gzclp.tracker.form.Skin
import dev.gzclp.tracker.form.V
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint as NativePaint

const val DEFAULT_YAW = 35f
const val DEFAULT_PITCH = 15f
const val MAX_PITCH = 89f
private const val DRAG_DEG_PER_PX = 0.4f

/** How fast the camera circles the figure when no finger holds it: one turn in 30 s. */
private const val SPIN_DEG_PER_SEC = 12f

/** One breath at rest, in seconds (about 14 a minute). */
private const val BREATH_SEC = 4.2

/** How far each silhouette fades out past its edge, in pixels: the figure's anti-aliasing. */
private const val FRINGE = 1.2f

/** How far, as a share of the other part's radius, a surface cut at a joint keeps going inside it, so no gap opens along the crease. */
private const val OVERLAP = 0.015

/** The opacity, of 255, left to fixed gear (a post, a step) that stands between the camera and the body, so the body shows through. */
private const val GHOST = 77

/** How many of the body's points a piece of gear must stand in front of before it fades. */
private const val HIDDEN = 8

/** Contact shadows fade out by this height above the floor, in metres. */
private const val LIFT = 0.35

/**
 * The orbit camera angles. Pitch stays between the floor plane (0) and straight down (89), so the eye never goes below the floor.
 * While no finger [held] it, a playing view [spin]s it slowly around the figure.
 */
@Stable
class Orbit(private val home: Float = DEFAULT_YAW) {
    var yaw by mutableFloatStateOf(home)
    var pitch by mutableFloatStateOf(DEFAULT_PITCH)
    var held = false

    fun spin(sec: Float) {
        if (!held) yaw = (yaw + sec * SPIN_DEG_PER_SEC) % 360f
    }

    fun drag(dx: Float, dy: Float) {
        yaw = (yaw - dx * DRAG_DEG_PER_PX) % 360f
        pitch = clampPitch(pitch + dy * DRAG_DEG_PER_PX)
    }

    fun reset() { yaw = home; pitch = DEFAULT_PITCH }

    companion object {
        fun clampPitch(p: Float) = p.coerceIn(0f, MAX_PITCH)
    }
}

/** A perspective camera on a sphere around [target]: [project] gives the screen point and depth, the target landing on the canvas center. */
class Projector {
    var sx = 0f; var sy = 0f; var depth = 0f

    /** Toward the key light, which rides with the camera so the side in view is always lit, and toward the eye; both in world space. */
    val light = FloatArray(3)
    val view = FloatArray(3)
    private val eye = FloatArray(3)
    private val f = FloatArray(3)
    private val r = FloatArray(3)
    private val u = FloatArray(3)
    private var focal = 1f
    private var cx = 0f
    private var cy = 0f

    fun set(yawDeg: Float, pitchDeg: Float, target: FloatArray, dist: Float, focal: Float, cx: Float, cy: Float) {
        val yaw = yawDeg * (PI / 180).toFloat()
        val pitch = Orbit.clampPitch(pitchDeg) * (PI / 180).toFloat()
        val cp = cos(pitch)
        f[0] = -cp * sin(yaw); f[1] = -sin(pitch); f[2] = -cp * cos(yaw)
        for (i in 0..2) eye[i] = target[i] - f[i] * dist
        val n = sqrt(f[0] * f[0] + f[2] * f[2])
        r[0] = -f[2] / n; r[1] = 0f; r[2] = f[0] / n
        u[0] = r[1] * f[2] - r[2] * f[1]; u[1] = r[2] * f[0] - r[0] * f[2]; u[2] = r[0] * f[1] - r[1] * f[0]
        this.focal = focal; this.cx = cx; this.cy = cy
        for (i in 0..2) { view[i] = -f[i]; light[i] = -0.5f * r[i] + 0.8f * u[i] - 0.45f * f[i] }
        val l = sqrt(light[0] * light[0] + light[1] * light[1] + light[2] * light[2])
        for (i in 0..2) light[i] /= l
    }

    fun project(x: Float, y: Float, z: Float) {
        val dx = x - eye[0]; val dy = y - eye[1]; val dz = z - eye[2]
        depth = dx * f[0] + dy * f[1] + dz * f[2]
        val s = focal / depth
        sx = cx + (dx * r[0] + dy * r[1] + dz * r[2]) * s
        sy = cy - (dx * u[0] + dy * u[1] + dz * u[2]) * s
    }
}

/** Shadow-to-light colour ramps for the body, the highlighted target area and each material, and the floor and shadow paints, for one palette. */
private class Tones(p: Palette) {
    val body: IntArray
    val accent: IntArray
    private val metal: IntArray
    private val rubber: IntArray
    private val pad: IntArray
    val floor = NativePaint(NativePaint.ANTI_ALIAS_FLAG)
    val shadow = NativePaint(NativePaint.ANTI_ALIAS_FLAG)

    init {
        fun ramp(lo: Color, hi: Color) = IntArray(64) { lerp(lo, hi, it / 63f).toArgb() }
        val bg = p.bg; val fg = p.fg; val g = p.fg2; val a = p.accent
        if (p.dark) {
            body = ramp(lerp(fg, bg, 0.66f), fg)
            accent = ramp(lerp(a, bg, 0.62f), lerp(a, Color.White, 0.2f))
            metal = ramp(lerp(g, bg, 0.72f), lerp(g, Color.White, 0.15f))
            rubber = ramp(lerp(g, bg, 0.9f), lerp(g, bg, 0.55f))
            pad = ramp(lerp(g, bg, 0.86f), lerp(g, bg, 0.45f))
        } else {
            body = ramp(lerp(fg, bg, 0.3f), lerp(fg, bg, 0.8f))
            accent = ramp(lerp(a, fg, 0.4f), lerp(a, bg, 0.45f))
            metal = ramp(lerp(g, fg, 0.3f), lerp(g, bg, 0.7f))
            rubber = ramp(lerp(fg, bg, 0.08f), lerp(fg, bg, 0.42f))
            pad = ramp(lerp(fg, bg, 0.15f), lerp(fg, bg, 0.5f))
        }
        val f = fg.copy(alpha = if (p.dark) 0.07f else 0.05f).toArgb()
        floor.shader = RadialGradient(0f, 0f, 1f, intArrayOf(f, f, f and 0xFFFFFF), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        val s = if (p.dark) 0x70000000 else 0x48000000
        shadow.shader = RadialGradient(0f, 0f, 1f, intArrayOf(s, s / 2 and 0x7F000000, 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    fun of(t: Tone) = when (t) { Tone.METAL -> metal; Tone.RUBBER -> rubber; Tone.PAD -> pad }
}

/** Each bone's print on the floor for its contact shadow: from [0] to [1] along it, [2] toward its front, [3] wide. */
private val PRINT: Array<DoubleArray> = Bone.entries.map { b ->
    when (b) {
        Bone.PELVIS -> doubleArrayOf(-0.04, 0.08, -0.02, 0.15)
        Bone.WAIST -> doubleArrayOf(0.0, 0.18, 0.0, 0.14)
        Bone.CHEST -> doubleArrayOf(0.0, 0.26, 0.01, 0.16)
        Bone.NECK -> doubleArrayOf(0.0, 0.1, 0.0, 0.05)
        Bone.HEAD -> doubleArrayOf(0.0, 0.17, 0.02, 0.09)
        Bone.ARM_L, Bone.ARM_R -> doubleArrayOf(0.0, 0.3, 0.0, 0.05)
        Bone.FOREARM_L, Bone.FOREARM_R -> doubleArrayOf(0.0, 0.25, 0.0, 0.04)
        Bone.HAND_L, Bone.HAND_R -> doubleArrayOf(0.0, 0.1, 0.0, 0.04)
        Bone.THIGH_L, Bone.THIGH_R -> doubleArrayOf(0.0, 0.43, 0.0, 0.07)
        Bone.SHIN_L, Bone.SHIN_R -> doubleArrayOf(0.0, 0.43, 0.0, 0.05)
        Bone.FOOT_L, Bone.FOOT_R -> doubleArrayOf(-0.05, 0.14, -0.06, 0.045)
        Bone.TOES_L, Bone.TOES_R -> doubleArrayOf(0.0, 0.05, 0.0, 0.04)
    }
}.toTypedArray()

/**
 * Everything one figure needs per frame, allocated once and grown only when a heavier scene needs it. Each frame the posed
 * meshes are lit per vertex, back faces dropped, surfaces that meet at a joint cut where they run into each other (whole
 * triangles sorted by depth would interleave along the crossing), each silhouette edge given a fringe that fades to clear
 * (anti-aliasing, which drawVertices lacks), and every triangle sorted far to near into one drawVertices call.
 */
private class Scene {
    val pose = Poser()
    val pr = Projector()
    private val paint = NativePaint().apply { color = android.graphics.Color.WHITE }
    private val over = NativePaint().apply { color = android.graphics.Color.WHITE; xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC) }
    private val m = Matrix()
    private val mv = FloatArray(9)
    private var xy = FloatArray(4096)
    private var dep = FloatArray(2048)
    private var col = IntArray(2048)
    /** Each vertex's colour in the see-through pass: fixed gear that hides the body keeps its own, everything else is clear. */
    private var shade = IntArray(2048)
    private var wp = FloatArray(2048 * 3)
    private var cut = FloatArray(2048)
    private var tri = IntArray(4096 * 3)
    private var keys = LongArray(4096)
    private var out = ShortArray(4096 * 3)
    private var front = BooleanArray(512)
    private val skin = FloatArray(pose.bent.size)
    private val skinNormal = FloatArray(pose.bent.size)
    private var nv = 0
    private var nt = 0
    private var hot = IntArray(0)

    /** Each surface placed this frame and its first vertex; per bone, the surface it moves and where the bone starts in it. */
    private val placed = ArrayList<Mesh>()
    private var starts = IntArray(32)
    private val meshOf = arrayOfNulls<Mesh>(Bone.entries.size)
    private val startOf = IntArray(Bone.entries.size)
    private val originOf = arrayOfNulls<V>(Bone.entries.size)
    private val poly = IntArray(4)
    private var gearAt = IntArray(64)

    fun draw(c: NativeCanvas, w: Float, h: Float, motion: Motion, d: Double, breath: Double, orbit: Orbit, tones: Tones, focus: Focus?) {
        val r = motion.radius
        val dist = r * 3.5f
        pr.set(orbit.yaw, orbit.pitch, motion.center, dist, min(w, h) * 0.46f * sqrt(dist * dist - r * r) / r, w / 2, h / 2)
        motion.pose(pose, d, breath)

        val ring = max(r, 1f)
        ellipse(c, motion.center[0], motion.center[2], ring, 0f, 0f, ring, tones.floor)
        for (b in Bone.entries) if (pose.has(b)) {
            val p = PRINT[b.ordinal]
            val f = pose[b]
            shadow(c, f.at(0.0, p[0], p[2]), f.at(0.0, p[1], p[2]), p[3], tones.shadow)
        }
        for (g in pose.gear) when (g.mesh) {
            Body.rod -> shadow(c, g.frame.o, g.frame.at(0.0, g.sy, 0.0), g.sx, tones.shadow)
            else -> shadow(c, g.frame.o, g.frame.o, max(g.sx, g.sz) / 2, tones.shadow)
        }

        nv = 0; nt = 0; placed.clear(); meshOf.fill(null); hot = tones.accent
        if (Figure.on) figure(tones, focus?.figure) else {
            for ((s, bones) in pose.chains()) if (bones.all { pose.has(it) }) chain(s, bones, tones, focus?.of(s, bones))
            for (b in Bone.entries) if (pose.has(b)) Body.of(b)?.let { part(it, pose[b], 1.0, 1.0, 1.0, tones.body, false, b, focus?.of(it, b)) }
        }
        val body = nv
        val gear = pose.gear
        if (gearAt.size <= gear.size) gearAt = IntArray(gear.size * 2 + 1)
        for (k in gear.indices) { val g = gear[k]; gearAt[k] = nv; part(g.mesh, g.frame, g.sx, g.sy, g.sz, tones.of(g.tone), g.tone == Tone.METAL) }
        gearAt[gear.size] = nv
        val ghosts = ghost(body)
        if (!Figure.on) for (s in SEAMS) { cut(s.a, s.b); cut(s.b, s.a) }
        for (k in placed.indices) faces(placed[k], starts[k])
        java.util.Arrays.sort(keys, 0, nt)
        for (j in 0 until nt) {
            val t = (keys[j] and 0x3FFFFF).toInt() * 3
            out[j * 3] = tri[t].toShort(); out[j * 3 + 1] = tri[t + 1].toShort(); out[j * 3 + 2] = tri[t + 2].toShort()
        }
        c.drawVertices(NativeCanvas.VertexMode.TRIANGLES, nv * 2, xy, 0, null, 0, col, 0, out, 0, nt * 3, paint)
        if (!ghosts) return
        // Every triangle again, far to near, each replacing what is under it: the faded gear leaves its colour, and any of the
        // body in front of it clears it again. One layer, so where two of its faces overlap it does not get darker.
        c.saveLayerAlpha(null, GHOST)
        c.drawVertices(NativeCanvas.VertexMode.TRIANGLES, nv * 2, xy, 0, null, 0, shade, 0, out, 0, nt * 3, over)
        c.restore()
    }

    /** Fills [paint]'s unit circle over the ellipse on the floor centred at ([x], [z]) with half-axes ([ux], [uz]) and ([vx], [vz]). */
    private fun ellipse(c: NativeCanvas, x: Float, z: Float, ux: Float, uz: Float, vx: Float, vz: Float, paint: NativePaint) {
        pr.project(x, 0f, z); val cx = pr.sx; val cy = pr.sy
        pr.project(x + ux, 0f, z + uz); val ax = pr.sx - cx; val ay = pr.sy - cy
        pr.project(x + vx, 0f, z + vz); val bx = pr.sx - cx; val by = pr.sy - cy
        mv[0] = ax; mv[1] = bx; mv[2] = cx; mv[3] = ay; mv[4] = by; mv[5] = cy; mv[6] = 0f; mv[7] = 0f; mv[8] = 1f
        m.setValues(mv)
        c.save(); c.concat(m); c.drawCircle(0f, 0f, 1f, paint); c.restore()
    }

    /** A soft shadow under a capsule from [a] to [b] [w] thick, darker the nearer it is to the floor. */
    private fun shadow(c: NativeCanvas, a: V, b: V, w: Double, paint: NativePaint) {
        val h = min(a.y, b.y) - w
        if (h >= LIFT) return
        val k = 1 - max(h, 0.0) / LIFT
        paint.alpha = (255 * k * k).toInt()
        val dx = b.x - a.x; val dz = b.z - a.z
        val len = sqrt(dx * dx + dz * dz)
        val ux = if (len > 1e-4) dx / len else 1.0
        val uz = if (len > 1e-4) dz / len else 0.0
        val half = len / 2 + w
        val wide = w * 1.25
        ellipse(c, ((a.x + b.x) / 2).toFloat(), ((a.z + b.z) / 2).toFloat(), (ux * half).toFloat(), (uz * half).toFloat(), (-uz * wide).toFloat(), (ux * wide).toFloat(), paint)
    }

    /** Grows the buffers to take [verts] more vertices and [tris] more triangles. */
    private fun grow(verts: Int, tris: Int) {
        if (nv + verts > col.size) {
            val n = max(col.size * 2, nv + verts)
            xy = xy.copyOf(n * 2); dep = dep.copyOf(n); col = col.copyOf(n); shade = shade.copyOf(n); wp = wp.copyOf(n * 3); cut = cut.copyOf(n)
        }
        if (nt + tris > keys.size) {
            val n = max(keys.size * 2, nt + tris)
            tri = tri.copyOf(n * 3); keys = keys.copyOf(n); out = out.copyOf(n * 3)
        }
    }

    /** Makes room for [mesh]'s vertices from [nv] on and lists it to draw; false once the scene outgrows drawVertices' 16-bit indices. */
    private fun place(mesh: Mesh): Boolean {
        if (nv + mesh.verts > Short.MAX_VALUE) return false
        grow(mesh.verts, 0)
        if (placed.size == starts.size) starts = starts.copyOf(starts.size * 2)
        starts[placed.size] = nv
        placed += mesh
        return true
    }

    /** Queues a triangle; the key sorts far ones first and keeps the order of equal depths. */
    private fun add(a: Int, b: Int, c: Int, depth: Float) {
        tri[nt * 3] = a; tri[nt * 3 + 1] = b; tri[nt * 3 + 2] = c
        keys[nt] = ((Int.MAX_VALUE - java.lang.Float.floatToRawIntBits(depth)).toLong() shl 22) or nt.toLong()
        nt++
    }

    /** Projects and lights one mesh placed by [f] and stretched by [sx], [sy], [sz]; [bone] is the body part it is, if any, and [glow] each vertex's share of the accent. */
    private fun part(mesh: Mesh, f: Frame, sx: Double, sy: Double, sz: Double, ramp: IntArray, metal: Boolean, bone: Bone? = null, glow: FloatArray? = null) {
        if (!place(mesh)) return
        if (bone != null) { meshOf[bone.ordinal] = mesh; startOf[bone.ordinal] = nv; originOf[bone.ordinal] = V.ZERO }
        val p = mesh.pos
        val n = mesh.norm
        val o = f.o; val fx = f.x; val fy = f.y; val fz = f.z
        for (i in 0 until mesh.verts) {
            val lx = p[i * 3] * sx; val ly = p[i * 3 + 1] * sy; val lz = p[i * 3 + 2] * sz
            val ax = n[i * 3] / sx; val ay = n[i * 3 + 1] / sy; val az = n[i * 3 + 2] / sz
            vertex(
                nv + i,
                (o.x + fx.x * lx + fy.x * ly + fz.x * lz).toFloat(), (o.y + fx.y * lx + fy.y * ly + fz.y * lz).toFloat(), (o.z + fx.z * lx + fy.z * ly + fz.z * lz).toFloat(),
                (fx.x * ax + fy.x * ay + fz.x * az).toFloat(), (fx.y * ax + fy.y * ay + fz.y * az).toFloat(), (fx.z * ax + fy.z * ay + fz.z * az).toFloat(),
                ramp, metal, glow?.get(i) ?: 0f,
            )
        }
        nv += mesh.verts
    }

    /** A [Skin] bent by [bones]; each vertex takes its [glow] share of the accent. */
    private fun chain(s: Skin, bones: Array<Bone>, tones: Tones, glow: FloatArray?) {
        val mesh = s.mesh
        if (!place(mesh)) return
        for (k in bones.indices) { meshOf[bones[k].ordinal] = mesh; startOf[bones[k].ordinal] = nv; originOf[bones[k].ordinal] = s.origin(k) }
        s.pose(pose, bones, skin, skinNormal)
        for (i in 0 until mesh.verts) {
            vertex(nv + i, skin[i * 3], skin[i * 3 + 1], skin[i * 3 + 2], skinNormal[i * 3], skinNormal[i * 3 + 1], skinNormal[i * 3 + 2], tones.body, false, glow?.get(i) ?: 0f)
        }
        nv += mesh.verts
    }

    private val fig by lazy { FloatArray(Figure.mesh.verts * 3) }
    private val figNormal by lazy { FloatArray(Figure.mesh.verts * 3) }

    /** The [Figure] mesh bent to the pose, in one piece with no joints to cut; each vertex takes its [glow] share of the accent. */
    private fun figure(tones: Tones, glow: FloatArray?) {
        val mesh = Figure.mesh
        if (!place(mesh)) return
        Figure.pose(pose, fig, figNormal)
        for (i in 0 until mesh.verts) {
            vertex(nv + i, fig[i * 3], fig[i * 3 + 1], fig[i * 3 + 2], figNormal[i * 3], figNormal[i * 3 + 1], figNormal[i * 3 + 2], tones.body, false, glow?.get(i) ?: 0f)
        }
        nv += mesh.verts
    }

    /** Moves each item of fixed gear that hides some of the body (a post, a step) out of the solid pass into [shade]; false if none does. */
    private fun ghost(body: Int): Boolean {
        val gear = pose.gear
        shade.fill(0, 0, nv)
        var any = false
        var i = 0
        while (i < gear.size) {
            var j = i + 1
            while (j < gear.size && gear[j].piece > 0) j++
            var front = false
            if (!gear[i].held) for (k in i until j) front = front || hides(gearAt[k], gearAt[k + 1], body)
            if (front) for (v in gearAt[i] until gearAt[j]) { shade[v] = col[v]; col[v] = col[v] and 0xFFFFFF }
            any = any || front
            i = j
        }
        return any
    }

    /** Whether the piece drawn with vertices [from] until [to] stands in front of [HIDDEN] or more of the body's first [body] vertices. */
    private fun hides(from: Int, to: Int, body: Int): Boolean {
        var x0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var y1 = -Float.MAX_VALUE; var far = -Float.MAX_VALUE
        for (v in from until to) {
            x0 = min(x0, xy[v * 2]); x1 = max(x1, xy[v * 2]); y0 = min(y0, xy[v * 2 + 1]); y1 = max(y1, xy[v * 2 + 1]); far = max(far, dep[v])
        }
        var n = 0
        for (v in 0 until body) if (dep[v] > far && xy[v * 2] in x0..x1 && xy[v * 2 + 1] in y0..y1 && ++n == HIDDEN) return true
        return false
    }

    /** Marks each point of [a] that may run into [b] with how far outside [b] it sits; one marked below 0 is cut away. */
    private fun cut(a: Side, b: Side) {
        val m = meshOf[a.bone.ordinal] ?: return
        val solid = meshOf[b.bone.ordinal] ?: return
        val base = startOf[a.bone.ordinal]
        val o = originOf[b.bone.ordinal] ?: return
        val f = pose[b.bone]
        val shape = solid.profile
        for (i in 0 until m.verts) {
            val rest = m.pos[i * 3 + 1]
            if (rest < a.lo || rest > a.hi) continue
            val v = base + i
            val dx = wp[v * 3] - f.o.x; val dy = wp[v * 3 + 1] - f.o.y; val dz = wp[v * 3 + 2] - f.o.z
            val y = dx * f.y.x + dy * f.y.y + dz * f.y.z + o.y
            if (y < b.from || y > b.to) continue
            val x = dx * f.x.x + dy * f.x.y + dz * f.x.z + o.x
            val z = dx * f.z.x + dy * f.z.y + dz * f.z.z + o.z
            cut[v] = min(cut[v], (shape.inside(x, y, z) + OVERLAP).toFloat())
        }
    }

    /** Projects vertex [v] at ([x], [y], [z]) and shades it along [ramp] by its normal ([nx], [ny], [nz], any length), [glow] of the way to the accent; [metal] adds a glint. */
    private fun vertex(v: Int, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, ramp: IntArray, metal: Boolean, glow: Float = 0f) {
        pr.project(x, y, z)
        xy[v * 2] = pr.sx; xy[v * 2 + 1] = pr.sy; dep[v] = pr.depth
        wp[v * 3] = x; wp[v * 3 + 1] = y; wp[v * 3 + 2] = z; cut[v] = 1f
        val nl = sqrt(nx * nx + ny * ny + nz * nz)
        val ux = nx / nl; val uy = ny / nl; val uz = nz / nl
        val lit = pr.light; val eye = pr.view
        val wrap = ((ux * lit[0] + uy * lit[1] + uz * lit[2] + 0.3f) / 1.3f).coerceIn(0f, 1f)
        val facing = (ux * eye[0] + uy * eye[1] + uz * eye[2]).coerceIn(0f, 1f)
        val k = 0.04f + 0.6f * wrap + 0.18f * (0.5f + 0.5f * uy) + 0.18f * facing
        val shade = (k.coerceIn(0f, 1f) * (ramp.size - 1)).toInt()
        var c = ramp[shade]
        if (glow > 0f) c = mix(c, hot[shade], glow)
        if (metal) {
            val hx = lit[0] + eye[0]; val hy = lit[1] + eye[1]; val hz = lit[2] + eye[2]
            val s = ((ux * hx + uy * hy + uz * hz) / sqrt(hx * hx + hy * hy + hz * hz)).coerceAtLeast(0f).pow(24) * 0.7f
            c = mix(c, -1, s)
        }
        col[v] = c
    }

    /** Queues the front faces of [mesh], whose vertices start at [base], cut where they run into another surface, and a fringe along its silhouette. */
    private fun faces(mesh: Mesh, base: Int) {
        // A cut triangle can add two vertices; a mesh nothing cuts (the Figure) needs no room for them.
        var cuts = false
        for (v in base until base + mesh.verts) cuts = cuts || cut[v] < 0f
        val more = !cuts || nv + 2 * mesh.tris <= Short.MAX_VALUE
        grow(if (cuts && more) 2 * mesh.tris else 0, 3 * mesh.tris)
        if (front.size < mesh.tris) front = BooleanArray(mesh.tris)
        val edges = mesh.edges
        val t = mesh.tri
        for (k in 0 until mesh.tris) {
            val a = base + t[k * 3]; val b = base + t[k * 3 + 1]; val c = base + t[k * 3 + 2]
            val area = (xy[b * 2] - xy[a * 2]) * (xy[c * 2 + 1] - xy[a * 2 + 1]) - (xy[c * 2] - xy[a * 2]) * (xy[b * 2 + 1] - xy[a * 2 + 1])
            front[k] = area < 0f
            if (area >= 0f) continue
            val kept = (if (cut[a] >= 0f) 1 else 0) + (if (cut[b] >= 0f) 1 else 0) + (if (cut[c] >= 0f) 1 else 0)
            if (kept == 3 || (kept > 0 && !more)) { add(a, b, c, (dep[a] + dep[b] + dep[c]) / 3); continue }
            // What the cut hides is drawn too, behind everything: inside the body's outline, it shows only through the pinholes
            // where the cut's straight edges fall short of the crease.
            add(a, b, c, Float.MAX_VALUE)
            if (kept > 0) clip(a, b, c)
        }
        var rims = 0
        for (k in edges.indices step 6) if (front[edges[k + 2]] != (edges[k + 5] >= 0 && front[edges[k + 5]])) rims++
        if (!more || nv + 2 * rims > Short.MAX_VALUE) return
        grow(2 * rims, 2 * rims)
        for (k in edges.indices step 6) {
            val one = front[edges[k + 2]]
            val two = edges[k + 5] >= 0 && front[edges[k + 5]]
            if (one == two) continue
            val a = base + if (one) edges[k] else edges[k + 4]
            val b = base + if (one) edges[k + 1] else edges[k + 3]
            if (cut[a] < 0f || cut[b] < 0f) continue
            val ex = xy[b * 2] - xy[a * 2]; val ey = xy[b * 2 + 1] - xy[a * 2 + 1]
            val len = sqrt(ex * ex + ey * ey)
            if (len < 1e-3f) continue
            val px = -ey / len * FRINGE; val py = ex / len * FRINGE
            val a2 = nv; val b2 = nv + 1
            xy[a2 * 2] = xy[a * 2] + px; xy[a2 * 2 + 1] = xy[a * 2 + 1] + py; col[a2] = col[a] and 0xFFFFFF; shade[a2] = shade[a] and 0xFFFFFF; dep[a2] = dep[a]
            xy[b2 * 2] = xy[b * 2] + px; xy[b2 * 2 + 1] = xy[b * 2 + 1] + py; col[b2] = col[b] and 0xFFFFFF; shade[b2] = shade[b] and 0xFFFFFF; dep[b2] = dep[b]
            nv += 2
            val d = (dep[a] + dep[b]) / 2
            add(a, b, b2, d); add(a, b2, a2, d)
        }
    }

    /** Queues the part of triangle ([a], [b], [c]) that its cut keeps, as one triangle or two. */
    private fun clip(a: Int, b: Int, c: Int) {
        var n = 0
        for (e in 0..2) {
            val p = if (e == 0) a else if (e == 1) b else c
            val q = if (e == 0) b else if (e == 1) c else a
            if (cut[p] >= 0f) poly[n++] = p
            if ((cut[p] >= 0f) != (cut[q] >= 0f)) poly[n++] = between(p, q)
        }
        var d = 0f
        for (i in 0 until n) d += dep[poly[i]]
        add(poly[0], poly[1], poly[2], d / n)
        if (n == 4) add(poly[0], poly[2], poly[3], d / n)
    }

    /** A new vertex where the edge from [p] to [q] crosses its cut. */
    private fun between(p: Int, q: Int): Int {
        val s = cut[p] / (cut[p] - cut[q])
        fun at(k: Int) = wp[p * 3 + k] + (wp[q * 3 + k] - wp[p * 3 + k]) * s
        val v = nv++
        pr.project(at(0), at(1), at(2))
        xy[v * 2] = pr.sx; xy[v * 2 + 1] = pr.sy; dep[v] = pr.depth; col[v] = mix(col[p], col[q], s); shade[v] = 0
        return v
    }
}

/** [a] moved [t] of the way to [b], channel by channel. */
private fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(s: Int) = ((a shr s and 0xFF) + (((b shr s and 0xFF) - (a shr s and 0xFF)) * t)).toInt() shl s
    return (a.toLong() and 0xFF000000).toInt() or ch(16) or ch(8) or ch(0)
}

/**
 * The exercise as a 3D figure you can orbit: drag to turn around it or look down on it, double-tap to reset; let go and the
 * camera circles it again. It loops one rep at the lift's real tempo; [still] freezes it at one point of the rep and stops
 * the frame clock and the circling (thumbnails, tests). [time], read every frame, sets the seconds into the rep instead of
 * the frame clock, for a player that keeps the figure in step with its own timer. [focus] lights where a stretch is felt;
 * [mirror] shows the move done on the other side.
 */
@Composable
fun FormView(
    motion: Motion,
    modifier: Modifier = Modifier,
    focus: Focus? = null,
    mirror: Boolean = false,
    still: Float? = null,
    time: (() -> Double)? = null,
    orbit: Orbit = remember(motion) { Orbit(motion.yaw ?: DEFAULT_YAW) },
) {
    val p = LocalPalette.current
    val scene = remember { Scene() }
    val tones = remember(p) { Tones(p) }
    var clock by remember { mutableLongStateOf(0L) }
    // A kept tab out of sight stops the clock, so it runs no frames there.
    if (still == null && LocalTabShown.current.value) LaunchedEffect(motion, orbit) {
        val start = withFrameNanos { it }
        var last = start
        while (true) withFrameNanos {
            clock = it - start
            orbit.spin((it - last) / 1e9f)
            last = it
        }
    }
    // Keyed on the orbit: a new motion brings a new orbit, and a gesture bound to the old one would turn nothing on screen.
    val gestures = if (still != null) Modifier else Modifier
        .pointerInput(orbit) {
            detectDragGestures(
                onDragStart = { orbit.held = true },
                onDragEnd = { orbit.held = false },
                onDragCancel = { orbit.held = false },
            ) { change, d -> change.consume(); orbit.drag(d.x, d.y) }
        }
        .pointerInput(orbit) { detectTapGestures(onDoubleTap = { orbit.reset() }) }
    // The mirror flips the drawn picture, and the drag with it, so the figure still turns under the finger.
    Canvas(modifier.clipToBounds().semantics { contentDescription = "3D exercise form. Drag to look around it, double-tap to reset." }.graphicsLayer(scaleX = if (mirror) -1f else 1f).then(gestures)) {
        val sec = clock / 1e9
        val d = still?.toDouble() ?: motion.tempo.at(time?.invoke() ?: sec)
        val breath = if (still != null) 0.0 else 0.5 - 0.5 * cos(2 * PI * sec / BREATH_SEC)
        drawIntoCanvas { scene.draw(it.nativeCanvas, size.width, size.height, motion, d, breath, orbit, tones, focus) }
    }
}

/** A collapsed "Show form" row that opens the exercise's 3D figure inline; it loads and animates nothing until opened, and is absent for an exercise with no motion. */
@Composable
fun FormToggle(exId: String, modifier: Modifier = Modifier) {
    val id = Motions.resolve(exId) ?: return
    val p = LocalPalette.current
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        Pressable({ open = !open }, Modifier.fillMaxWidth().heightIn(min = 44.dp), label = if (open) "Hide form" else "Show form", scale = 1f) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Txt(if (open) "Hide form" else "Show form", Type.subhead, color = p.fg2)
                Spacer(Modifier.width(Space.xs))
                Icon(Icons.chevronRight, p.fg2, 16.dp, Modifier.rotate(if (open) 90f else 0f))
            }
        }
        if (open) FormView(
            Motions.of(id),
            Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(Radius.lg)).background(p.fill),
        )
    }
}
