package dev.gzclp.tracker

import android.graphics.drawable.AnimatedVectorDrawable
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SplashTest {
    @Test fun launchScreenAnimatesTheMarkOnTheIconBackground() {
        val themed = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_GZCLP)
        val a = themed.obtainStyledAttributes(intArrayOf(android.R.attr.windowSplashScreenBackground, android.R.attr.windowSplashScreenAnimatedIcon))
        try {
            assertEquals(themed.getColor(R.color.launcher_bg), a.getColor(0, 0))
            assertTrue(a.getDrawable(1) is AnimatedVectorDrawable)
        } finally {
            a.recycle()
        }
    }
}
