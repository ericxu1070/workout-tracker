package dev.gzclp.tracker.form

import dev.gzclp.tracker.engine.Catalog
import dev.gzclp.tracker.stretch.StretchMoves
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

class FigureTest {
    private val mesh = Figure.mesh
    private val pos = FloatArray(mesh.verts * 3)

    @Before fun on() { Figure.on = true }
    @After fun off() { Figure.on = false }

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
        println("FIGURE longest edge ${"%.3f".format(worst)} m at $where, ${"%.3f".format(rest)} m at rest")
        assertTrue("an edge ${"%.3f".format(worst)} m long at $where", worst < STRETCH * rest)
    }

    /** Seen from behind at the bottom of a squat, a crotch left on the pelvis swings back level with the glutes: a bump between them. */
    @Test fun theCrotchStaysTuckedBetweenTheGlutesThroughASquat() {
        val hip = Figure.REST[Bone.PELVIS].o.y
        fun rest(i: Int, c: Int) = mesh.pos[i * 3 + c].toDouble()
        val crotch = (0 until mesh.verts).filter { kotlin.math.abs(rest(it, 0)) < 0.02 && rest(it, 1) in hip - 0.12..hip - 0.02 }
        val glutes = (0 until mesh.verts).filter { kotlin.math.abs(rest(it, 0)) in 0.04..0.12 && rest(it, 2) < -0.06 && rest(it, 1) in hip - 0.08..hip + 0.02 }
        val p = Poser()
        var least = Double.MAX_VALUE
        for (k in 0..20) {
            Motions.of("squat").pose(p, k / 20.0)
            Figure.pose(p, pos)
            least = minOf(least, crotch.minOf { at(it).z } - glutes.minOf { at(it).z })
        }
        println("FIGURE crotch at least ${"%.4f".format(least)} m in front of the glutes")
        assertTrue("the crotch comes within ${"%.4f".format(least)} m of the back of the glutes", least > 0.03)
    }

    @Test fun theWallCalfStretchLightsTheLeftCalfAlone() {
        val share = StretchMoves.WALL_CALF_STRETCH.focus!!.figure
        val lit = (0 until mesh.verts).filter { share[it] > 0.5f }.map { Bone.entries[Figure.lead[it]] }.toSet()
        assertEquals(setOf(Bone.THIGH_L, Bone.SHIN_L), lit)
        val calf = (0 until mesh.verts).filter { Figure.lead[it] == Bone.SHIN_L.ordinal }.maxOf { share[it] }
        assertTrue(calf > 0.95f)
    }

    private companion object {
        const val STRETCH = 1.5
    }
}
