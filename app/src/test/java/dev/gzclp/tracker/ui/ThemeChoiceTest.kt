package dev.gzclp.tracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeChoiceTest {
    @Test fun systemFollowsThePhone() {
        assertEquals(Palettes.dark, Palettes.resolve(Palettes.SYSTEM, systemDark = true, laptop = null))
        assertEquals(Palettes.light, Palettes.resolve(Palettes.SYSTEM, systemDark = false, laptop = null))
        assertEquals("System", Palettes.label(Palettes.SYSTEM))
    }

    @Test fun savedThemesStillLoad() {
        for (id in listOf("jade", "tokyo", "dark", "light", "glass")) assertEquals(id, Palettes.resolve(id, false, null).id)
        assertEquals(Palettes.tokyo, Palettes.resolve(LaptopTheme.ID, false, null))
        assertEquals("Laptop", Palettes.label(LaptopTheme.ID))
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
