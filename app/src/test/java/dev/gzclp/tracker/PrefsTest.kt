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

    @Test fun theOldLiquidGlassThemeLoadsAsMidnightInLoaded() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("gzclp", android.content.Context.MODE_PRIVATE).edit().putString("theme", "glass").commit()
        val prefs = Prefs(context)
        assertEquals("midnight", prefs.theme)
        assertEquals(true, prefs.loaded)
        // Loaded stays on after choosing another theme, and off once Performance is chosen.
        prefs.theme = "light"
        assertEquals(true, Prefs(context).loaded)
        prefs.loaded = false
        assertEquals(false, Prefs(context).loaded)
        assertEquals("light", Prefs(context).theme)
    }

    @Test fun theLiquidGlassSwitchCarriesOverToMotionAndAnUntouchedOneTakesTheDefault() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val raw = context.getSharedPreferences("gzclp", android.content.Context.MODE_PRIVATE)
        assertEquals(Prefs.LOADED_BY_DEFAULT, Prefs(context).loaded)
        raw.edit().putBoolean("glass", false).commit()
        assertEquals(false, Prefs(context).loaded)
        raw.edit().putBoolean("glass", true).commit()
        assertEquals(true, Prefs(context).loaded)
    }

    @Test fun theBuildDefaultsToAthleticAndKeepsAChosenOne() {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        assertEquals("athletic", prefs.bodyBuild)
        prefs.bodyBuild = "normal"
        assertEquals("normal", Prefs(ApplicationProvider.getApplicationContext()).bodyBuild)
    }

    @Test fun waterGoalSetBeforeAutoGoalsCarriesOverButTheOldDefaultDoesNot() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val raw = context.getSharedPreferences("gzclp", android.content.Context.MODE_PRIVATE)
        raw.edit().putInt("water_goal_ml", 3200).commit()
        assertEquals(3200, Prefs(context).waterGoalOverrideMl)
        raw.edit().clear().putInt("water_goal_ml", 2500).commit()
        assertEquals(null, Prefs(context).waterGoalOverrideMl)
        val prefs = Prefs(context)
        prefs.waterGoalOverrideMl = 2600
        assertEquals(2600, Prefs(context).waterGoalOverrideMl)
        prefs.waterGoalOverrideMl = null
        assertEquals(null, Prefs(context).waterGoalOverrideMl)
    }
}
