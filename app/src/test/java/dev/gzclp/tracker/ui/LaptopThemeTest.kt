package dev.gzclp.tracker.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fixtures are every Omarchy theme on the laptop: `<name>.toml` is what the laptop hook syncs
 * (`omarchy-theme-color --all` plus a name line), `<name>.raw.toml` the theme's own colors.toml.
 */
class LaptopThemeTest {
    private fun theme(name: String) = javaClass.getResource("/themes/$name.toml")!!.readText()
    private fun parse(name: String) = LaptopTheme.parse(theme(name))!!
    private val themes = File(javaClass.getResource("/themes")!!.toURI()).list()!!
        .filter { it.endsWith(".raw.toml") }.map { it.removeSuffix(".raw.toml") }.sorted()

    @Test fun everyLaptopThemeIsCompleteAndReadable() {
        assertEquals(26, themes.size)
        fun ok(what: String, a: Color, b: Color, min: Float) =
            assertTrue("$what ${LaptopTheme.contrast(a, b)}", LaptopTheme.contrast(a, b) >= min)
        for (name in themes) {
            val p = parse(name)
            assertEquals(name, p.name)
            // The raw colors.toml has no name line; otherwise the two forms must agree token for token.
            assertEquals(name, p, LaptopTheme.parse(theme("$name.raw"))!!.copy(name = name))
            assertNotEquals("$name: elevated distinct", p.bg, p.elevated)
            ok("$name fg/bg", p.fg, p.bg, 4.5f)
            ok("$name fg/elevated", p.fg, p.elevated, 4.5f)
            ok("$name fg2/bg", p.fg2, p.bg, 4.5f)
            ok("$name fg2/elevated", p.fg2, p.elevated, 4.5f)
            ok("$name onAccent/accent", p.onAccent, p.accent, 4.5f)
            ok("$name accent/bg", p.accent, p.bg, 3f)
        }
    }

    @Test fun jadeHorseMapsKeysToTokens() {
        val p = parse("jade-horse")
        assertEquals(Palette("laptop", "jade-horse", true, Color(0xFF142620), Color(0xFF203B31), Color(0xFFDFEDE5), Color(0xFF91AD9F), Color(0xFF8BCBB1), Color.Black, Color(0xFFD88D91)), p)
    }

    @Test fun shortAliasKeysAreRead() {
        // city-783's colors.toml names these only as lighter_bg and dark_fg.
        val p = LaptopTheme.parse(theme("city-783.raw"))!!
        assertEquals(Color(0xFF20232A), p.elevated)
        assertEquals(Color(0xFF181A1F), p.bg)
        assertEquals(Color(0xFFB9BEC6), p.fg)
        assertEquals(Color(0xFFFF5C5C), p.accent)
    }

    @Test fun lowContrastSecondaryTextIsBlendedUntilReadable() {
        for (name in listOf("tokyo-night", "catppuccin-latte")) {
            val p = parse(name)
            assertNotEquals(name, Color(if (name == "tokyo-night") 0xFF565F89 else 0xFF9CA0B0), p.fg2)
            assertTrue("$name: dimmer than foreground", LaptopTheme.contrast(p.fg2, p.bg) < LaptopTheme.contrast(p.fg, p.bg))
        }
    }

    @Test fun lightThemeSetsModeAndWhiteOnAccent() {
        val p = parse("catppuccin-latte")
        assertFalse(p.dark)
        assertEquals(Color.White, p.onAccent)
        assertEquals(Color(0xFFEFF1F5), p.bg)
    }

    @Test fun missingKeysDeriveFromTheSameTheme() {
        val p = LaptopTheme.parse("background = \"#ffffff\"\nforeground = \"#000000\"\n")!!
        assertFalse(p.dark)
        assertNotEquals(p.bg, p.elevated)
        assertTrue(LaptopTheme.contrast(p.fg2, p.bg) in 4.5f..20f)
        assertEquals(p.fg, p.accent)
        assertEquals(Color(0xFFE5484D), p.red)
        assertEquals("Laptop", p.name)
    }

    @Test fun fileWithoutBackgroundOrForegroundIsUnreadable() {
        assertNull(LaptopTheme.parse(""))
        assertNull(LaptopTheme.parse(theme("jade-horse").lines().filterNot { it.startsWith("foreground") || it.startsWith("fg ") || it.startsWith("color7") }.joinToString("\n")))
    }

    @Test fun cacheRoundTrips() {
        for (name in themes) {
            val p = parse(name)
            assertEquals(name, p, LaptopTheme.parse(LaptopTheme.encode(p)))
        }
    }
}
