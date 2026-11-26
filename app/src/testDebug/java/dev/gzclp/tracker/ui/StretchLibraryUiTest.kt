package dev.gzclp.tracker.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.gzclp.tracker.log.Commands
import dev.gzclp.tracker.log.Store
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchRoutine
import dev.gzclp.tracker.stretch.StretchRoutines
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.OffsetDateTime

/** The routine list: After workout and a few popular ones, the library behind Show more, and each preset's info in place. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class StretchLibraryUiTest {
    @get:Rule val rule = createComposeRule()

    private val popular = StretchRoutines.popular.first()
    private val library = StretchRoutines.more.first()

    private fun show(defaultId: String = StretchRoutines.ID_AFTER_WORKOUT, onOpen: (StretchRoutine) -> Unit = {}) {
        rule.setContent { StretchScreen(null, defaultId, {}, onOpen, {}) }
    }

    @Test fun theLibraryWaitsBehindShowMore() {
        show()
        rule.onNodeWithText(popular.name).assertExists()
        rule.onAllNodesWithText(library.name).assertCountEquals(0)
        rule.onNodeWithText("Show more").performClick()
        rule.onNodeWithText(library.name).assertExists()
        rule.onNodeWithText("Show less").performClick()
        rule.onAllNodesWithText(library.name).assertCountEquals(0)
    }

    @Test fun aDefaultFromTheLibraryOpensTheLibrary() {
        show(library.id)
        rule.onNodeWithText("Show less").assertExists()
    }

    @Test fun aCustomRoutineStaysInSightAndItsInfoOpensItsPage() {
        val store = Store(File.createTempFile("gzclp", ".jsonl").apply { delete(); deleteOnExit() })
        store.dispatch(OffsetDateTime.parse("2026-11-25T20:00:00-08:00")) { Commands.newStretchRoutine(it, "Evening", listOf(StretchMoves.PIGEON_POSE)) }
        val state = store.state.value
        var opened: StretchRoutine? = null
        rule.setContent { StretchScreen(state, StretchRoutines.ID_AFTER_WORKOUT, {}, { opened = it }, {}) }
        rule.onNodeWithText("Show more").assertExists()
        rule.onNodeWithText("Evening").assertExists()
        rule.onNodeWithContentDescription("Evening moves").performClick()
        assertEquals("Evening", opened?.name)
    }

    @Test fun infoOpensTheGoalInPlaceAndLeadsToTheMoves() {
        var opened: StretchRoutine? = null
        show(onOpen = { opened = it })
        rule.onAllNodesWithText(popular.goal).assertCountEquals(0)
        rule.onNodeWithContentDescription("About ${popular.name}").performClick()
        rule.onNodeWithText(popular.goal).assertExists()
        rule.onNodeWithText(popular.pick).assertExists()
        assertEquals(null, opened)
        rule.onNodeWithText("See moves").performClick()
        assertEquals(popular.id, opened?.id)
        rule.onNodeWithContentDescription("About ${popular.name}").performClick()
        rule.onAllNodesWithText(popular.goal).assertCountEquals(0)
    }
}
