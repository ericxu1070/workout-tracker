package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.engine.Program
import dev.gzclp.tracker.engine.ProgramDay
import dev.gzclp.tracker.log.SessionRecord
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.OffsetDateTime

/** Tap and scrub on the charts, read back through the value screen readers hear; renders each picked state to build/shots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ChartTouchTest {
    @get:Rule val rule = createComposeRule()
    private val out = File(System.getProperty("gzclp.shots") ?: "build/shots")
    private val t0 = OffsetDateTime.parse("2026-10-01T18:00:00-07:00")
    private val day = ProgramDay("a", "A", emptyList())
    private val sessions = listOf(52, 61, 47, 70, 58, 66).mapIndexed { i, m ->
        SessionRecord("s$i", Program("gzclp", "GZCLP", listOf(day)), day, t0.plusDays(i * 2L), m * 60L, emptyList())
    }
    private val top = listOf(135f, 140f, 145f, 145f, 150f).mapIndexed { i, v -> Pt(t0.plusDays(i * 3L), v) }
    private val e1rm = listOf(160f, 166f, 171f, 172f, 178f).mapIndexed { i, v -> Pt(t0.plusDays(i * 3L), v) }

    private fun state(text: String?) = SemanticsMatcher("stateDescription is $text") {
        it.config.getOrElseNullable(SemanticsProperties.StateDescription) { null } == text
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = rule.setContent {
        val p = Palettes.jade
        GzTheme(p) { Box(Modifier.background(p.bg).padding(Space.lg)) { content() } }
    }

    @Test fun tappingABarShowsItsValueUntilTappedAgain() {
        show { BarChart(sessions) }
        val chart = rule.onNodeWithContentDescription("Oct\u00A01, 52 min", substring = true)
        chart.assert(state(null))
        // The six bars share the 331 dp after a 48 dp axis, in a 379 dp wide chart: bar 3 centers at 48 + 55.2 × 3.5.
        chart.performTouchInput { click(Offset((48 + 331f / 6 * 3.5f).dp.toPx(), height / 2f)) }
        chart.assert(state("Oct\u00A07, 1 hr 10 min"))
        rule.onRoot().captureRoboImage(File(out, "charts/bar-tap.png").path)
        chart.performTouchInput { click(Offset((48 + 331f / 6 * 3.5f).dp.toPx(), height / 2f)) }
        chart.assert(state(null))
        // The bars reach the plot's right edge, so a tap near it picks the last one.
        chart.performTouchInput { click(Offset(width - 8.dp.toPx(), height / 2f)) }
        chart.assert(state("Oct\u00A011, 1 hr 6 min"))
    }

    @Test fun draggingALineScrubsAndReleasingHidesIt() {
        show { LineChart(listOf(e1rm to Palettes.jade.fg2, top to Palettes.jade.accent), listOf("1RM", "Top")) }
        val chart = rule.onNodeWithContentDescription("Oct\u00A01, 1RM 160 lb, Top 135 lb", substring = true)
        chart.performTouchInput {
            down(Offset(60.dp.toPx(), height / 2f))
            moveBy(Offset(40.dp.toPx(), 0f))
            moveTo(Offset(width - 64.dp.toPx(), height / 2f))
        }
        chart.assert(state("Oct\u00A013, 1RM 178 lb, Top 150 lb"))
        chart.performTouchInput { moveTo(Offset(48.dp.toPx() + (width - 112.dp.toPx()) / 2, height / 2f)) }
        chart.assert(state("Oct\u00A07, 1RM 171 lb, Top 145 lb"))
        rule.onRoot().captureRoboImage(File(out, "charts/line-scrub.png").path)
        chart.performTouchInput { up() }
        chart.assert(state(null))
    }

    @Test fun aVerticalDragIsNotAScrub() {
        show { LineChart(listOf(top to Palettes.jade.accent), listOf("Top")) }
        val chart = rule.onNodeWithContentDescription("Oct\u00A01, Top 135 lb", substring = true)
        chart.performTouchInput {
            down(center)
            moveBy(Offset(0f, 60.dp.toPx()))
        }
        chart.assert(state(null))
        chart.performTouchInput { up() }
    }
}
