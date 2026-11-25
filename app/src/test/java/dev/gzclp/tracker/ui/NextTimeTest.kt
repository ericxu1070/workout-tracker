package dev.gzclp.tracker.ui

import dev.gzclp.tracker.engine.CycleState
import dev.gzclp.tracker.engine.LiftState
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.engine.Tier
import dev.gzclp.tracker.engine.Wave
import dev.gzclp.tracker.log.AppState
import org.junit.Assert.assertEquals
import org.junit.Test

/** An exercise detail's "Next time" shows only the program being run, though the log keeps every program's progress. */
class NextTimeTest {
    private val split = Programs.of("bro").copy(id = "mine", name = "Mine", from = "bro")
    private val all = AppState(
        gz = mapOf(("bench" to Tier.T1) to LiftState(175), ("bench" to Tier.T2) to LiftState(135)),
        cycle = mapOf("bench" to CycleState(150)),
        custom = mapOf(("bench" to Wave.DEFAULT.key) to CycleState(140)),
        customPrograms = mapOf(split.id to split),
    )

    private fun labels(programId: String) = nextTime(all.copy(programId = programId), "bench").map { it.first }

    @Test fun eachProgramShowsOnlyItsOwnRows() {
        assertEquals(listOf("GZCLP T1", "GZCLP T2"), labels(Programs.GZCLP))
        assertEquals(listOf("Cycle"), labels("bro"))
        assertEquals(listOf(Wave.DEFAULT.display), labels(split.id))
    }
}
