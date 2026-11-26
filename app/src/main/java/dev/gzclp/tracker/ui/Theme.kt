package dev.gzclp.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Color tokens only: every theme shares the same components and layout.
 * Every text token (fg, fg2, fg3, accent, red) reads at 4.5:1 on every one of [surfaces]; PaletteContrastTest holds each theme to it.
 */
data class Palette(
    val id: String,
    val name: String,
    val dark: Boolean,
    val bg: Color,
    val elevated: Color,
    val fg: Color,
    val fg2: Color,
    val accent: Color,
    val onAccent: Color,
    val red: Color,
    /** A selected chip's fill, which [onThumb] labels sit on: the accent; in Liquid Glass, the clear glass every neutral control is made of. */
    val thumb: Color = accent,
    /** Liquid Glass: soft glows at their peak alpha, laid over [bg] as a wallpaper the glass shows; empty for a flat theme. */
    val glows: List<Color> = emptyList(),
    /** Liquid Glass bars' and sheets' alpha, fitted by [glassed]. */
    val chromeAlpha: Float = 1f,
    /** How [thumb] is painted, resting and lifted under a finger. */
    val thumbPainter: ThumbPainter = SolidThumb,
) {
    val glass: Boolean get() = glows.isNotEmpty()

    /**
     * This theme in Liquid Glass: a wallpaper of [glowsFor] its accent, bars at [CLEAREST_CHROME], and fg, fg2, accent and red each lifted
     * toward the extreme just enough to read at 4.5:1 over everything [glassSurfaces] names, fg with tertiary text's alpha so the ladder keeps its steps.
     * Where no lift reaches, the bars go more opaque and then the glows dimmer. One instance per theme: the wallpaper cache and every token search key on it.
     * Lifts and bar alpha are bisected, since each step toward the extreme only adds contrast: a linear search held a phone's first frame for a second or two.
     */
    val glassed: Palette by lazy {
        if (glass) return@lazy this
        val toward = if (dark) Color.White else Color.Black
        // Opaque text against surfaces whose luminance is worked out once: a luminance costs a few hundred nanoseconds and this runs on the first frame.
        fun reads(c: Color, lums: List<Float>) = c.luminance().let { l -> lums.all { ratio(l, it) >= 4.5f } }
        fun lift(c: Color, ok: (Color) -> Boolean): Color? {
            fun at(i: Int) = Color(lerp(c, toward, i / 100f).toArgb())
            return if (ok(at(100))) at(least(0, 100) { ok(at(it)) }) else null
        }
        fun attempt(k: Int, a: Int): Palette? {
            val lit = copy(id = "$id-glass", glows = glowsFor(accent, k / 10f), chromeAlpha = a / 100f, thumbPainter = GlassThumb)
            val sheen = Color.White.copy(alpha = SHEEN)
            val clear = clearGlass(dark)
            val tertiary = maxOf(fg3.alpha, 0.75f)
            // fg tints the fills it sits on, so each candidate is held to the surfaces it makes: as tertiary text, and as a label under
            // a control's sheen on its own fill or on clear glass.
            val f = lift(fg) { c ->
                val l = c.luminance()
                lit.copy(fg = c).surfaces.all { s ->
                    val ls = s.luminance()
                    ratio(l, sheen.compositeOver(s).luminance()) >= 4.5f && ratio(l, sheen.compositeOver(clear.compositeOver(s)).luminance()) >= 4.5f &&
                        ratio(c.copy(alpha = tertiary).compositeOver(s).luminance(), ls) >= 4.5f
                }
            } ?: return null
            val lums = lit.copy(fg = f).surfaces.map { it.luminance() }
            val f2 = lift(fg2) { reads(it, lums) } ?: return null
            val ac = lift(accent) { reads(it, lums) } ?: return null
            // A destructive button's label reads on its red at the top of the sheen.
            val r = lift(red) { reads(it, lums) && contrast(lit.onRed, sheen.compositeOver(it)) >= 4.5f } ?: return null
            return lit.copy(
                fg = f, fg2 = f2, accent = ac, red = r, thumb = clear,
                onAccent = if (contrast(Color.Black, ac) >= contrast(Color.White, ac)) Color.Black else Color.White,
            ).takeIf { it.textReads() }
        }
        // The strongest glow that reads with opaque bars, then the clearest bars that read under it.
        if (attempt(0, 100) == null) return@lazy copy(id = "$id-glass", glows = glowsFor(accent, 0f), thumb = clearGlass(dark), thumbPainter = GlassThumb)
        val k = 10 - least(0, 10) { attempt(10 - it, 100) != null }
        attempt(k, least(CLEAREST_CHROME, 100) { attempt(k, it) != null })!!
    }
    fun withGlass(on: Boolean) = if (on) glassed else this
    /**
     * Text reads on every glass surface, a filled control's label under its sheen too (on its own fill, on clear glass, and on
     * a [stain]ed or [redStain]ed button), and a switch stands off its track.
     */
    private fun textReads() = surfaces.all { s ->
        val sheen = Color.White.copy(alpha = SHEEN)
        listOf(fg, fg2, fg3, accent, red).all { contrast(it.compositeOver(s), s) >= 4.5f } &&
            contrast(fg, sheen.compositeOver(s)) >= 4.5f && contrast(accent, fillPressed.compositeOver(s)) >= 3f &&
            contrast(fg, sheen.compositeOver(thumb.compositeOver(s))) >= 4.5f &&
            contrast(onAccent, sheen.compositeOver(stain.compositeOver(s))) >= 4.5f && contrast(onRed, sheen.compositeOver(redStain.compositeOver(s))) >= 4.5f
    }
    /**
     * Labels on [thumb]: [onAccent] on a flat theme's accent pill; [fg] on Liquid Glass's clear one, which no colored label clears
     * 4.5:1 on over bright content, so glass chips go monochrome.
     */
    val onThumb: Color get() = if (glass) fg else onAccent
    /** A primary button's fill: the accent, or in Liquid Glass the accent stained into the glass ([stained]). */
    val stain: Color by lazy { stained(accent, onAccent) }
    /** A destructive button's fill: [red], stained into the glass in Liquid Glass. */
    val redStain: Color by lazy { stained(red, onRed) }
    /** [c] at the lowest alpha down to [CLEAREST_STAIN] on which [label] under the sheen reads at 4.5:1 over every one of [surfaces]. */
    private fun stained(c: Color, label: Color): Color {
        if (!glass) return c
        val l = label.luminance()
        fun reads(a: Int) = c.copy(alpha = a / 100f).let { t -> surfaces.all { ratio(l, Color.White.copy(alpha = SHEEN).compositeOver(t.compositeOver(it)).luminance()) >= 4.5f } }
        return if (reads(100)) c.copy(alpha = least(CLEAREST_STAIN, 100, ::reads) / 100f) else c
    }

    /** A card's fill: [elevated], or in Liquid Glass a translucent pane of it over the wallpaper. */
    val card: Color get() = if (glass) elevated.copy(alpha = 0.6f) else elevated
    /** Liquid Glass bars and sheets: [bg] at [chromeAlpha], over whatever is blurred behind them. */
    val chrome: Color get() = bg.copy(alpha = chromeAlpha)
    /** Tertiary text and icons: the faintest [fg] that still reads at 4.5:1 on every surface. */
    val fg3: Color by lazy {
        fun reads(a: Int) = fg.copy(alpha = a / 100f).let { c -> surfaces.all { contrast(c.compositeOver(it), it) >= 4.5f } }
        if (reads(100)) fg.copy(alpha = least(42, 100, ::reads) / 100f) else fg
    }
    /** Day marks for lifting, cardio and stretching, fitted to this theme's surfaces. */
    val kinds: KindColors by lazy { kindColors(bg, elevated, fg) }
    /**
     * The pill behind the set being worked on: the strongest accent tint up to 18% on which fg reads at 4.5:1
     * and the accent ring at 3:1, over every one of [surfaces]. Controls on it stay opaque, so their text keeps its contrast.
     */
    val stepPill: Color by lazy {
        (18 downTo 6).map { accent.copy(alpha = it / 100f) }.firstOrNull { tint ->
            surfaces.all { s -> tint.compositeOver(s).let { contrast(fg, it) >= 4.5f && contrast(accent, it) >= 3f } }
        } ?: fill
    }
    /** Text and icons on a [red] fill; red reads at 4.5:1 on both, so either reads on red. */
    val onRed: Color get() = if (dark) bg else elevated
    /** Every background text sits on: the page, the sheet or card, and a [fill]-tinted control on each; in Liquid Glass, the worst case behind each pane. */
    val surfaces: List<Color> by lazy { if (glass) glassSurfaces(chrome) else surfaces(bg, elevated, fg) }
    val separator: Color get() = fg.copy(alpha = 0.10f)
    val fill: Color get() = fg.copy(alpha = 0.07f)
    val fillPressed: Color get() = fg.copy(alpha = 0.14f)
    val scrim: Color get() = Color.Black.copy(alpha = if (dark) 0.55f else 0.30f)

    /**
     * The wallpaper at its plainest and brightest (every glow at its peak at once), cards over it, and [bar] over all of
     * that plus whatever bright content can scroll under a bar (accent and red pills, white knobs and photos), cards on those
     * bars, and a [fill] on each.
     */
    private fun glassSurfaces(bar: Color): List<Color> {
        val walls = listOf(bg) + glows.map { it.compositeOver(bg) } + glows.fold(bg) { under, g -> g.compositeOver(under) }
        val panes = walls + walls.map { card.compositeOver(it) }
        val bars = (panes + listOf(accent, red, Color.White, Color.Black)).map { bar.compositeOver(it) }
        val all = panes + bars + bars.map { card.compositeOver(it) }
        return all + all.map { fill.compositeOver(it) }
    }
}

