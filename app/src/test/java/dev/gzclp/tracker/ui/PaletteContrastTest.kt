package dev.gzclp.tracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** WCAG minimums for every built-in theme and every Omarchy theme the Laptop theme can take, each with Liquid Glass off and on: 4.5:1 for text, 3:1 for controls and chart marks. */
class PaletteContrastTest {
    private val laptop = File(javaClass.getResource("/themes")!!.toURI()).list()!!
        .filter { it.endsWith(".raw.toml") }.sorted()
        .map { LaptopTheme.parse(javaClass.getResource("/themes/$it")!!.readText())!!.let { p -> p.copy(id = "laptop-${it.removeSuffix(".raw.toml")}") } }
    private val flat = Palettes.all + Palettes.midnight + Palettes.omarchy + laptop
    private val glass = flat.map { it.glassed }
    private val themes = flat + glass

    private fun Palette.check(what: String, fgColor: Color, bgColor: Color, min: Float) {
        val solid = if (fgColor.alpha < 1f) fgColor.compositeOver(bgColor) else fgColor
        val c = contrast(solid, bgColor)
        assertTrue("$id $what %.2f < $min".format(c), c >= min)
    }

    @Test fun coversEveryTheme() = assertEquals(2 * (4 + 1 + 24 + 26), themes.size)

    @Test fun textReadsOnEverySurface() {
        for (p in themes) for ((i, s) in p.surfaces.withIndex()) {
            for ((name, c) in listOf("fg" to p.fg, "fg2" to p.fg2, "fg3" to p.fg3, "accent" to p.accent, "red" to p.red)) p.check("$name on surface $i", c, s, 4.5f)
        }
    }

    @Test fun untrainedMusclesStandOffADarkPage() {
        for (p in themes.filter { it.dark }) p.check("untrained muscle", p.muscle, p.bg, 3f)
    }

    @Test fun textReadsOnFilledControls() {
        for (p in themes) {
            // Primary pills, the selected segment, active week days, and stretch chips.
            p.check("onAccent on accent", p.onAccent, p.accent, 4.5f)
            // The red Delete pill and minus in edit mode.
            p.check("onRed on red", p.onRed, p.red, 4.5f)
        }
    }

