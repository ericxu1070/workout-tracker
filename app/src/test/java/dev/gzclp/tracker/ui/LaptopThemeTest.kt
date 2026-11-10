package dev.gzclp.tracker.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are real files: the laptop's synced omarchy-theme.toml and two Omarchy colors.toml with a name line added. */
class LaptopThemeTest {
    private fun theme(name: String) = javaClass.getResource("/themes/$name.toml")!!.readText()
    private fun parse(name: String) = LaptopTheme.parse(theme(name), Palettes.jade)

    @Test fun jadeHorseMapsKeysToTokens() {
        val p = parse("jade-horse")
        assertEquals(Palette("laptop", "jade-horse", true, Color(0xFF142620), Color(0xFF203B31), Color(0xFFDFEDE5), Color(0xFF91AD9F), Color(0xFF8BCBB1), Color.Black, Color(0xFFD88D91)), p)
    }

    @Test fun lowContrastSecondaryTextIsBlendedUntilReadable() {
        for (name in listOf("tokyo-night", "catppuccin-latte")) {
            val p = parse(name)
            assertNotEquals(name, Color(if (name == "tokyo-night") 0xFF565F89 else 0xFF9CA0B0), p.fg2)
            assertTrue(name, LaptopTheme.contrast(p.fg2, p.bg) >= 4.5f)
            assertTrue("$name: dimmer than foreground", LaptopTheme.contrast(p.fg2, p.bg) < LaptopTheme.contrast(p.fg, p.bg))
        }
    }

    @Test fun lightThemeSetsModeAndWhiteOnAccent() {
        val p = parse("catppuccin-latte")
        assertFalse(p.dark)
        assertEquals("catppuccin-latte", p.name)
        assertEquals(Color.White, p.onAccent)
        assertEquals(Color(0xFFEFF1F5), p.bg)
    }

    @Test fun halfWrittenOrMalformedFileFallsBackPerKey() {
        val cached = parse("tokyo-night")
        val text = theme("jade-horse").replace("#dfede5", "#dfed").substringBefore("lighter_background")
        val p = LaptopTheme.parse(text, cached)
        assertEquals("jade-horse", p.name)
        assertEquals(Color(0xFF142620), p.bg)
        assertEquals(Color(0xFF8BCBB1), p.accent)
        assertEquals(cached.fg, p.fg)
        assertEquals(cached.elevated, p.elevated)
        assertEquals(cached.red, p.red)
        assertEquals(cached, LaptopTheme.parse("", cached))
    }

    @Test fun cacheRoundTrips() {
        for (name in listOf("jade-horse", "tokyo-night", "catppuccin-latte")) {
            val p = parse(name)
            assertEquals(name, p, LaptopTheme.parse(LaptopTheme.encode(p), Palettes.dark))
        }
    }
}