object Palettes {
    val jade = Palette("jade", "Jade", true, Color(0xFF142620), Color(0xFF203B31), Color(0xFFDFEDE5), Color(0xFF9BB5A8), Color(0xFF8BCBB1), Color(0xFF0F1E19), Color(0xFFDF9EA1))
    val tokyo = Palette("tokyo", "Tokyo Night", true, Color(0xFF1A1B26), Color(0xFF24283B), Color(0xFFC0CAF5), Color(0xFFA9B1D6), Color(0xFF7AA2F7), Color(0xFF1A1B26), Color(0xFFF7768E))
    val dark = Palette("dark", "Dark", true, Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFFF5F5F7), Color(0xFFA1A1A6), Color(0xFF2A94FF), Color(0xFF000000), Color(0xFFFF584E))
    val light = Palette("light", "Light", false, Color(0xFFF2F2F7), Color(0xFFFFFFFF), Color(0xFF1C1C1E), Color(0xFF65656A), Color(0xFF0062CC), Color(0xFFFFFFFF), Color(0xFFCE0014))
    /** The colors the Liquid Glass theme had before glass became a switch; a saved `glass` theme loads as Midnight with glass on. */
    val midnight = Palette("midnight", "Midnight", true, Color(0xFF0B0F1A), Color(0xFF1C202A), Color(0xFFF2F5FF), Color(0xFFB4BDD2), Color(0xFF7DBFFF), Color(0xFF0B0F1A), Color(0xFFFF9E96))
    /** Midnight in Liquid Glass, the look the Liquid Glass theme shipped with. */
    val glass get() = midnight.glassed
    /** The hand-tuned themes, which every screenshot renders. */
    val all = listOf(jade, tokyo, dark, light)

    /** Omarchy's themes (Jade and Tokyo Night are hand-tuned above), read the way the Laptop theme reads its file. */
    val omarchy = listOf(
        omarchy("catppuccin-latte", "Catppuccin Latte", "light", "#eff1f5", "#dce0e8", "#4c4f69", "#9ca0b0", "#1e66f5", "#d20f39"),
        omarchy("catppuccin", "Catppuccin", "dark", "#1e1e2e", "#313244", "#cdd6f4", "#6c7086", "#89b4fa", "#f38ba8"),
        omarchy("city-783", "City 783", "dark", "#181a1f", "#20232a", "#b9bec6", "#6a707a", "#ff5c5c", "#e53939"),
        omarchy("deep-ocean", "Deep Ocean", "dark", "#0a1a1f", "#12282f", "#c8e6e3", "#6a8f93", "#3ee6d6", "#ff6b6b"),
        omarchy("desert-dusk", "Desert Dusk", "dark", "#1f1a24", "#2c2532", "#e8d9c8", "#8a7a80", "#e8925a", "#e06c6c"),
        omarchy("ethereal", "Ethereal", "dark", "#060b1e", "#131a3a", "#ffcead", "#6d7db6", "#7d82d9", "#ed5b5a"),
        omarchy("everforest", "Everforest", "dark", "#2d353b", "#343f44", "#d3c6aa", "#4f585e", "#7fbbb3", "#e67e80"),
        omarchy("flexoki-light", "Flexoki Light", "light", "#fffcf0", "#e6e4d9", "#100f0f", "#878580", "#205ea6", "#d14d41"),
        omarchy("gruvbox", "Gruvbox", "dark", "#282828", "#3c3836", "#d4be98", "#7c6f64", "#7daea3", "#ea6962"),
        omarchy("hackerman", "Hackerman", "dark", "#0b0c16", "#151828", "#ddf7ff", "#6a6e95", "#82fb9c", "#50f872"),
        omarchy("kanagawa", "Kanagawa", "dark", "#1f1f28", "#223249", "#dcd7ba", "#727169", "#dcd7ba", "#c34043"),
        omarchy("last-horizon", "Last Horizon", "dark", "#0c0b0c", null, "#fafcfb", "#584e51", "#b59790", "#c38b7b"),
        omarchy("lumon", "Lumon", "dark", "#16242d", "#1b2d40", "#d6e2ee", "#4d86b0", "#8bc9eb", "#4d86b0"),
        omarchy("lupine", "Lupine", "light", "#fafafa", "#f5f5f5", "#212121", "#757575", "#3264eb", "#c900c4"),
        omarchy("matte-black", "Matte Black", "dark", "#121212", "#1e1e1e", "#bebebe", "#555555", "#e68e0d", "#d35f5f"),
        omarchy("miasma", "Miasma", "dark", "#222222", "#2c2c2c", "#c2c2b0", "#555555", "#78824b", "#685742"),
        omarchy("nord", "Nord", "dark", "#2e3440", "#3b4252", "#d8dee9", "#667080", "#81a1c1", "#bf616a"),
        omarchy("retro-82", "Retro 82", "dark", "#05182e", "#0a2540", "#f6dcac", "#3f8f8a", "#faa968", "#f85525"),
        omarchy("ristretto", "Ristretto", "dark", "#2c2525", "#3d2f2a", "#e6d9db", "#72696a", "#f38d70", "#fd6883"),
        omarchy("rose-pine", "Rose Pine", "light", "#faf4ed", "#f2e9e1", "#575279", "#9893a5", "#56949f", "#b4637a"),
        omarchy("solitude", "Solitude", "dark", "#101315", null, "#cacccc", "#4b4e55", "#798186", "#565d60"),
        omarchy("starship", "Starship", "dark", "#1c1c1e", "#2c2c2e", "#f5f5f7", "#98989d", "#ff9f0a", "#ff5f4a"),
        omarchy("vantablack", "Vantablack", "dark", "#000000", "#1a1a1a", "#ffffff", "#505050", "#8d8d8d", "#a4a4a4"),
        omarchy("white", "White", "light", "#ffffff", "#c0c0c0", "#000000", "#c0c0c0", "#6e6e6e", "#2a2a2a"),
    )

    /** Every theme past Light, Dark and System, by name. */
    val custom = (listOf(jade, tokyo, midnight) + omarchy).sortedBy { it.name }

    /** Light or Dark, following the phone. */
    const val SYSTEM = "system"

    fun of(id: String) = (all + midnight + omarchy).firstOrNull { it.id == id } ?: tokyo

    fun resolve(id: String, systemDark: Boolean, laptop: Palette?) = when (id) {
        SYSTEM -> if (systemDark) dark else light
        LaptopTheme.ID -> laptop ?: tokyo
        else -> of(id)
    }

    fun label(id: String) = when (id) {
        SYSTEM -> "System"
        LaptopTheme.ID -> "Laptop"
        else -> of(id).name
    }

    private fun omarchy(id: String, name: String, mode: String, bg: String, lighterBg: String?, fg: String, darkFg: String?, accent: String, red: String) =
        LaptopTheme.parse(
            listOf("mode" to mode, "background" to bg, "lighter_background" to lighterBg, "foreground" to fg, "dark_foreground" to darkFg, "accent" to accent, "red" to red)
                .mapNotNull { (k, v) -> v?.let { "$k = \"$it\"" } }.joinToString("\n"),
        )!!.copy(id = id, name = name)
}

