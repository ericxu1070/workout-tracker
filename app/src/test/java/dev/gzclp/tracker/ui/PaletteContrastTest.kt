package dev.gzclp.tracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** WCAG minimums for every built-in theme and every Omarchy theme the Laptop theme can take: 4.5:1 for text, 3:1 for controls and chart marks. */
class PaletteContrastTest {
    private val laptop = File(javaClass.getResource("/themes")!!.toURI()).list()!!
        .filter { it.endsWith(".raw.toml") }.sorted()
        .map { LaptopTheme.parse(javaClass.getResource("/themes/$it")!!.readText())!!.let { p -> p.copy(id = "laptop-${it.removeSuffix(".raw.toml")}") } }
    private val themes = Palettes.all + Palettes.omarchy + laptop

    private fun Palette.check(what: String, fgColor: Color, bgColor: Color, min: Float) {
        val solid = if (fgColor.alpha < 1f) fgColor.compositeOver(bgColor) else fgColor
        val c = contrast(solid, bgColor)
        assertTrue("$id $what %.2f < $min".format(c), c >= min)
    }

    @Test fun coversEveryTheme() = assertEquals(4 + 24 + 26, themes.size)

    @Test fun textReadsOnEverySurface() {
        for (p in themes) for ((i, s) in p.surfaces.withIndex()) {
            for ((name, c) in listOf("fg" to p.fg, "fg2" to p.fg2, "fg3" to p.fg3, "accent" to p.accent, "red" to p.red)) p.check("$name on surface $i", c, s, 4.5f)
        }
    }

    @Test fun textReadsOnFilledControls() {
        for (p in themes) {
            // Primary pills, the selected segment, active week days, and stretch chips.
            p.check("onAccent on accent", p.onAccent, p.accent, 4.5f)
            // The red Delete pill and minus in edit mode.
            p.check("onRed on red", p.onRed, p.red, 4.5f)
        }
    }

    @Test fun controlStatesAndChartMarksClearThreeToOne() {
        for (p in themes) for (s in listOf(p.bg, p.elevated)) {
            // The accent marks the selected segment, a switch that is on, a slider's fill, and the current chart series;
            // each sits on a fillPressed track or the bare surface.
            p.check("accent vs track", p.accent, p.fillPressed.compositeOver(s), 3f)
            p.check("fg2 chart series", p.fg2, s, 3f)
            p.check("fg3 chevron and bar", p.fg3, s, 3f)
            // A set logged short: a red check on a red tint.
            p.check("red check on its tint", p.red, p.red.copy(alpha = 0.18f).compositeOver(s), 3f)
            // The slider knob is white; where white can't clear 3:1, its fg3 ring must.
            p.check("slider thumb edge", if (thumbRinged(p)) p.fg3 else Color.White, s, 3f)
        }
    }
}
