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
    val all = listOf(jade, tokyo, dark, light)
    fun of(id: String) = all.firstOrNull { it.id == id } ?: tokyo
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
