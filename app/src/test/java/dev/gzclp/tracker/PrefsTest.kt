package dev.gzclp.tracker

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsTest {
    @Test fun themeDefaultsToTokyoNightAndKeepsAChosenOne() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        assertEquals("tokyo", prefs.theme)
        prefs.theme = "jade"
        assertEquals("jade", Prefs(ApplicationProvider.getApplicationContext()).theme)
    }
}
