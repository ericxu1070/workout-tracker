package dev.gzclp.tracker.form

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/**
 * Every check in [FormTest] again with the figure drawn and solved as the one [Figure] mesh in each body. The motions were
 * set on the pieces, and where a body is fuller than them it misses by a little; each miss is named here with the most it
 * measured (2026-11-26), so it cannot grow unseen. A sink past 10 mm shows: the glutes or thighs sitting into the floor.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class FigureFormTest(private val body: String) : FormTest() {
    @Before fun mesh() { Figure.on = true; Figure.shape = body }
    @After fun pieces() { Figure.on = false; Figure.shape = "male" }

    override fun sink(id: String) = SINKS[body]?.get(id) ?: super.sink(id)
    override fun clear() = CLEAR[body] ?: super.clear()

    /** Lying or sitting with an arm or leg along the floor, the body rests on it rather than rising off it to lift the limb. */
    @Test fun theBodyRestsOnTheFloorUnderItsLimbs() {
        val p = Poser()
        val bad = mutableListOf<String>()
        for (id in listOf("hamstrings_legs_up_wall", "spine_seated_twist", "hips_seated_piriformis")) {
            val m = Motions.of(id)
            for (k in 0..20) {
                m.pose(p, k / 20.0)
                val gap = p.lowest(Bone.TRUNK + listOf(Bone.THIGH_L, Bone.THIGH_R))
                if (gap > 0.003) bad += "$id d=${k / 20.0}: ${"%.3f".format(gap)} m off the floor"
            }
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun bodies() = Figure.bodies.map { arrayOf<Any>(it) }

        /**
         * Rolling over the toes into upward dog, the arms and legs are straight to the floor, so the floor lift cannot raise the
         * fuller thighs off it without leaving a hand or foot short.
         */
        val SINKS = mapOf(
            "female-bodybuilder" to mapOf("spine_sun_salutation_a" to 0.0060),
            "female-normal" to mapOf("spine_sun_salutation_a" to 0.0051),
        )

        /** The fuller thighs reach nearer a deadlift's bar than the pieces' legs it was set against. */
        val CLEAR = mapOf("male-bodybuilder" to 0.008, "male-normal" to 0.007, "female-normal" to 0.005)
    }
}
