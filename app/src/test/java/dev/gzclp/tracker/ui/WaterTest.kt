package dev.gzclp.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.WaterLogged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class WaterUiTest {
    private val out = File(System.getProperty("gzclp.shots") ?: "build/shots")

    @Test
    fun keyframePointsValidation() {
        assertEquals(5, KEYFRAME_SIZES.size)
        assertEquals(5, KEYFRAMES.size)

        for (k in KEYFRAMES) {
            assertEquals(20, k.size)
            for (p in k) {
                assertTrue("x must be in 0..1: ${p.x}", p.x in 0f..1f)
                assertTrue("y must be in 0..1: ${p.y}", p.y in 0f..1f)
            }
        }
    }

    @Test
    fun interpolationContinuous() {
        val testSizes = listOf(100, 200, 325, 450, 550, 650, 775, 900, 1000)
        for (size in testSizes) {
            val points = interpolatePoints(size)
            assertEquals(20, points.size)
            for (p in points) {
                assertTrue("Interpolated x must be in 0..1: ${p.x}", p.x in 0f..1f)
                assertTrue("Interpolated y must be in 0..1: ${p.y}", p.y in 0f..1f)
            }
        }

        // At keyframes exact match
        assertEquals(KEYFRAMES[0], interpolatePoints(100))
        assertEquals(KEYFRAMES[1], interpolatePoints(325))
        assertEquals(KEYFRAMES[2], interpolatePoints(550))
        assertEquals(KEYFRAMES[3], interpolatePoints(775))
        assertEquals(KEYFRAMES[4], interpolatePoints(1000))
    }

    @Test
    fun vesselNames() {
        assertEquals("Espresso Cup", vesselName(100))
        assertEquals("Tumbler", vesselName(300))
        assertEquals("Tall Glass", vesselName(550))
        assertEquals("Bottle", vesselName(750))
        assertEquals("Jug", vesselName(1000))
    }

    @Test
    fun morphingCupThreeSizesScreenshots() {
        out.mkdirs()
        val p = Palettes.jade
        val testCases = listOf(
            Triple(100, 0.4f, "small-espresso-100ml"),
            Triple(300, 0.6f, "medium-glass-300ml"),
            Triple(800, 0.75f, "large-bottle-800ml"),
        )

        for ((sizeMl, fraction, label) in testCases) {
            captureRoboImage(File(out, "cup-$label.png").path) {
                GzTheme(p) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(p.bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        MorphingCup(
                            cupSizeMl = sizeMl,
                            fillFraction = fraction,
                            modifier = Modifier.size(240.dp),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun waterScreenScreenshot() {
        out.mkdirs()
        val p = Palettes.jade
        val state = AppState().copy(
            water = mapOf(
                "wt1" to WaterLogged("wt1", "2026-11-23T08:30:00-08:00", 250),
                "wt2" to WaterLogged("wt2", "2026-11-23T11:15:00-08:00", 500),
            )
        )

        captureRoboImage(File(out, "water-screen.png").path) {
            GzTheme(p) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(p.bg),
                ) {
                    WaterScreen(
                        state = state,
                        day = LocalDate.parse("2026-11-23"),
                        goalMl = 2500,
                        cupSizeMl = 300,
                        onBack = {},
                    )
                }
            }
        }
    }

    @Test
    fun foodScreenWithWaterEntryPointScreenshot() {
        out.mkdirs()
        val p = Palettes.jade
        val day = LocalDate.parse("2026-11-23")

        captureRoboImage(File(out, "food-with-water.png").path) {
            GzTheme(p) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(p.bg),
                ) {
                    FoodScreen(
                        meals = emptyList(),
                        day = day,
                        today = day,
                        error = null,
                        onDay = {},
                        onSnap = {},
                        onUpload = {},
                        onOpen = {},
                        waterIntakeMl = 1250,
                        waterGoalMl = 2500,
                    )
                }
            }
        }
    }
}
