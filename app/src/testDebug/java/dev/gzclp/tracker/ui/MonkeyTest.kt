package dev.gzclp.tracker.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.gzclp.tracker.MainActivity
import dev.gzclp.tracker.sync.Mirror
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * A monkey on the real app in Loaded, over the demo log: random taps, swipes and Backs from a fixed seed, so a crash it finds
 * comes back on the next run. -PgzclpMonkey=N runs N events (default 150); -PgzclpMonkeyShots=/path saves every 20th screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class MonkeyTest {
    @get:Rule(order = 0) val seed = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            app.getSharedPreferences("gzclp", Context.MODE_PRIVATE).edit().putBoolean("loaded", true).commit()
            Demo.build(File(app.filesDir, Mirror.NAME))
        }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun randomTapsSwipesAndBacksNeverCrashLoaded() {
        val n = System.getProperty("gzclp.monkey")?.toIntOrNull() ?: 150
        val shots = System.getProperty("gzclp.monkeyShots")?.let(::File)?.apply { mkdirs() }
        val r = Random(7)
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(1000)
        repeat(n) { i ->
            val root = rule.onRoot()
            when (r.nextInt(10)) {
                0 -> rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
                1, 2 -> root.performTouchInput {
                    val y = height * (0.15f + 0.7f * r.nextFloat())
                    val (a, b) = if (r.nextBoolean()) 0.8f to 0.2f else 0.2f to 0.8f
                    swipe(Offset(width * a, y), Offset(width * b, y), 120)
                }
                3 -> root.performTouchInput {
                    val x = width * r.nextFloat()
                    val (a, b) = if (r.nextBoolean()) 0.75f to 0.3f else 0.3f to 0.75f
                    swipe(Offset(x, height * a), Offset(x, height * b), 150)
                }
                // The tab bar and a header's switch get their share, or the monkey rarely leaves a screen.
                4 -> root.performTouchInput { click(Offset(width * r.nextFloat(), height * 0.95f)) }
                5 -> root.performTouchInput { click(Offset(width * r.nextFloat(), height * 0.13f)) }
                else -> root.performTouchInput { click(Offset(width * r.nextFloat(), height * r.nextFloat())) }
            }
            // Short and long gaps, so some events land mid-animation and some after it.
            rule.mainClock.advanceTimeBy(if (r.nextInt(3) == 0) 600L else 48L)
            if (shots != null && i % 20 == 0) rule.onRoot().captureRoboImage(File(shots, "monkey-%04d.png".format(i)).path)
        }
        rule.mainClock.advanceTimeBy(2000)
    }
}
