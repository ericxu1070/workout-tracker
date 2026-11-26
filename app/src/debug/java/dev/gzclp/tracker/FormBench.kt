package dev.gzclp.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dev.gzclp.tracker.form.Figure
import dev.gzclp.tracker.form.Motions
import dev.gzclp.tracker.ui.FormView
import dev.gzclp.tracker.ui.GzTheme
import dev.gzclp.tracker.ui.Palettes

/**
 * One move playing full screen, as the pieces or (with the extra mesh) the Figure mesh, to compare them and time frames:
 * adb shell am start -n dev.gzclp.tracker.debug/dev.gzclp.tracker.FormBench -e id squat --ez mesh true
 */
class FormBench : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Figure.on = intent.getBooleanExtra("mesh", false)
        val motion = Motions.of(intent.getStringExtra("id") ?: "squat")
        val p = Palettes.jade
        setContent { GzTheme(p) { FormView(motion, Modifier.fillMaxSize().background(p.bg)) } }
    }
}
