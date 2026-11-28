package dev.gzclp.tracker.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.gzclp.tracker.MainActivity
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.sync.Mirror
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Back from a screen opened from another pushed screen returns to that screen, not to the tabs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class BackToOriginTest {
    @get:Rule(order = 0) val seed = object : ExternalResource() {
        override fun before() {
            val log = File(ApplicationProvider.getApplicationContext<Application>().filesDir, Mirror.NAME)
            Demo.build(log)
            // A top-sets workout of A1 after the demo's open one, since only a top-sets workout's rows open their exercise.
            Store(log).run {
                val t = Demo.now.plusHours(1)
                dispatch(t) { Commands.end(it, t, null, counts = false) }
                dispatch(t) { Commands.startTop(it, t, it.program.days.first()) }
                for (ex in state.value.sessionExercises().take(2)) dispatch(t) { Commands.logSet(it, ex, 1, 3, ex.weight ?: 45, t, emptyMap(), sets = 5) }
                dispatch(t) { Commands.endTop(it, t, 2400) }
            }
        }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun tap(text: String) = rule.onAllNodesWithText(text).onFirst().performClick().also { rule.waitForIdle() }
    private fun shows(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring).onFirst().assertIsDisplayed()
    private fun gone(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring).fetchSemanticsNodes().let {
        org.junit.Assert.assertTrue("\"$text\" still shown", it.isEmpty())
    }
    private fun systemBack() = rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }.also { rule.waitForIdle() }
    private fun backButton() = rule.onNodeWithContentDescription("Back").performClick().also { rule.waitForIdle() }

    @Test fun aLiftOpenedFromStrengthOpensOnPercentileAndGoesBackToStrength() {
        tap("Exercises")
        tap("Strength")
        shows("Bench press")
        tap("Bench press")
        // The Percentile view's link back to Strength.
        shows("three lifts", substring = true)
        systemBack()
        gone("three lifts", substring = true)
        shows("Estimated 1RM", substring = true)
        tap("Squat")
        backButton()
        shows("Estimated 1RM", substring = true)
        backButton()
        gone("Estimated 1RM", substring = true)
        shows("Strength")
    }

    @Test fun anExerciseOpenedFromAWorkoutGoesBackToThatWorkout() {
        tap("History")
        tap("All")
        rule.onAllNodesWithText("Workout A1 · Mon, Nov 30").onFirst().performClick()
        rule.waitForIdle()
        shows("Duration")
        tap("Squat")
        gone("Duration")
        systemBack()
        shows("Duration")
        tap("Squat")
        backButton()
        shows("Duration")
        rule.activityRule.scenario.recreate()
        tap("Squat")
        rule.activityRule.scenario.recreate()
        systemBack()
        shows("Duration")
        systemBack()
        shows("All")
    }
}
