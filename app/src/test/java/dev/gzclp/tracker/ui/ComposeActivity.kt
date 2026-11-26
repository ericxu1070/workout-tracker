package dev.gzclp.tracker.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf

/**
 * Registers the activity [androidx.compose.ui.test.junit4.createComposeRule] launches. ui-test-manifest declares it only
 * in debug, so without this the rule fails in release unit tests. Give it a lower rule order than the compose rule.
 */
class ComposeActivity : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = ComponentName(app, ComponentActivity::class.java)
        val pm = shadowOf(app.packageManager)
        pm.addActivityIfNotPresent(name)
        pm.addIntentFilterForActivity(name, IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) })
    }
}
