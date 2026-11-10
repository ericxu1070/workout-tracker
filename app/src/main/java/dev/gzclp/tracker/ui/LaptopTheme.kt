package dev.gzclp.tracker.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Maps the laptop's Omarchy theme (`omarchy-theme.toml` in the sync folder: a `name` line plus
 * the theme's colors.toml keys) onto the app's palette tokens.
 */
object LaptopTheme {
    const val FILE = "omarchy-theme.toml"
    const val ID = "laptop"

    private val line = Regex("""^\s*([a-z_]+)\s*=\s*"([^"]*)"\s*$""")
    private val hex = Regex("^#[0-9a-fA-F]{6}$")

    /** Every key absent or malformed in [text] keeps [base]'s value, so a half-written file never breaks the UI. */
    fun parse(text: String, base: Palette): Palette {
        val kv = text.lines().mapNotNull { line.find(it)?.destructured?.let { (k, v) -> k to v } }.toMap()
        fun color(key: String, fallback: Color) = kv[key]?.takeIf(hex::matches)?.let { Color(("FF" + it.drop(1)).toLong(16)) } ?: fallback
        val bg = color("background", base.bg)
        val fg = color("foreground", base.fg)
        val accent = color("accent", base.accent)
        return Palette(
            id = ID,
            name = kv["name"]?.takeIf { it.isNotBlank() } ?: base.name,
            dark = when (kv["mode"]) { "light" -> false; "dark" -> true; else -> base.dark },
            bg = bg,
            elevated = color("lighter_background", base.elevated),
            fg = fg,
            fg2 = readable(color("dark_foreground", base.fg2), fg, bg),
            accent = accent,
            onAccent = if (contrast(Color.Black, accent) >= contrast(Color.White, accent)) Color.Black else Color.White,
            red = color("red", base.red),
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

    /** Secondary text must reach 4.5:1; otherwise take the dimmest foreground-background blend that does. */
    private fun readable(fg2: Color, fg: Color, bg: Color): Color {
        if (contrast(fg2, bg) >= 4.5f) return fg2
        return (60 downTo 0 step 5).map { lerp(fg, bg, it / 100f) }.firstOrNull { contrast(it, bg) >= 4.5f } ?: fg
    }
}
