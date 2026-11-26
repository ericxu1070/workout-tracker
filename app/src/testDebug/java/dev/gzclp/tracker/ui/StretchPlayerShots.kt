package dev.gzclp.tracker.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.stretch.StretchMoves
import dev.gzclp.tracker.stretch.StretchPlayerState
import dev.gzclp.tracker.stretch.StretchRoutine
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the stretch player partway through a still hold and through a dynamic move's reps. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class StretchPlayerShots {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, routine: StretchRoutine, atMs: Long, p: Palette = Palettes.jade) {
        val out = System.getProperty("gzclp.shots") ?: return
        rule.mainClock.autoAdvance = false
        rule.setContent { GzTheme(p) { GuidedStretchPlayerScreen(routine, {}, {}) } }
        rule.mainClock.advanceTimeBy(atMs)
        rule.onRoot().captureRoboImage(File(out, "${p.id}-$name.png").path)
    }

    @Test fun stillHold() = shot(
        "60-stretch-player-hold", StretchRoutine("t", "Bedtime wind-down", "", "", listOf(StretchMoves.STANDING_FORWARD_FOLD, StretchMoves.PIGEON_POSE)),
        StretchPlayerState.READY_SEC * 1000L + 11_400,
    )

    /** Liquid Glass's player: the pause button stained glass. */
    @Test fun stillHoldGlass() = shot(
        "60-stretch-player-hold", StretchRoutine("t", "Bedtime wind-down", "", "", listOf(StretchMoves.STANDING_FORWARD_FOLD, StretchMoves.PIGEON_POSE)),
        StretchPlayerState.READY_SEC * 1000L + 11_400, Palettes.glass,
    )

    @Test fun dynamicReps() = shot(
        "61-stretch-player-dynamic", StretchRoutine("t", "Pre-training dynamic", "", "", listOf(StretchMoves.CAT_COW_STRETCH.copy(holdSec = 30, isDynamic = true), StretchMoves.LIZARD_LUNGE)),
        StretchPlayerState.READY_SEC * 1000L + 9_700,
    )
}
