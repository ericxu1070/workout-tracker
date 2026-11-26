package dev.gzclp.tracker.form

import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.stretch.StretchMoves
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.math.sqrt

/** Each check on every body. */
@RunWith(Parameterized::class)
class FigureTest(private val body: String) {
    init { Figure.shape = body }
    private val mesh = Figure.mesh
    private val pos = FloatArray(mesh.verts * 3)

    @Before fun on() { Figure.on = true; Figure.shape = body }
    @After fun off() { Figure.on = false; Figure.shape = "male" }

    private fun at(i: Int) = V(pos[i * 3].toDouble(), pos[i * 3 + 1].toDouble(), pos[i * 3 + 2].toDouble())

    @Test fun theRestPoseIsTheMeshAsFitted() {
        Figure.pose(Figure.REST, pos)
        val off = (0 until mesh.verts * 3).maxOf { kotlin.math.abs(pos[it] - mesh.pos[it]).let { d -> if (d.isNaN()) Float.MAX_VALUE else d } }
        assertTrue("$off m", off < 1e-5f)
    }

    /** A bench's seat height is found with only the pelvis placed, so the pelvis's own skin must already be there. */
    @Test fun aHalfBuiltPoseStillHasTheBonesItPlaced() {
        val p = Poser().apply { pelvis(V(0.0, 0.5, 0.0)) }
        assertTrue(p.lowest(listOf(Bone.PELVIS)) in 0.3..0.5)
    }

    @Test fun aClosedHandWrapsTheGrip() {
        val p = Poser()
        Motions.of("deadlift").pose(p, 0.0)
        for (s in SIDES) {
            assertTrue(p.closed(s))
            Figure.pose(p, pos)
            val g = p.hand(s)
            val near = (0 until mesh.verts).filter { Figure.lead[it] == Bone.hand(s).ordinal }.minOf { (at(it) - g).len }
            // The fist is closed tighter than a 28 mm bar, so the fingers sink into it: they must reach its surface, and not past its axis.
            assertTrue("nearest knuckle ${"%.4f".format(near)} m from the bar's axis", near in 0.005..0.014)
        }
    }

    /** Linear blend skinning thins and stretches a joint bent far, into long flat facets; none grows much past the longest edge at rest. */
    @Test fun noEdgeStretchesIntoATear() {
        val ids = Catalog.all.map { it.id } + StretchMoves.ALL.map { it.id }
        val p = Poser()
        val e = mesh.edges
        Figure.pose(Figure.REST, pos)
        val rest = (e.indices step 6).maxOf { (at(e[it]) - at(e[it + 1])).len }
        var worst = 0.0; var where = ""
        for (id in ids) for (k in 0..20) {
            Motions.of(id).pose(p, k / 20.0)
            Figure.pose(p, pos)
            for (j in e.indices step 6) {
                val d = (at(e[j]) - at(e[j + 1])).len
                if (d > worst) { worst = d; where = "$id d=${k / 20.0} ${Bone.entries[Figure.lead[e[j]]]}" }
            }
        }
        println("FIGURE longest edge ${"%.4f".format(worst)} m at $where, ${"%.4f".format(rest)} m at rest")
        assertTrue("an edge ${"%.3f".format(worst)} m long at $where", worst < (TEAR[body] ?: STRETCH * rest))
    }

    /** Seen from behind at the bottom of a squat, a crotch left on the pelvis swings back level with the glutes: a bump between them. */
    @Test fun theCrotchStaysTuckedBetweenTheGlutesThroughASquat() {
        val hip = Figure.REST[Bone.PELVIS].o.y
        fun rest(i: Int, c: Int) = mesh.pos[i * 3 + c].toDouble()
        val crotch = (0 until mesh.verts).filter { kotlin.math.abs(rest(it, 0)) < 0.02 && rest(it, 1) in hip - 0.12..hip - 0.02 }
        val glutes = (0 until mesh.verts).filter { kotlin.math.abs(rest(it, 0)) in 0.04..0.12 && rest(it, 2) < -0.06 && rest(it, 1) in hip - 0.08..hip + 0.02 }
        val p = Poser()
        var least = Double.MAX_VALUE
        // Below standing only: standing, the crotch set takes in the inner faces of round glutes, a cleft and not the bump.
        for (k in 1..20) {
            Motions.of("squat").pose(p, k / 20.0)
            Figure.pose(p, pos)
            least = minOf(least, crotch.minOf { at(it).z } - glutes.minOf { at(it).z })
        }
        println("FIGURE crotch at least ${"%.4f".format(least)} m in front of the glutes")
        assertTrue("the crotch comes within ${"%.4f".format(least)} m of the back of the glutes", least > 0.03)
    }

