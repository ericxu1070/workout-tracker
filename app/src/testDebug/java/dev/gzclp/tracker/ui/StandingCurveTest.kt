package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.engine.NormLift
import dev.gzclp.tracker.engine.Norms
import dev.gzclp.tracker.engine.Peer
import dev.gzclp.tracker.log.exerciseLogs
import dev.gzclp.tracker.log.standing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/** Hold and drag on the strength curve, read back through the value screen readers hear; the Strength screen's rows and stepper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class StandingCurveTest {
    @get:Rule val rule = createComposeRule()
    private val out = File(System.getProperty("gzclp.shots") ?: "build/shots")
    private val peer = Peer(female = false, bodyKg = 82.0, age = 34.0)
    private val mine = standing(NormLift.SQUAT, 315, peer)
    private val norm = Norms.of(false, NormLift.SQUAT)
    private fun lbAt(percent: Int) = (norm.liftAt(percent / 100.0, peer) / Norms.KG_PER_LB).roundToInt()
    private fun reading(percent: Int) = "${ordinal(percent)} percentile, ${lbAt(percent)} lb, ${Fmt.ratio(norm.liftAt(percent / 100.0, peer) / peer.bodyKg)} body weight"

    private fun state(text: String?) = SemanticsMatcher("stateDescription is $text") {
        it.config.getOrElseNullable(SemanticsProperties.StateDescription) { null } == text
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = rule.setContent {
        val p = Palettes.jade
        GzTheme(p) { Box(Modifier.background(p.bg).padding(Space.lg)) { content() } }
    }

    @Test fun ordinalsEndAsSpoken() {
        assertEquals(listOf("1st", "2nd", "3rd", "4th", "11th", "12th", "13th", "21st", "62nd", "93rd", "99th"), listOf(1, 2, 3, 4, 11, 12, 13, 21, 62, 93, 99).map(::ordinal))
    }

    @Test fun theCurveNamesItsGroupTheMedianAndTheLifter() {
        show { StandingCurve(mine) }
        rule.onNodeWithContentDescription("Squat among men at 181 lb, age 34: median ${lbAt(50)} lb, you 315 lb, ${ordinal(mine.rank)} percentile").assert(state(null))
    }

    @Test fun draggingReadsEachPercentileAndReleasingHidesIt() {
        show { StandingCurve(mine) }
        val chart = rule.onNodeWithContentDescription("Squat among men", substring = true)
        // The plot spans the 0.5th to 99.5th percentile, so the median's weight sits at its share of that span.
        val lo = norm.liftAt(0.005, peer)
        val hi = norm.liftAt(0.995, peer)
        fun at(percent: Int) = ((norm.liftAt(percent / 100.0, peer) - lo) / (hi - lo)).toFloat()
        chart.performTouchInput {
            down(Offset(width * at(20), height / 2f))
            moveBy(Offset(40f, 0f))
            moveTo(Offset(width * at(50), height / 2f))
        }
        chart.assert(state(reading(50)))
        rule.onRoot().captureRoboImage(File(out, "charts/standing-scrub.png").path)
        chart.performTouchInput { moveTo(Offset(width * at(90), height / 2f)) }
        chart.assert(state(reading(90)))
        chart.performTouchInput { up() }
        chart.assert(state(null))
    }

    @Test fun aTapPinsAPercentileUntilTappedAgain() {
        show { StandingCurve(mine) }
        val chart = rule.onNodeWithContentDescription("Squat among men", substring = true)
        val lo = norm.liftAt(0.005, peer)
        val hi = norm.liftAt(0.995, peer)
        val x = ((norm.liftAt(0.75, peer) - lo) / (hi - lo)).toFloat()
        chart.performTouchInput { click(Offset(width * x, height / 2f)) }
        chart.assert(state(reading(75)))
        chart.performTouchInput { click(Offset(width * x, height / 2f)) }
        chart.assert(state(null))
    }

    @Test fun aLiftPastTheCurveStillDrawsAndRanksAtTheEnd() {
        show { StandingCurve(standing(NormLift.SQUAT, 900, peer)) }
        rule.onNodeWithContentDescription("you 900 lb, 99th percentile", substring = true).assertExists()
        rule.onRoot().captureRoboImage(File(out, "charts/standing-far.png").path)
    }

    @Test fun strengthStepsTheBirthYearFromThirtyAndOpensALift() {
        var year: Int? = null
        var opened: String? = null
        val idle = Demo.state.copy(active = null)
        rule.setContent { GzTheme(Palettes.tokyo) { StrengthScreen(idle, false, year, Demo.today, {}, { year = it }, { opened = it }, {}) } }
        rule.onNodeWithText("Not set").assertExists()
        rule.onNodeWithContentDescription("Later birth year").performClick()
        assertEquals(Demo.today.year - 30, year)
        rule.onNodeWithText("Deadlift").performClick()
        assertEquals("deadlift", opened)
    }

    @Test fun strengthWithoutAWeighInAsksForOne() {
        var logs = 0
        rule.setContent { GzTheme(Palettes.tokyo) { StrengthScreen(Demo.state.copy(active = null, bodyWeights = emptyMap()), false, 1992, Demo.today, {}, {}, {}, { logs++ }) } }
        rule.onNodeWithText("No body weight yet").assertExists()
        rule.onNodeWithText("Log weight").performClick()
        assertEquals(1, logs)
    }

    @Test fun aCompetitionLiftHasAPercentileViewAndOthersDoNot() {
        val idle = Demo.state.copy(active = null)
        val logs = idle.exerciseLogs()
        rule.setContent { GzTheme(Palettes.tokyo) { ExerciseScreen(idle, logs.first { it.id == "ohp" }, {}, {}) } }
        rule.onNodeWithText("Percentile").assertDoesNotExist()
    }

    @Test fun thePercentileViewShowsTheRankAndLeadsToStrength() {
        var strength = 0
        val idle = Demo.state.copy(active = null)
        rule.setContent { GzTheme(Palettes.tokyo) { ExerciseScreen(idle, idle.exerciseLogs().first { it.id == "squat" }, {}, {}, birthYear = 1992, today = Demo.today, onStrength = { strength++ }) } }
        rule.onNodeWithText("Percentile").performClick()
        rule.onNodeWithText("percentile", substring = true).assertExists()
        rule.onNodeWithContentDescription("Squat among men", substring = true).assertExists()
        rule.onNodeWithText("Strength").performClick()
        assertEquals(1, strength)
    }

    @Test fun aSetBirthYearClearsBackToNotSet() {
        var year: Int? = 1992
        var calls = 0
        val idle = Demo.state.copy(active = null)
        rule.setContent { GzTheme(Palettes.tokyo) { StrengthScreen(idle, false, year, Demo.today, {}, { year = it; calls++ }, {}, {}) } }
        rule.onNodeWithText("Clear birth year").performClick()
        assertEquals(1, calls)
        assertEquals(null, year)
    }

    @Test fun anUnsetBirthYearHasNothingToClear() {
        val idle = Demo.state.copy(active = null)
        rule.setContent { GzTheme(Palettes.tokyo) { StrengthScreen(idle, false, null, Demo.today, {}, {}, {}, {}) } }
        rule.onNodeWithText("Not set").assertExists()
        rule.onNodeWithText("Clear birth year").assertDoesNotExist()
    }

    @Test fun theAgeNeverBreaksAcrossLines() {
        val width = mutableIntStateOf(200)
        val ageless = Peer(female = false, bodyKg = 82.0, age = null)
        rule.setContent { GzTheme(Palettes.jade) { Column(Modifier.width(width.intValue.dp)) { StandingBlock(standing(NormLift.TOTAL, 1040, ageless), "Total 1040 lb") } } }
        for (w in 200..411 step 3) {
            width.intValue = w
            rule.waitForIdle()
            val node = rule.onNodeWithText("more than", substring = true).fetchSemanticsNode()
            val layouts = mutableListOf<TextLayoutResult>()
            node.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
            val layout = layouts.single()
            val text = layout.layoutInput.text.text
            val start = text.indexOf("ages")
            assertTrue("$w dp: $text", start >= 0)
            assertEquals("$w dp: \"ages 24 to 34\" split", layout.getLineForOffset(start), layout.getLineForOffset(text.length - 1))
        }
    }

    @Test fun aZeroPoundLiftStillDrawsTheCurve() {
        // Light and old enough that the plot reaches down to 0 lb, where the density was 0 / 0 and blanked the whole line.
        val light = Peer(female = false, bodyKg = 120 * Norms.KG_PER_LB, age = 75.0)
        var view: android.view.View? = null
        show { view = androidx.compose.ui.platform.LocalView.current; StandingCurve(standing(NormLift.SQUAT, 0, light)) }
        rule.onNodeWithContentDescription("you 0 lb", substring = true).assertExists()
        val img = rule.runOnIdle {
            val v = view!!
            android.graphics.Bitmap.createBitmap(v.width, v.height, android.graphics.Bitmap.Config.ARGB_8888).also { v.draw(android.graphics.Canvas(it)) }
        }
        val line = Palettes.jade.fg2
        fun near(c: Int, f: Float) = kotlin.math.abs(c / 255f - f) < 0.06f
        // Only the plot, above its axis: the labels under it share the line's color.
        val drawn = (0 until img.width).sumOf { x ->
            (0 until (img.height * 0.8f).toInt()).count { y -> img.getPixel(x, y).let { near(it shr 16 and 255, line.red) && near(it shr 8 and 255, line.green) && near(it and 255, line.blue) } }
        }
        assertTrue("curve pixels: $drawn", drawn > 200)
    }
}