    @Test fun currentSetReadsOnItsPill() {
        for (p in themes) {
            // Accent-tinted in every theme, never the plain fill it falls back to.
            assertTrue("${p.id} step pill falls back to fill", p.stepPill != p.fill)
            for (s in p.surfaces) {
                val pill = p.stepPill.compositeOver(s)
                // The set number; the fields and check on the pill are opaque fill over the page or card, which textReadsOnEverySurface covers.
                p.check("fg on the step pill", p.fg, pill, 4.5f)
                // The current set's check keeps its accent ring.
                p.check("accent ring on the step pill", p.accent, pill, 3f)
            }
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

    @Test fun liquidGlassReadsOverItsWorstBackdrop() = glass.forEach { p ->
        // Translucent over a visible wallpaper (at least a fifth of the glow Liquid Glass was tuned with), or it is only a flat theme with rims.
        assertTrue("${p.id} chrome alpha ${p.chrome.alpha}", p.chrome.alpha < 0.96f)
        assertTrue("${p.id} card alpha ${p.card.alpha}", p.card.alpha <= 0.6f)
        assertTrue("${p.id} glow alpha ${p.glows.first().alpha}", p.glows.first().alpha > 0.03f)
        for ((i, s) in p.surfaces.withIndex()) {
            // A filled control's label at the top of its sheen, and a switch or segment against its track, on every backdrop.
            p.check("fg on sheen over surface $i", p.fg, Color.White.copy(alpha = SHEEN).compositeOver(s), 4.5f)
            p.check("accent vs track over surface $i", p.accent, p.fillPressed.compositeOver(s), 3f)
        }
        // Every control is glass: neutral ones and selections clear, the primary and destructive stained.
        assertTrue("${p.id} clear glass alpha ${p.thumb.alpha}", p.thumb.alpha < 1f)
        assertTrue("${p.id} stain alpha ${p.stain.alpha}", p.stain.alpha < 1f)
        assertTrue("${p.id} red stain alpha ${p.redStain.alpha}", p.redStain.alpha < 1f)
        for ((i, s) in p.surfaces.withIndex()) {
            // Labels and icons at the top of the sheen on a clear button, the selected segment, tab or chip, and the stained buttons.
            val sheen = Color.White.copy(alpha = SHEEN)
            p.check("label on clear glass over surface $i", p.onThumb, sheen.compositeOver(p.thumb.compositeOver(s)), 4.5f)
            p.check("fg on clear glass over surface $i", p.fg, sheen.compositeOver(p.thumb.compositeOver(s)), 4.5f)
            p.check("onAccent on stained glass over surface $i", p.onAccent, sheen.compositeOver(p.stain.compositeOver(s)), 4.5f)
            p.check("onRed on red stained glass over surface $i", p.onRed, sheen.compositeOver(p.redStain.compositeOver(s)), 4.5f)
        }
    }

    @Test fun barsAreSeeThroughAndTheirLabelsReadOverWhatScrollsUnder() = glass.forEach { p ->
        // The tab bar and the floating switch are clearer than sheets, tinted the theme's own color; the Liquid Glass theme's at most 45% opaque.
        assertTrue("${p.id} bar alpha ${p.bar.alpha} vs chrome ${p.chrome.alpha}", p.bar.alpha < p.chrome.alpha && p.bar.alpha >= CLEAREST_BAR / 100f)
        assertEquals(p.bg.copy(alpha = p.bar.alpha), p.bar)
        // The narrowed set: what scrolls under a bar is the wallpaper (plainest, each glow, all glows at once) and cards over it.
        // Brighter blocks (accent and red pills, white knobs) are left to the labels' halo, a judgment made on the phone, not here.
        val walls = listOf(p.bg) + p.glows.map { it.compositeOver(p.bg) } + p.glows.fold(p.bg) { u, g -> g.compositeOver(u) }
        val sheen = Color.White.copy(alpha = SHEEN)
        for (u in walls + walls.map { p.card.compositeOver(it) }) for (s in p.bar.compositeOver(u).let { listOf(it, sheen.compositeOver(it)) }) {
            p.check("tab label on the bar", p.fg2, s, 4.5f)
            p.check("current tab on its pill", p.fg, p.pill.compositeOver(s), 4.5f)
            p.check("segment label on the track", p.fg, p.fill.compositeOver(s), 4.5f)
            p.check("current segment on its pill", p.fg, p.pill.compositeOver(p.fill.compositeOver(s)), 4.5f)
        }
    }

    @Test fun theLiquidGlassThemesBarsAreSeeThrough() = assertTrue(Palettes.glass.bar.alpha <= 0.45f)

    @Test fun restingPillIsNeutralAndTheCurrentSegmentReadsOnIt() = themes.forEach { p ->
        // One shade off the bar, never the accent: fg's own hue, so lighter on a dark bar and grayer on a light one.
        assertEquals(p.fg.copy(alpha = p.pill.alpha), p.pill)
        assertTrue("${p.id} pill alpha ${p.pill.alpha}", p.pill.alpha in 0.055f..0.125f)
        // Surfaces include the fill-tinted track the pill slides on.
        for ((i, s) in p.surfaces.withIndex()) p.check("fg on the resting pill over surface $i", p.fg, p.pill.compositeOver(s), 4.5f)
    }

    @Test fun flatControlsKeepTheirSolidFills() = flat.forEach { p ->
        assertEquals(p.accent, p.stain)
        assertEquals(p.red, p.redStain)
        assertEquals(p.accent, p.thumb)
        assertEquals(p.onAccent, p.onThumb)
    }

    @Test fun theLiquidGlassThemeKeepsItsLook() {
        // Midnight under glass is what the Liquid Glass theme was: full glows, bars at 82%, secondary text as it was.
        val p = Palettes.midnight.glassed
        assertEquals(0.18f, p.glows.first().alpha, 1e-3f)
        assertEquals(0.82f, p.chrome.alpha, 1e-3f)
        assertEquals(Palettes.midnight.fg2, p.fg2)
        // Its accent, the blue its controls and charts were tuned in, moves by at most a few 8-bit steps.
        for ((a, b) in listOf(Palettes.midnight.accent to p.accent).flatMap { (a, b) -> listOf(a.red to b.red, a.green to b.green, a.blue to b.blue) }) assertEquals(a, b, 4 / 255f)
    }

    @Test fun tertiaryTextIsTheFaintestThatReads() = themes.forEach { p ->
        // The bisected alpha is the one a scan from the faintest up finds.
        fun reads(a: Int) = p.surfaces.all { contrast(p.fg.copy(alpha = a / 100f).compositeOver(it), it) >= 4.5f }
        val scanned = (42..100).firstOrNull(::reads)?.let { it / 100f } ?: 1f
        assertEquals(p.id, scanned, p.fg3.alpha, 1 / 255f)
    }

    @Test fun kindDotsStandApartInEveryTheme() {
        for (p in themes) {
            val k = p.kinds
            val hues = listOf(k.lift, k.cardio, k.stretch)
            // No theme falls back to one color for all three.
            assertEquals("${p.id} kinds", 3, hues.distinct().size)
            for (c in hues) for (s in listOf(p.bg, p.elevated)) p.check("kind mark on surface", c, s, 3f)
        }
    }
}