    /** Linear blending collapses a joint twisted far: the upper arm turns about half a turn over the top of a band pass. */
    @Test fun theShouldersKeepTheirGirthHoweverTheArmsTurn() {
        val ids = Catalog.all.map { it.id } + StretchMoves.ALL.map { it.id }
        val p = Poser()
        var least = Double.MAX_VALUE; var where = ""
        for (s in SIDES) {
            val arm = Bone.arm(s)
            val joint = Figure.REST[arm].o
            // The flesh around the shoulder, 5 to 10 cm from the joint, held by the chest or the upper arm.
            val ring = (0 until mesh.verts).filter { Figure.lead[it] in listOf(Bone.CHEST.ordinal, arm.ordinal) && V(mesh.pos[it * 3] - joint.x, mesh.pos[it * 3 + 1] - joint.y, mesh.pos[it * 3 + 2] - joint.z).len in 0.05..0.10 }
            for (id in ids) for (k in 0..20) {
                Motions.of(id).pose(p, k / 20.0)
                Figure.pose(p, pos)
                val now = p[arm].o
                for (i in ring) {
                    val r = (at(i) - now).len / V(mesh.pos[i * 3] - joint.x, mesh.pos[i * 3 + 1] - joint.y, mesh.pos[i * 3 + 2] - joint.z).len
                    if (r < least) { least = r; where = "$id d=${k / 20.0} $arm" }
                }
            }
        }
        println("FIGURE shoulder flesh keeps at least ${"%.2f".format(least)} of its reach from the joint, at $where")
        // A twist collapsed through the joint kept a thirtieth. Linear blending still pinches a shoulder swung far overhead
        // (the triceps stretch keeps a fifth), as it did before the twist was taken out of it.
        assertTrue("the shoulder flesh falls to ${"%.2f".format(least)} of its reach from the joint at $where", least > 0.15)
    }

    /** The pieces are drawn unless the mesh is asked for, so opening a move must not wait on reading and fitting it. */
    @Test fun thePiecesNeverReadTheMesh() {
        val read = mutableListOf<String>()
        val urls = System.getProperty("java.class.path").split(java.io.File.pathSeparator).map { java.io.File(it).toURI().toURL() }
        // A fresh copy of the app's classes (over the JDK's alone), so what earlier tests loaded does not count.
        val fresh = object : java.net.URLClassLoader(urls.toTypedArray(), null) {
            override fun getResourceAsStream(name: String): java.io.InputStream? = super.getResourceAsStream(name).also { read += name }
        }
        val motions = fresh.loadClass(Motions::class.java.name)
        val poser = fresh.loadClass(Poser::class.java.name)
        val motion = motions.getMethod("of", String::class.java).invoke(motions.getField("INSTANCE").get(null), "squat")
        motion.javaClass.getMethod("pose", poser, Double::class.java, Double::class.java).invoke(motion, poser.getConstructor().newInstance(), 0.5, 0.0)
        assertTrue("opening the squat as the pieces read $read", read.none { it.endsWith(".bin") })
    }

    @Test fun theWallCalfStretchLightsTheLeftCalfAlone() {
        val share = StretchMoves.WALL_CALF_STRETCH.focus!!.figure
        val lit = (0 until mesh.verts).filter { share[it] > 0.5f }.map { Bone.entries[Figure.lead[it]] }.toSet()
        assertEquals(setOf(Bone.THIGH_L, Bone.SHIN_L), lit)
        val calf = (0 until mesh.verts).filter { Figure.lead[it] == Bone.SHIN_L.ordinal }.maxOf { share[it] }
        assertTrue(calf > 0.95f)
    }

    @Test fun eachGenderAndBuildIsABodyWithItsOwnHair() {
        val all = listOf(false, true).flatMap { f -> listOf("athletic", "bodybuilder", "normal").map { Figure.body(f, it) } }
        assertEquals(all.toSet(), Figure.bodies.toSet())
        val hair = Figure.hair!!
        Figure.shape = if (body.startsWith("female")) "male" else "female"
        assertTrue("$body wears the other sex's hair", Figure.hair!!.verts != hair.verts)
    }

    /** The hair rides the head as one piece, so a head laid on the floor could push its back (or a ponytail) under it. */
    @Test fun theHairNeverGoesBelowTheFloor() {
        val placed = FloatArray(Figure.hair!!.verts * 3)
        val p = Poser()
        val bad = mutableListOf<String>()
        for (id in Catalog.all.map { it.id } + StretchMoves.ALL.map { it.id }) for (k in 0..20) {
            Motions.of(id).pose(p, k / 20.0)
            Figure.placeHair(p[Bone.HEAD], placed)
            val low = (1 until placed.size step 3).minOf { placed[it].toDouble() }
            if (low < minOf(-0.005, p.lowest(listOf(Bone.HEAD)))) bad += "$id d=${k / 20.0}: y=${"%.3f".format(low)}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    companion object {
        const val STRETCH = 1.5

        /**
         * Measured (2026-11-26), where the limit falls short: the woman's normal build has the shortest longest edge at rest,
         * so the same inner-thigh edge every body stretches to about 0.10 m (the weights step across it) counts against her.
         */
        val TEAR = mapOf("female-normal" to 0.1016)

        @JvmStatic @Parameterized.Parameters(name = "{0}") fun bodies() = Figure.bodies
    }
}
