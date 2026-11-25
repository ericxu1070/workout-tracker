package dev.gzclp.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.gzclp.tracker.engine.Lift
import dev.gzclp.tracker.engine.Programs
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.OffsetDateTime

/** Headings stand alone: no subtitle repeats the program, and the open-session options need no captions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class DeclutterTest {
    @get:Rule val rule = createComposeRule()

    private val t0 = OffsetDateTime.parse("2026-11-25T18:00:00-08:00")

    private fun state() = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() }).also { store ->
        store.dispatch(t0) { Commands.setup(mapOf(Lift.SQUAT to 245, Lift.BENCH to 175, Lift.DEADLIFT to 295, Lift.OHP to 115), Programs.GZCLP) }
    }.state.value

    @Test fun nextWorkoutShowsOnlyTheDayAsItsHeading() {
        val s = state()
        rule.setContent { HomeScreen(s, {}, {}, {}) }
        rule.onNodeWithText(s.nextDay.title).assertExists()
        rule.onAllNodesWithText(s.program.name, substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("first workout", substring = true).assertCountEquals(0)
        rule.onAllNodesWithText("starting weight", substring = true).assertCountEquals(0)
    }

    @Test fun openSessionOptionsNameTheDayWithoutCaptions() {
        val s = state()
        rule.setContent { OpenSessionStartSheet(s, {}, {}, {}) }
        rule.onNodeWithText("Start ${s.nextDay.title}").assertExists()
        rule.onNodeWithText("Start blank").assertExists()
        for (gone in listOf("Choose how", "Prefills", "add exercises as you go")) {
            rule.onAllNodesWithText(gone, substring = true).assertCountEquals(0)
        }
    }

    @Test fun countSheetIsJustTheQuestionAndItsAnswers() {
        rule.setContent { OpenSessionCountSheet({}, {}, {}) }
        rule.onNodeWithText("Yes, count toward program").assertExists()
        rule.onAllNodesWithText("strength model", substring = true).assertCountEquals(0)
    }
}
