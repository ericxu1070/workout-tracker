package dev.gzclp.tracker.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import dev.gzclp.tracker.AppModel
import dev.gzclp.tracker.ui.GzTheme
import dev.gzclp.tracker.ui.LocalPalette
import dev.gzclp.tracker.ui.NavRow
import dev.gzclp.tracker.ui.ScreenFrame
import dev.gzclp.tracker.ui.Space
import dev.gzclp.tracker.ui.Txt
import dev.gzclp.tracker.ui.Type

/** The privacy note Health Connect opens from its permission screen; it requires one before it grants access. */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GzTheme(AppModel.get(this).palette) {
                ScreenFrame {
                    NavRow(::finish)
                    Txt("Health Connect", Type.largeTitle)
                    Spacer(Modifier.height(Space.lg))
                    Txt(
                        "GZCLP writes each finished workout (its sets and reps, with weights in the notes), each cardio session, and each weigh-in to Health Connect. " +
                            "It never reads your health data and sends nothing off this phone. Turn it off in Settings or revoke access in Health Connect at any time.",
                        Type.body, color = LocalPalette.current.fg2,
                    )
                }
            }
        }
    }
}
