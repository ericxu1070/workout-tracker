package dev.gzclp.tracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import dev.gzclp.tracker.log.Kind
import dev.gzclp.tracker.log.Segment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** WCAG minimums for every built-in theme and every Omarchy theme the Laptop theme can take: 4.5:1 for text, 3:1 for controls and chart marks. */
class PaletteContrastTest {
    private val laptop = File(javaClass.getResource("/themes")!!.toURI()).list()!!
        .filter { it.endsWith(".raw.toml") }.sorted()
        .map { LaptopTheme.parse(javaClass.getResource("/themes/$it")!!.readText())!!.let { p -> p.copy(id = "laptop-${it.removeSuffix(".raw.toml")}") } }
    private val themes = Palettes.all + Palettes.glass + Palettes.omarchy + laptop

    private fun Palette.check(what: String, fgColor: Color, bgColor: Color, min: Float) {
        val solid = if (fgColor.alpha < 1f) fgColor.compositeOver(bgColor) else fgColor
        val c = contrast(solid, bgColor)
        assertTrue("$id $what %.2f < $min".format(c), c >= min)
    }

    @Test fun coversEveryTheme() = assertEquals(4 + 1 + 24 + 26, themes.size)

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

    @Test fun liquidGlassReadsOverItsWorstBackdrop() {
        val p = Palettes.glass
        // Translucent, or it is only a flat theme with rims.
        assertTrue("chrome alpha ${p.chrome.alpha}", p.chrome.alpha < 0.9f)
        assertTrue("card alpha ${p.card.alpha}", p.card.alpha < 0.6f)
        for ((i, s) in p.surfaces.withIndex()) {
            // A filled control's label at the top of its sheen, and a switch or segment against its track, on every backdrop.
            p.check("fg on sheen over surface $i", p.fg, Color.White.copy(alpha = SHEEN).compositeOver(s), 4.5f)
            p.check("accent vs track over surface $i", p.accent, p.fillPressed.compositeOver(s), 3f)
        }
        // The primary pill and the segmented thumb, lit.
        p.check("onAccent on lit accent", p.onAccent, Color.White.copy(alpha = SHEEN).compositeOver(p.accent), 4.5f)
    }

    @Test fun dayMarkersReadAndStandApartInEveryTheme() {
        val mixes = listOf(
            listOf(Segment(Kind.LIFT, 3000, 0.5f, 0f), Segment(Kind.CARDIO, 2400, 0.4f, 1f), Segment(Kind.STRETCH, 600, 0.1f, 0.5f)),
            listOf(Segment(Kind.CARDIO, 5700, 0.95f, 1f), Segment(Kind.STRETCH, 300, 0.05f, 0f)),
        )
        for (p in themes) {
            val k = p.kinds
            val hues = listOf(k.lift, k.cardio, k.stretch)
            // No theme falls back to one color for all three.
            assertEquals("${p.id} kinds", 3, hues.distinct().size)
            for (c in hues) for (t in listOf(0f, 0.5f, 1f)) p.check("day number on a kind at $t", k.on, atIntensity(c, t), 4.5f)
            for (c in hues) for (s in listOf(p.bg, p.elevated)) p.check("kind mark on surface", c, s, 3f)
            // Every color in a pie, blends included, keeps the day number readable.
            for (m in mixes) for ((_, c) in sweepStops(m, m.map { atIntensity(k.of(it.kind), it.intensity) })) p.check("day number on a blend", k.on, c, 4.5f)
        }
    }

    @Test fun sweepStopsRunInOrderAndCloseOnTheFirstColor() {
        val segs = listOf(Segment(Kind.LIFT, 3000, 0.5f, 1f), Segment(Kind.CARDIO, 2400, 0.4f, 1f), Segment(Kind.STRETCH, 600, 0.1f, 1f))
        val cs = listOf(Color.Red, Color.Green, Color.Blue)
        val stops = sweepStops(segs, cs)
        assertEquals(stops.map { it.first }.sorted(), stops.map { it.first })
        assertEquals(0f to Color.Red, stops.first())
        assertEquals(1f to Color.Red, stops.last())
        // Half the day in the first arc puts its middle, stop 0, at 3 o'clock, where the sweep starts anyway.
        assertEquals(0f, sweepTurn(segs), 1e-6f)
    }
}
