package dev.gzclp.tracker.ui

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The system Back swipe drives a pushed screen's pop (finding 22). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class PredictiveBackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var route by mutableStateOf<String?>("detail")
    private var sheet by mutableStateOf(false)

    private fun show() = rule.setContent {
        BackHandler(sheet) { sheet = false }
        BackStack(route, { if (it == null) 0 else 1 }, { !sheet && route != null }, { null }, { route = null }) { r ->
            Box(Modifier.fillMaxSize().background(Color.White).testTag(r ?: "tabs"))
        }
    }

    private fun swipe(progress: Float) = rule.runOnIdle {
        rule.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 400f, progress, BackEventCompat.EDGE_LEFT))
    }

    private fun start() = rule.runOnIdle {
        rule.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 400f, 0f, BackEventCompat.EDGE_LEFT))
    }

    @Test fun aPushedScreenFollowsTheSwipeAndACancelPutsItBack() {
        show()
        start()
        swipe(0.5f)
        rule.waitForIdle()
        val left = rule.onNodeWithTag("detail").getBoundsInRoot().left
        assertEquals(411f / 2, left.value, 2f)
        rule.onNodeWithTag("tabs").assertExists()

        rule.runOnIdle { rule.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        rule.waitForIdle()
        assertEquals(0f, rule.onNodeWithTag("detail").getBoundsInRoot().left.value, 0.5f)
        rule.onNodeWithTag("tabs").assertDoesNotExist()
        assertEquals("detail", route)
    }

    @Test fun lettingGoPops() {
        show()
        start()
        swipe(0.3f)
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(null, route)
        rule.onNodeWithTag("detail").assertDoesNotExist()
        rule.onNodeWithTag("tabs").assertExists()
    }

    @Test fun backWithASheetOpenClosesTheSheetFirst() {
        sheet = true
        show()
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertTrue(!sheet)
        assertEquals("detail", route)
        rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertEquals(null, route)
    }
}
