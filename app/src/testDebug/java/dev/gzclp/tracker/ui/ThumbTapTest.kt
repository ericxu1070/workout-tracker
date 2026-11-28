package dev.gzclp.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Debug-only (ui-test-manifest): a tap on a pill selector moves the pill as a drag does. It glides there in about a third of a second; the bar
 * swells and the pill lifts only while it travels, in Liquid Glass (Loaded) the pill clears to glass, and all of it is back at rest as the pill lands.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class ThumbTapTest {
    @get:Rule val rule = createComposeRule()

    private fun look() = rule.onNode(SemanticsMatcher.keyIsDefined(ThumbLookKey)).fetchSemanticsNode().config[ThumbLookKey]()

    /** Runs [frames] 16 ms frames with the clock held, returning how the thumb looks on each. */
    private fun watch(frames: Int) = List(frames) { rule.mainClock.advanceTimeByFrame(); look() }

    private fun settle() {
        rule.mainClock.autoAdvance = true
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
    }

    /** Taps [label] (down and up on its center) with the clock held, and returns how the thumb looks on each frame of the second after. */
    private fun tapAndWatch(label: String): List<ThumbLook> {
        rule.waitForIdle()
        val b = rule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(b.center); up() }
        return watch(60).also { settle() }
    }

    /**
     * Checks the slide from segment 0 to [to]: still on its way at 150 ms, grown mid-slide (clear glass only in [glass]), and back to the
     * plain resting pill on the frame it lands: the bar and pill are raised only while the pill travels.
     */
    private fun assertSlides(p: Palette, label: String, to: Int, content: @Composable () -> Unit) {
        rule.setContent { GzTheme(p) { content() } }
        val frames = tapAndWatch(label)
        assertTrue("still gliding at 150 ms: ${frames[8].x}", frames[8].x > 0.2f && frames[8].x < to * 0.9f)
        // Landed: within a twentieth of a segment, 4 dp on the tab bar, with only the glide's slow last creep to go.
        val land = frames.indexOfFirst { abs(it.x - to) < landed }
        assertTrue("lands at frame $land", land in 12..24)
        // Raised while it travels: at its fullest somewhere between the two segments.
        val peak = frames.take(land).maxBy { it.lift }
        assertTrue("grown mid-slide, not at an end: ${peak.x}", peak.x > 0.1f * to && peak.x < to - 0.1f)
        assertTrue("bar swells: ${peak.swell}", peak.swell > 1.02f)
        assertTrue("pill lifts: ${peak.lift}", peak.lift > 0.5f)
        if (p.glass) assertTrue("pill clears to glass: ${peak.glass}", peak.glass > 0.5f)
        else assertEquals("no glass in Performance", 0f, frames.maxOf { it.glass }, 0f)
        // At rest as it lands, with nothing left to shrink once it sits on the word.
        for (l in frames.drop(land)) {
            assertEquals("lift once landed", 0f, l.lift, 0.02f)
            assertEquals("swell once landed", 1f, l.swell, 0.001f)
            assertEquals("glass once landed", 0f, l.glass, 0.02f)
        }
        val rest = look()
        assertEquals(to.toFloat(), rest.x, 0f)
        assertEquals(1f, rest.swell, 0f)
        assertEquals(0f, rest.lift, 0f)
        assertEquals(0f, rest.glass, 0f)
        rule.onNodeWithText(label).assertIsSelected()
    }

    private val landed = 0.05f
    private var seg by mutableStateOf(0)
    private var tab by mutableStateOf(Tab.Workout)
    private var mode by mutableStateOf(0)
    private val modes = listOf("Lifting", "Cardio", "Stretching")

    @Composable private fun segmented() = Segmented(listOf("Day", "Week", "Month"), seg, { seg = it })
    @Composable private fun tabBar() = TabBar(tab) { tab = it }
    @Composable private fun workoutSwitch() = WorkoutPages(mode, { mode = it }, {}) { Txt("${modes[it]} page", Type.body) }

    @Test fun segmentedTapInLoaded() { assertSlides(Palettes.glass, "Month", 2) { segmented() }; assertEquals(2, seg) }
    @Test fun segmentedTapInPerformance() { assertSlides(Palettes.light, "Month", 2) { segmented() }; assertEquals(2, seg) }
    @Test fun tabBarTapInLoaded() { assertSlides(Palettes.glass, Tab.entries[3].label, 3) { tabBar() }; assertEquals(Tab.entries[3], tab) }
    @Test fun tabBarTapInPerformance() { assertSlides(Palettes.dark, Tab.entries[3].label, 3) { tabBar() }; assertEquals(Tab.entries[3], tab) }
    @Test fun workoutSwitchTapInLoaded() { assertSlides(Palettes.glass, "Stretching", 2) { workoutSwitch() }; assertEquals(2, mode) }
    @Test fun workoutSwitchTapInPerformance() { assertSlides(Palettes.light, "Stretching", 2) { workoutSwitch() }; assertEquals(2, mode) }

    // The nearest segment and the farthest: a short hop still grows, and a long slide has still let go by the time it lands.
    @Test fun aTapOnTheNextTab() { assertSlides(Palettes.glass, Tab.entries[1].label, 1) { tabBar() } }
    @Test fun aTapOnTheFarthestTab() { assertSlides(Palettes.dark, Tab.entries[4].label, 4) { tabBar() } }
    @Test fun aTapOnTheNextPage() { assertSlides(Palettes.glass, "Cardio", 1) { workoutSwitch() }; assertEquals(1, mode) }

    /** A tap the selection never follows (the caller keeps its tab) moves nothing, so nothing grows. */
    @Test fun aDeclinedTapGrowsNothing() {
        rule.setContent { GzTheme(Palettes.glass) { TabBar(Tab.Workout) {} } }
        for (l in tapAndWatch("History") + look()) assertEquals(ThumbLook(0f, 1f, 0f, 0f), l)
    }

    /** A tap on the segment already chosen moves nothing and grows nothing. */
    @Test fun tapOnTheChosenSegmentStaysPut() {
        rule.setContent { GzTheme(Palettes.glass) { segmented() } }
        for (l in tapAndWatch("Day")) assertEquals(ThumbLook(0f, 1f, 0f, 0f), l)
        assertEquals(0, seg)
    }

    /** A finger that catches the pill mid-slide takes it over from where it is: the pill grows on to its held size and never dips first. */
    @Test fun aGrabMidSlideNeverDips() {
        rule.setContent { GzTheme(Palettes.glass) { segmented() } }
        rule.waitForIdle()
        val bar = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val month = rule.onNodeWithText("Month").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(month.center); up() }
        val before = watch(6).last()
        assertTrue("caught mid-slide: $before", before.lift > 0.2f && before.lift < 1f && before.x < 1.5f)
        rule.onRoot().performTouchInput { down(Offset(bar.left + bar.width * (before.x + 0.5f) / 3, bar.center.y)) }
        val held = listOf(before) + watch(40)
        for ((a, b) in held.zipWithNext()) {
            assertTrue("lift dips: ${a.lift} to ${b.lift}", b.lift >= a.lift - 1e-3f)
            assertTrue("swell dips: ${a.swell} to ${b.swell}", b.swell >= a.swell - 1e-4f)
        }
        assertEquals(1f, held.last().lift, 0.02f)
        rule.onRoot().performTouchInput { up() }
        settle()
        assertEquals(1f, look().swell, 0f)
        assertEquals(0f, look().lift, 0f)
    }

    /** A second tap while the pill is still sliding sends it on to the new segment without a jump in its size, and it lands there at rest. */
    @Test fun aSecondTapMidSlideCarriesOn() {
        rule.setContent { GzTheme(Palettes.dark) { tabBar() } }
        rule.waitForIdle()
        val food = rule.onNodeWithText("Food").fetchSemanticsNode().boundsInRoot
        val upcoming = rule.onNodeWithText("Upcoming").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(food.center); up() }
        val first = watch(8)
        rule.onRoot().performTouchInput { down(upcoming.center); up() }
        val frames = listOf(first.last()) + watch(60)
        settle()
        // No frame's lift moves more than a quick spring's step: nothing snaps.
        for ((a, b) in frames.zipWithNext()) assertTrue("lift jumps: ${a.lift} to ${b.lift}", abs(b.lift - a.lift) < 0.5f)
        val land = frames.indexOfFirst { abs(it.x - 1f) < landed }
        for (l in frames.drop(land)) assertEquals("lift once landed", 0f, l.lift, 0.02f)
        assertEquals(ThumbLook(1f, 1f, 0f, 0f), look())
        assertEquals(Tab.Upcoming, tab)
    }
}

