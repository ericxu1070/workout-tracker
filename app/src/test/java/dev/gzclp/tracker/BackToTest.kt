package dev.gzclp.tracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackToTest {
    /** Opens [to] at [arg] from the screen on top of [stack], the way push() does, and returns the new stack. */
    private fun open(stack: List<Screen>, to: Route, arg: String = ""): List<Screen> {
        val origins = stack.dropLast(1)
        val after = originsAfterPush(origins, stack.last(), to)
        return after + screenOf(to, arg, after)
    }

    /** Back from the top of [stack], the way pop() does, and returns the new stack. */
    private fun back(stack: List<Screen>): List<Screen> {
        val here = stack.last()
        val origins = stack.dropLast(1)
        val to = backTo(here, origins)
        val left = if (parentOf(here.route!!) == null) origins.dropLast(1) else origins
        return left + screenOf(to.route, to.arg, left)
    }

    private val tabs = listOf(Screen(null))

    /** Back always lands on a shallower screen, so a Back swipe slides the screen out the way it came in. */
    @Test fun backFromEveryScreenLandsShallower() {
        for (r in Route.entries) for (from in listOf(null, Route.Session, Route.StretchRoutine)) {
            val start = if (from == null) tabs else open(tabs, from, "x")
            val stack = open(start, r, "a/b")
            val to = backTo(stack.last(), stack.dropLast(1))
            assertTrue("$r from $from -> $to", to.depth < stack.last().depth)
        }
    }

    @Test fun aLiftOpenedFromStrengthGoesBackToStrength() {
        val strength = open(tabs, Route.Strength)
        val squat = open(strength, Route.Exercise, "squat")
        assertEquals(Route.Strength, squat.last().from)
        assertEquals(strength, back(squat))
        assertEquals(tabs, back(back(squat)))
    }

    @Test fun anExerciseOpenedFromAWorkoutGoesBackToThatWorkout() {
        val session = open(tabs, Route.Session, "s42")
        val row = open(session, Route.Exercise, "bench")
        assertEquals(Screen(Route.Session, "s42"), back(row).last())
        assertEquals(tabs, back(back(row)))
    }

    @Test fun backWalksTheWholeTrail() {
        val trail = open(open(open(open(tabs, Route.Exercise, "squat"), Route.Strength), Route.Exercise, "bench"), Route.Strength)
        val steps = generateSequence(trail) { if (it.last().route == null) null else back(it) }.map { it.last().route to it.last().arg }.toList()
        assertEquals(
            listOf(Route.Strength to "", Route.Exercise to "bench", Route.Strength to "", Route.Exercise to "squat", null to ""),
            steps,
        )
    }

    @Test fun thePlayerGoesBackToTheRoutineItWasStartedFrom() {
        assertEquals(Screen(Route.StretchRoutine, "desk"), back(open(open(tabs, Route.StretchRoutine, "desk"), Route.Stretch, "desk")).last())
        assertEquals(tabs, back(open(tabs, Route.Stretch, "desk")))
    }

    @Test fun settingsScreensGoBackToTheirParentAndAnewTrailForgetsTheOld() {
        val day = open(open(open(open(tabs, Route.Settings), Route.SettingsPage), Route.Split, "ppl"), Route.SplitDay, "ppl/push")
        assertEquals(Screen(Route.Split, "ppl"), back(day).last())
        assertEquals(listOf(Route.Split, Route.SettingsPage), listOf(back(day).last().route, back(back(day)).last().route))
        assertEquals(emptyList<Screen>(), originsAfterPush(listOf(Screen(Route.Session, "s")), Screen(null), Route.Exercise))
    }
}
