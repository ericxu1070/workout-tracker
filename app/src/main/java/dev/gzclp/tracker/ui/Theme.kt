package dev.gzclp.tracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
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
) {
    /** Tertiary text and icons: the faintest [fg] that still reads at 4.5:1 on every surface. */
    val fg3: Color by lazy {
        (42..100).map { fg.copy(alpha = it / 100f) }.firstOrNull { c -> surfaces.all { contrast(c.compositeOver(it), it) >= 4.5f } } ?: fg
    }
    /** Text and icons on a [red] fill; red reads at 4.5:1 on both, so either reads on red. */
    val onRed: Color get() = if (dark) bg else elevated
    /** Every background text sits on: the page, the sheet or card, and a [fill]-tinted control on each. */
    val surfaces: List<Color> get() = surfaces(bg, elevated, fg)
    val separator: Color get() = fg.copy(alpha = 0.10f)
    val fill: Color get() = fg.copy(alpha = 0.07f)
    val fillPressed: Color get() = fg.copy(alpha = 0.14f)
    val scrim: Color get() = Color.Black.copy(alpha = if (dark) 0.55f else 0.30f)
}

object Palettes {
    val jade = Palette("jade", "Jade", true, Color(0xFF142620), Color(0xFF203B31), Color(0xFFDFEDE5), Color(0xFF9BB5A8), Color(0xFF8BCBB1), Color(0xFF0F1E19), Color(0xFFDF9EA1))
    val tokyo = Palette("tokyo", "Tokyo Night", true, Color(0xFF1A1B26), Color(0xFF24283B), Color(0xFFC0CAF5), Color(0xFFA9B1D6), Color(0xFF7AA2F7), Color(0xFF1A1B26), Color(0xFFF7768E))
    val dark = Palette("dark", "Dark", true, Color(0xFF000000), Color(0xFF1C1C1E), Color(0xFFF5F5F7), Color(0xFFA1A1A6), Color(0xFF2A94FF), Color(0xFF000000), Color(0xFFFF584E))
    val light = Palette("light", "Light", false, Color(0xFFF2F2F7), Color(0xFFFFFFFF), Color(0xFF1C1C1E), Color(0xFF65656A), Color(0xFF0062CC), Color(0xFFFFFFFF), Color(0xFFCE0014))
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
    val custom = (listOf(jade, tokyo) + omarchy).sortedBy { it.name }

    /** Light or Dark, following the phone. */
    const val SYSTEM = "system"

    fun of(id: String) = (all + omarchy).firstOrNull { it.id == id } ?: tokyo

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

fun surfaces(bg: Color, elevated: Color, fg: Color): List<Color> =
    listOf(bg, elevated, fg.copy(alpha = 0.07f).compositeOver(bg), fg.copy(alpha = 0.07f).compositeOver(elevated))

/** WCAG contrast ratio, 1 to 21. */
fun contrast(a: Color, b: Color): Float {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05f) / (lo + 0.05f)
}

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
