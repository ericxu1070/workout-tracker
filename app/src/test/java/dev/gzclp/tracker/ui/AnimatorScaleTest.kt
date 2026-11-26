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

    /** As on the S21: the setting deleted, ValueAnimator left at 0, and Compose animating at 1. */
    @Test fun anUnsetAnimationScaleLeavesLoadedOnWhateverValueAnimatorSays() {
        Settings.Global.putString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, null)
        durationScale(0f)
        try {
            assertTrue(animatorsEnabled(resolver))
        } finally {
            durationScale(1f)
        }
    }

    @Test fun removeAnimationsTurnsLoadedOff() {
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertFalse(animatorsEnabled(resolver))
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0.5f)
        assertTrue(animatorsEnabled(resolver))
    }

    /** ValueAnimator's own scale, which the window manager sets and an app cannot. */
    private fun durationScale(v: Float) {
        android.animation.ValueAnimator::class.java.getMethod("setDurationScale", Float::class.javaPrimitiveType).invoke(null, v)
    }
}
