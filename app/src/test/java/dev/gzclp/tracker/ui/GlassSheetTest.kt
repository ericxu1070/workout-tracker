package dev.gzclp.tracker.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSheetTest {
    private val themes = Palettes.all + Palettes.omarchy

    @Test fun everySheetReadsOverAnythingUnderIt() {
        for (p in themes) assertTrue(p.name, sheetReads(p, sheetAlpha(p)))
    }

    /** Solid sheets would pass the test above; the hand-tuned themes must actually let the screen show through. */
    @Test fun theHandTunedThemesAreGlass() {
        for (p in Palettes.all) assertTrue("${p.name} ${sheetAlpha(p)}", sheetAlpha(p) < 0.95f)
    }

    /** At the floor opacity some theme's text drops below 4.5:1, so each theme's own opacity is doing work. */
    @Test fun seeThroughNeedsTheCheck() {
        assertTrue(themes.any { !sheetReads(it, SHEET_MIN_ALPHA) })
    }
}
