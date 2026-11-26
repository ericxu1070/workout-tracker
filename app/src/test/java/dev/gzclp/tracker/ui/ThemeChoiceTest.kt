package dev.gzclp.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeChoiceTest {
    @Test fun systemFollowsThePhone() {
        assertEquals(Palettes.dark, Palettes.resolve(Palettes.SYSTEM, systemDark = true, laptop = null))
        assertEquals(Palettes.light, Palettes.resolve(Palettes.SYSTEM, systemDark = false, laptop = null))
        assertEquals("System", Palettes.label(Palettes.SYSTEM))
    }

    @Test fun savedThemesStillLoad() {
        for (id in listOf("jade", "tokyo", "dark", "light", "midnight")) assertEquals(id, Palettes.resolve(id, false, null).id)
        assertEquals(Palettes.tokyo, Palettes.resolve(LaptopTheme.ID, false, null))
        assertEquals("Laptop", Palettes.label(LaptopTheme.ID))
    }

    @Test fun liquidGlassIsOneInstancePerThemeAndOffIsTheThemeItself() {
        for (p in Palettes.all + Palettes.custom) {
            assertTrue(p.withGlass(true) === p.withGlass(true))
            assertTrue(p.withGlass(true).glass)
            assertTrue(p.withGlass(false) === p)
            assertTrue(p.withGlass(true).withGlass(true) === p.withGlass(true))
        }
    }

    @Test fun everyOmarchyThemeIsListedAndMatchesItsLaptopReading() {
        assertEquals(27, Palettes.custom.size)
        assertEquals(Palettes.custom.map { it.name }.sorted(), Palettes.custom.map { it.name })
        for (p in Palettes.omarchy) {
            val fixture = javaClass.getResource("/themes/${p.id}.toml")!!.readText()
            assertEquals(p.id, LaptopTheme.parse(fixture)!!.copy(id = p.id, name = p.name), p)
            assertEquals(p, Palettes.resolve(p.id, false, null))
        }
    }
}
