package dev.gzclp.tracker

import org.junit.Assert.assertTrue
import org.junit.Test

class BackToTest {
    /** Back always lands on a shallower screen, so a Back swipe slides the screen out the way it came in. */
    @Test fun backFromEveryScreenLandsShallower() {
        for (r in Route.entries) for (fromPage in listOf(false, true)) {
            val to = backTo(r, fromPage)
            assertTrue("$r -> $to", (to?.depth ?: 0) < r.depth)
        }
    }
}
