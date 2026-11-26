package dev.gzclp.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performTouchInput
import dev.gzclp.tracker.log.CardioRun
import dev.gzclp.tracker.log.Muscle
import dev.gzclp.tracker.log.muscleWindow
import dev.gzclp.tracker.log.muscleWork
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MuscleMapTest {
    @get:Rule val rule = createComposeRule()
    private val sunday = Demo.today.minusDays(1)
    private val idle = Demo.state.copy(active = null)

    @Test fun theAppOpensOnExercisesUnlessASessionIsRunning() {
        assertEquals(Tab.Exercises, homeTab(idle))
        assertEquals(Tab.Workout, homeTab(Demo.state))
        assertEquals(Tab.Workout, homeTab(idle.copy(cardioActive = CardioRun("c", "run", "Run", Demo.now))))
    }

    @Test fun tappingAMuscleShowsItsNameAndSets() {
        val week = muscleWindow(sunday, month = false)
        val chest = idle.muscleWork(week.first, week.second).getValue(Muscle.CHEST).sets
        rule.setContent { GzTheme(Palettes.tokyo) { ExercisesScreen(idle, {}, {}, {}, today = sunday) } }
        val map = rule.onNodeWithContentDescription("Muscles worked: ", substring = true)
        map.assertExists()
        // The front figure's chest, in a 340 dp tall chart on a 411 dp wide screen.
        map.performTouchInput { click(percentOffset(0.244f, 0.255f)) }
        rule.onNodeWithText("Chest").assertExists()
        rule.onNodeWithText("$chest sets").assertExists()
        // Between the figures is no muscle, so the name goes.
        map.performTouchInput { click(percentOffset(0.5f, 0.5f)) }
        rule.onAllNodesWithText("Chest").assertCountEquals(0)
    }
}
