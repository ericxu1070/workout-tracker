package dev.gzclp.tracker

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
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
 * One move playing full screen, as the pieces or (with the extra mesh) the Figure mesh in the body named by the extra
 * shape (male by default), to compare them and time frames:
 * adb shell am start -n dev.gzclp.tracker.debug/dev.gzclp.tracker.FormBench -e id squat --ez mesh true -e shape female
 */
class FormBench : ComponentActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Figure.on = intent.getBooleanExtra("mesh", false)
        Figure.shape = intent.getStringExtra("shape") ?: "male"
        // First open, split into reading the mesh (none for the pieces) and solving the move: adb logcat -s FormBench
        val start = SystemClock.elapsedRealtimeNanos()
        if (Figure.on) Figure.mesh
        val read = SystemClock.elapsedRealtimeNanos()
        val motion = Motions.of(intent.getStringExtra("id") ?: "squat")
        Log.i("FormBench", "read ${(read - start) / 1_000_000} ms, solve ${(SystemClock.elapsedRealtimeNanos() - read) / 1_000_000} ms")
        val p = Palettes.jade
        setContent { GzTheme(p) { FormView(motion, Modifier.fillMaxSize().background(p.bg)) } }
    }
}
