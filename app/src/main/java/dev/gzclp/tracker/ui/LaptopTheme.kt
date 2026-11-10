package dev.gzclp.tracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Maps the laptop's Omarchy theme (`omarchy-theme.toml` in the sync folder: a `name` line plus
 * the theme's colors, resolved by `omarchy-theme-color --all` or a raw colors.toml) onto the app's palette tokens.
 */
object LaptopTheme {
    const val FILE = "omarchy-theme.toml"
    const val ID = "laptop"
    private val DANGER = Color(0xFFE5484D)

    private val line = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*"([^"]*)"""")
    private val hex = Regex("^#[0-9a-fA-F]{6}$")

    /**
     * Every token comes from this file alone: keys go through Omarchy's own alias cascade
     * (omarchy-theme-color), and what is still missing is derived from the theme's background and foreground.
     * Null when the file has neither, so the caller keeps the whole last good palette.
     */
    fun parse(text: String): Palette? {
        val kv = text.lines().mapNotNull { line.find(it)?.destructured?.let { (k, v) -> k to v.trim() } }.toMap()
        fun color(vararg keys: String) = keys.firstNotNullOfOrNull { k -> kv[k]?.takeIf(hex::matches)?.let { Color(("FF" + it.drop(1)).toLong(16)) } }
        val bg = color("background", "bg", "color0") ?: return null
        val fg = color("foreground", "fg", "color7") ?: return null
        // The app sets accent as text too, so take the theme's readable companion when it names one (city-783 does).
        val accent = color("text_accent", "accent", "blue", "color4") ?: fg
        // Omarchy resolves a missing lighter_background or dark_foreground to background or foreground; treat that as missing.
        val elevated = color("lighter_background", "lighter_bg")?.takeIf { it != bg } ?: lerp(bg, fg, 0.08f)
        val fg2 = color("dark_foreground", "dark_fg", "color8")?.takeIf { it != fg }
        val dark = when (kv["mode"] ?: kv["theme_type"]) {
            "light" -> false
            "dark" -> true
            else -> bg.toArgb().let { (it shr 16 and 0xFF) + (it shr 8 and 0xFF) + (it and 0xFF) } <= 382
        }
        return Palette(
            id = ID,
            name = kv["name"]?.takeIf { it.isNotBlank() } ?: "Laptop",
            dark = dark,
            bg = bg,
            elevated = elevated,
            fg = fg,
            fg2 = readable(fg2, fg, bg, elevated),
            accent = accent,
            onAccent = if (contrast(Color.Black, accent) >= contrast(Color.White, accent)) Color.Black else Color.White,
            red = color("red", "color1") ?: DANGER,
        )
    }

    /** The palette written back in the file's own format, for caching the last good theme. */
    fun encode(p: Palette): String = buildString {
        fun hex(c: Color) = "#%06x".format(c.toArgb() and 0xFFFFFF)
        appendLine("name = \"${p.name}\"")
        appendLine("mode = \"${if (p.dark) "dark" else "light"}\"")
        listOf("background" to p.bg, "lighter_background" to p.elevated, "foreground" to p.fg, "dark_foreground" to p.fg2, "accent" to p.accent, "red" to p.red)
            .forEach { (k, c) -> appendLine("$k = \"${hex(c)}\"") }
    }

    fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** Secondary text must reach 4.5:1 on both surfaces; otherwise take the dimmest foreground-background blend that does. */
    private fun readable(fg2: Color?, fg: Color, bg: Color, elevated: Color): Color {
        fun ok(c: Color) = contrast(c, bg) >= 4.5f && contrast(c, elevated) >= 4.5f
        if (fg2 != null && ok(fg2)) return fg2
        return (60 downTo 0 step 5).map { lerp(fg, bg, it / 100f) }.firstOrNull(::ok) ?: fg
    }
}