/** With the system's Remove animations on, a tap lands the pill at once and never grows the bar or the pill, not even for a frame. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ThumbTapNoMotionTest {
    @get:Rule val rule = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 0f })

    @Test fun aTapJumpsWithoutGrowing() {
        var seg by mutableStateOf(0)
        rule.setContent { GzTheme(Palettes.glass) { Segmented(listOf("Day", "Week", "Month"), seg, { seg = it }) } }
        rule.waitForIdle()
        val b = rule.onNodeWithText("Month").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(b.center); up() }
        repeat(20) { f ->
            rule.mainClock.advanceTimeByFrame()
            val l = rule.onNode(SemanticsMatcher.keyIsDefined(ThumbLookKey)).fetchSemanticsNode().config[ThumbLookKey]()
            assertEquals("swell at frame $f", 1f, l.swell, 1e-3f)
            assertEquals("lift at frame $f", 0f, l.lift, 1e-3f)
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(2, seg)
        assertEquals(2f, rule.onNode(SemanticsMatcher.keyIsDefined(ThumbLookKey)).fetchSemanticsNode().config[ThumbLookKey]().x, 0f)
    }
}

/**
 * With the system's animations slowed tenfold, a tap's slide takes over three seconds; the bar and pill stay grown for all of its
 * travel, not only for its first second.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class ThumbTapSlowMotionTest {
    @get:Rule val rule = createComposeRule(effectContext = object : androidx.compose.ui.MotionDurationScale { override val scaleFactor = 10f })

    private fun look() = rule.onNode(SemanticsMatcher.keyIsDefined(ThumbLookKey)).fetchSemanticsNode().config[ThumbLookKey]()

    @Test fun aSlowedTapStaysGrownPastASecond() {
        var tab by mutableStateOf(Tab.Workout)
        rule.setContent { GzTheme(Palettes.dark) { TabBar(tab) { tab = it } } }
        rule.waitForIdle()
        val b = rule.onNodeWithText("Food").fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false
        rule.onRoot().performTouchInput { down(b.center); up() }
        rule.mainClock.advanceTimeBy(1700)
        val mid = look()
        assertTrue("mid-slide: ${mid.x}", mid.x > 1f && mid.x < 3.5f)
        assertEquals("still grown 1.7 s in", 1f, mid.lift, 0.05f)
        assertEquals("bar still swelled 1.7 s in", 1.04f, mid.swell, 0.005f)
        rule.mainClock.autoAdvance = true
        rule.mainClock.advanceTimeBy(20_000)
        rule.waitForIdle()
        assertEquals(ThumbLook(4f, 1f, 0f, 0f), look())
        assertEquals(Tab.Food, tab)
    }
}