/** The clearest Liquid Glass bars get, in percent: the alpha the Liquid Glass theme was tuned with. */
const val CLEAREST_CHROME = 82

/** The clearest a stained button gets, in percent: below this, over dark glass, it reads as dimmed, like a disabled button. */
const val CLEAREST_STAIN = 85

/** Liquid Glass's clear control, a frost over whatever is behind it: faint on dark glass, a brighter sheet on light glass, as iOS's. */
fun clearGlass(dark: Boolean) = Color.White.copy(alpha = if (dark) 0.14f else 0.6f)

/**
 * Liquid Glass's three wallpaper glows for a theme with [accent]: the accent, a neighbor a little warmer and one a little cooler on the
 * hue wheel (blue, violet and teal for a blue accent), at [strength] of the peaks Liquid Glass was tuned with.
 */
fun glowsFor(accent: Color, strength: Float): List<Color> {
    val r = accent.red; val g = accent.green; val b = accent.blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
    val h = when {
        d == 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    // Saturated, as Liquid Glass's blue, violet and teal were: a vivid glow tints the glass without lifting it toward the text; a gray accent stays gray.
    val s = (if (max == 0f) 0f else d / max).let { if (it < 0.15f) it else maxOf(it, 0.82f) }
    return listOf(0f to 0.18f, 50f to 0.14f, -40f to 0.10f).map { (turn, peak) -> Color.hsv(((h + turn) % 360f + 360f) % 360f, s, max, peak * strength) }
}

fun surfaces(bg: Color, elevated: Color, fg: Color): List<Color> =
    listOf(bg, elevated, fg.copy(alpha = 0.07f).compositeOver(bg), fg.copy(alpha = 0.07f).compositeOver(elevated))

/** The least of [lo]..[hi] at which [ok] holds, given it holds at [hi] and, once it holds, holds above too. */
private fun least(lo: Int, hi: Int, ok: (Int) -> Boolean): Int {
    var no = lo - 1
    var yes = hi
    while (yes - no > 1) (no + yes).div(2).let { if (ok(it)) yes = it else no = it }
    return yes
}

/** WCAG contrast ratio, 1 to 21. */
fun contrast(a: Color, b: Color): Float = ratio(a.luminance(), b.luminance())

private fun ratio(a: Float, b: Float) = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)

val LocalPalette = staticCompositionLocalOf { Palettes.tokyo }

@Composable
fun GzTheme(palette: Palette, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalPalette provides palette, content = content)

/** iOS phone type ladder; weights 400/600/700 only. */
object Type {
    private val tnum = "tnum"
    val largeTitle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp, letterSpacing = (-0.022).em)
    val title1 = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, letterSpacing = (-0.019).em, fontFeatureSettings = tnum)
    val title2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp, letterSpacing = (-0.015).em)
    val title3 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp, letterSpacing = (-0.01).em)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, letterSpacing = (-0.005).em)
    val body = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val subhead = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp, letterSpacing = 0.01.em)
    val display = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.SemiBold, lineHeight = 72.sp, letterSpacing = (-0.02).em, fontFeatureSettings = tnum)

    fun TextStyle.tabular() = copy(fontFeatureSettings = tnum)
}

object Space {
    val xs = 4.dp; val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 20.dp; val xxl = 24.dp; val x3 = 32.dp; val x4 = 48.dp
}

object Radius {
    val sm = 6.dp; val md = 10.dp; val lg = 14.dp; val xl = 20.dp
}
