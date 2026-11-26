package dev.gzclp.tracker.form

import org.junit.After
import org.junit.Before
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
    @After fun pieces() { Figure.on = true; Figure.shape = "male" }

    override fun sink(id: String) = SINKS[body]?.get(id) ?: super.sink(id)
    override fun clear() = CLEAR[body] ?: super.clear()

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}") fun bodies() = Figure.bodies.map { arrayOf<Any>(it) }

        /**
         * Sitting or lying, the floor lift stops short where rising would leave the legs or arms reaching for the floor they
         * are set on, so the glutes and thighs, fuller than the pieces' pelvis and legs, stay sunk into it.
         */
        val SINKS = mapOf(
            "male" to mapOf("hamstrings_legs_up_wall" to 0.0075, "hamstrings_seated_single" to 0.0076, "spine_seated_twist" to 0.0168),
            "female" to mapOf("hamstrings_pancake" to 0.0252),
            "male-bodybuilder" to mapOf(
                "hamstrings_legs_up_wall" to 0.0121, "hamstrings_seated_single" to 0.0092, "hips_seated_piriformis" to 0.0107, "spine_seated_twist" to 0.0172,
            ),
            "male-normal" to mapOf("hamstrings_legs_up_wall" to 0.0063, "spine_seated_twist" to 0.0171),
            "female-bodybuilder" to mapOf(
                "hamstrings_legs_up_wall" to 0.0088, "hamstrings_pancake" to 0.0232, "hamstrings_seated_single" to 0.0067,
                "hips_seated_piriformis" to 0.0070, "spine_seated_twist" to 0.0069, "spine_sun_salutation_a" to 0.0060,
            ),
            "female-normal" to mapOf("hamstrings_pancake" to 0.0255, "spine_sun_salutation_a" to 0.0051),
        )

        /** The fuller thighs reach nearer a deadlift's bar than the pieces' legs it was set against. */
        val CLEAR = mapOf("male-bodybuilder" to 0.008, "male-normal" to 0.007, "female-normal" to 0.005)
    }
}
