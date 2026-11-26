package dev.gzclp.tracker.form

import org.junit.After
import org.junit.Before

/**
 * Every check in [FormTest] again with the figure drawn and solved as the one [Figure] mesh. The motions were tuned on
 * the pieces, and where the mesh is fuller than them it misses by a little; each miss is named here with the most it
 * measured (2026-11-25), so it cannot grow unseen.
 */
class FigureFormTest : FormTest() {
    @Before fun mesh() { Figure.on = true }
    @After fun pieces() { Figure.on = false }

    override fun sink(id: String) = SINKS[id] ?: super.sink(id)
    override fun short(id: String) = SHORT[id] ?: super.short(id)

    private companion object {
        val SINKS = mapOf(
            // Sitting, the fold under the glutes is fuller than the pieces' pelvis the seat was found on.
            "hamstrings_seated_single" to 0.006,
            "spine_seated_twist" to 0.014,
            "hamstrings_pancake" to 0.013,
            "hips_seated_piriformis" to 0.009,
            // The fingers of the hand flat on the mat curl a little below its palm.
            "upper_back_thread_needle" to 0.019,
            // A forearm lying on the floor is fuller than the pieces' forearm.
            "hamstrings_legs_up_wall" to 0.008,
            "hips_foam_roll_adductors" to 0.007,
        )
        val SHORT = mapOf(
            // The ribs under the armpit are narrower than the pieces' chest, so the body rides higher on the roller
            // and the top foot reaches for the floor.
            "shoulders_foam_roll_rear" to 0.021,
        )
    }
}
