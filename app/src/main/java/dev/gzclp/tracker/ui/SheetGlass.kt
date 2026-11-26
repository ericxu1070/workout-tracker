package dev.gzclp.tracker.ui

import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** How many [Sheet]s are open; while any is, the screens under them blur and fade toward their background ([underSheets]). */
val LocalSheetsOpen = compositionLocalOf { mutableIntStateOf(0) }

/** Counts an open sheet ([on]) in [LocalSheetsOpen]. */
@Composable
fun SheetOpen(on: Boolean) {
    val open = LocalSheetsOpen.current
    DisposableEffect(on, open) {
        if (on) open.intValue++
        onDispose { if (on) open.intValue-- }
    }
}

/** How far the screen under an open sheet fades toward its background, so whatever lies there stays faint behind the glass. */
const val SHEET_WASH = 0.75f

/** The most a sheet's own fill lets through: the floor that keeps it reading as a solid sheet. */
const val SHEET_MIN_ALPHA = 0.75f

val SheetBlur = 24.dp

/**
 * Blurs and fades what this draws while a sheet is open over it, so a translucent sheet shows only its soft colors.
 * Not in Liquid Glass, whose sheet is a [glassPane] that blurs what is behind it itself.
 */
@Composable
fun Modifier.underSheets(): Modifier {
    val p = LocalPalette.current
    if (p.glass) return this
    val open = LocalSheetsOpen.current.intValue > 0
    val t = animateFloatAsState(if (open) 1f else 0f, tween(if (open) 300 else 200), label = "underSheets")
    // Clipped, so the blur stays inside what it blurs.
    return graphicsLayer { renderEffect = backdrop(t.value, SheetBlur.toPx(), p.bg); clip = t.value > 0f }
}

/** [t] of the way to a full [SHEET_WASH] toward [bg] and a [blur] px blur; nothing at 0. */
fun backdrop(t: Float, blur: Float, bg: Color): RenderEffect? {
    if (t <= 0f) return null
    val k = SHEET_WASH * t
    val keep = 1f - k
    val wash = ColorMatrixColorFilter(
        floatArrayOf(
            keep, 0f, 0f, 0f, bg.red * k * 255f,
            0f, keep, 0f, 0f, bg.green * k * 255f,
            0f, 0f, keep, 0f, bg.blue * k * 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val r = (blur * t).coerceAtLeast(0.01f)
    return android.graphics.RenderEffect.createChainEffect(
        android.graphics.RenderEffect.createColorFilterEffect(wash),
        android.graphics.RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP),
    ).asComposeRenderEffect()
}

/**
 * The opacity of a sheet's fill in [p]: the most see-through, down to [SHEET_MIN_ALPHA], at which every text color still reads at 4.5:1
 * on the sheet and on a control's fill on it, whatever lies under it: any of the theme's colors, faded by [SHEET_WASH] and dimmed by the scrim.
 */
fun sheetAlpha(p: Palette): Float = sheetAlphas.getOrPut(p) {
    // Liquid Glass sheets are glass panes ([glassPane]), tinted with [Palette.chrome] instead.
    (SHEET_MIN_ALPHA * 100).toInt().rangeTo(100).map { it / 100f }.firstOrNull { a -> sheetReads(p, a) } ?: 1f
}

private val sheetAlphas = java.util.concurrent.ConcurrentHashMap<Palette, Float>()

/** Whether every text color reads at 4.5:1 on [p]'s sheet at opacity [a] over every washed and scrimmed theme color. */
fun sheetReads(p: Palette, a: Float): Boolean =
    listOf(p.bg, p.elevated, p.fg, p.accent, p.red).all { under ->
        val sheet = p.elevated.copy(alpha = a).compositeOver(p.scrim.compositeOver(under.copy(alpha = 1f - SHEET_WASH).compositeOver(p.bg)))
        listOf(sheet, p.fill.compositeOver(sheet)).all { s ->
            listOf(p.fg, p.fg2, p.fg3, p.accent, p.red).all { contrast(it.compositeOver(s), s) >= 4.5f }
        }
    }
