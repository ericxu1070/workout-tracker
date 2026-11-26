package dev.gzclp.tracker.ui

import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnimatorScaleTest {
    private val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver

    @Test fun anUnsetAnimationScaleLeavesLoadedOn() {
        Settings.Global.putString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, null)
        assertTrue(animatorsEnabled(resolver))
    }

    @Test fun removeAnimationsTurnsLoadedOff() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertFalse(animatorsEnabled(resolver))
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0.5f)
        assertTrue(animatorsEnabled(resolver))
    }
}
