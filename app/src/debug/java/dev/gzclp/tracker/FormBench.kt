package dev.gzclp.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.form.Figure
import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.log.Muscle
import dev.gzclp.tracker.ui.FormView
import dev.gzclp.tracker.ui.GzTheme
import dev.gzclp.tracker.ui.MuscleBody
import dev.gzclp.tracker.ui.MuscleChart
import dev.gzclp.tracker.ui.Palettes

/**
 * One move playing full screen, as the pieces or (with the extra mesh) the Figure mesh, to compare them and time frames:
 * adb shell am start -n dev.gzclp.tracker.debug/dev.gzclp.tracker.FormBench -e id squat --ez mesh true
 * Or the muscle map, redrawn every frame at its Exercises tab size, flat or 3D, male or female:
 * adb shell am start -n dev.gzclp.tracker.debug/dev.gzclp.tracker.FormBench -e map flat --ez female true
 */
class FormBench : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Figure.on = intent.getBooleanExtra("mesh", false)
        val p = Palettes.jade
        val map = intent.getStringExtra("map")
        if (map != null) {
            // Synthetic shares, one per muscle, so every shade shows.
            val levels = Muscle.entries.associateWith { (it.ordinal % 5) / 4f }
            val female = intent.getBooleanExtra("female", false)
            setContent {
                GzTheme(p) {
                    val tick = remember { mutableLongStateOf(0L) }
                    LaunchedEffect(Unit) { while (true) withFrameNanos { tick.longValue = it } }
                    val m = Modifier.padding(top = 120.dp, start = 16.dp, end = 16.dp).fillMaxWidth().height(340.dp)
                    if (map == "flat") MuscleChart(levels, female, m.drawWithContent { tick.longValue; drawContent() })
                    else MuscleBody(levels, m, still = false)
                }
            }
            return
        }
        val motion = Motions.of(intent.getStringExtra("id") ?: "squat")
        setContent { GzTheme(p) { FormView(motion, Modifier.fillMaxSize().background(p.bg)) } }
    }
}
