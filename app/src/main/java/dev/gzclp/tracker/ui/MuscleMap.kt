package dev.gzclp.tracker.ui

import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.log.Muscle
import dev.gzclp.tracker.log.MuscleWork
import dev.gzclp.tracker.log.Muscles
import dev.gzclp.tracker.ui.Type.tabular
import kotlin.math.min
import kotlin.math.roundToInt

/** One drawn side of the body: its view box, each region's muscle (null for skin the map never shades) and path, and the silhouette. */
internal class BodyView(val x: Float, val y: Float, val w: Float, val h: Float, val parts: List<Pair<Muscle?, Path>>, val outline: Path)

/**
 * The front and rear anatomy charts, male and female: react-native-body-highlighter 3.2.0's paths (MIT, see NOTICE),
 * extracted by tools/muscle-paths.py. Each line of the file is "view <id> <x> <y> <w> <h>" or "<id> <slug> <side> <d>".
 */
internal object BodyPaths {
    val views: Map<String, BodyView> by lazy {
        val boxes = HashMap<String, FloatArray>()
        val parts = HashMap<String, MutableList<Pair<Muscle?, Path>>>()
        val outlines = HashMap<String, Path>()
        BodyPaths::class.java.getResourceAsStream("muscles.txt")!!.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isBlank()) continue
                val f = line.split(' ', limit = 4)
                if (f[0] == "view") { boxes[f[1]] = line.split(' ').drop(2).map { it.toFloat() }.toFloatArray(); continue }
                val path = PathParser().parsePathString(f[3]).toPath()
                if (f[1] == "outline") outlines[f[0]] = path
                else parts.getOrPut(f[0]) { ArrayList() } += muscleOf(f[1], front = f[0].endsWith("f")) to path
            }
        }
        boxes.mapValues { (id, b) -> BodyView(b[0], b[1], b[2], b[3], parts.getValue(id), outlines.getValue(id)) }
    }

    private fun muscleOf(slug: String, front: Boolean): Muscle? = when (slug) {
        "chest" -> Muscle.CHEST
        "deltoids" -> if (front) Muscle.SHOULDERS else Muscle.REAR_DELTS
        "biceps" -> Muscle.BICEPS
        "triceps" -> Muscle.TRICEPS
        "forearm" -> Muscle.FOREARMS
        "abs" -> Muscle.ABS
        "obliques" -> Muscle.OBLIQUES
        "trapezius" -> Muscle.TRAPS
        "upper-back" -> Muscle.UPPER_BACK
        "lower-back" -> Muscle.LOWER_BACK
        "gluteal" -> Muscle.GLUTES
        "quadriceps" -> Muscle.QUADS
        "hamstring" -> Muscle.HAMSTRINGS
        "adductors" -> Muscle.ADDUCTORS
        "calves" -> Muscle.CALVES
        else -> null
    }
}

/** A muscle's fill at [level] (0..1) of a full week: the base when untrained, then from a pale accent tint to the full accent. */
private fun muscleFill(p: Palette, level: Float): Color = if (level <= 0f) p.muscle else lerp(p.muscle, p.accent, 0.25f + 0.75f * level.coerceIn(0f, 1f))

/** Where a tap landed: the muscle it hit, the tap's height, and the muscle's left and right edges, in the chart's pixels. */
private class Picked(val muscle: Muscle, val y: Float, val left: Float, val right: Float)

/**
 * The flat anatomy chart: front and rear side by side, each muscle filled by its share of the window's [work] over
 * [days] (see [Muscles.levels]). Tapping a muscle outlines it and shows its name and set count above it; tapping
 * anywhere else hides them.
 */
@Composable
fun MuscleChart(work: Map<Muscle, MuscleWork>, days: Long, female: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val g = if (female) "f" else "m"
    val views = listOf(BodyPaths.views.getValue(g + "f"), BodyPaths.views.getValue(g + "b"))
    val levels = remember(work, days) { Muscles.levels(work, days) }
    var picked by remember(female) { mutableStateOf<Picked?>(null) }
    val gap = with(LocalDensity.current) { Space.lg.toPx() }
    val said = work.entries.filter { it.value.sets > 0 }.sortedByDescending { it.value.sets }
        .joinToString(", ") { "${it.key.label} ${sets(it.value.sets)}" }.ifEmpty { "none" }
    Box(modifier.semantics { contentDescription = "Muscles worked: $said" }) {
        Canvas(
            Modifier.matchParentSize().graphicsLayer().pointerInput(views) {
                detectTapGestures { tap ->
                    picked = views.withIndex().firstNotNullOfOrNull { (i, v) ->
                        val f = fit(v, i, size.width.toFloat(), size.height.toFloat(), gap)
                        val x = (tap.x - f.dx) / f.s + v.x
                        val y = (tap.y - f.dy) / f.s + v.y
                        v.parts.firstOrNull { (m, path) -> m != null && path.contains(x, y) }?.let { (m, _) ->
                            val edges = v.parts.filter { it.first == m }.map { it.second.getBounds() }
                            Picked(m!!, tap.y, (edges.minOf { it.left } - v.x) * f.s + f.dx, (edges.maxOf { it.right } - v.x) * f.s + f.dx)
                        }
                    }
                }
            },
        ) {
            views.forEachIndexed { i, v ->
                val f = fit(v, i, size.width, size.height, gap)
                translate(f.dx, f.dy) {
                    scale(f.s, f.s, Offset.Zero) {
                        translate(-v.x, -v.y) {
                            for ((m, path) in v.parts) drawPath(path, if (m == null) p.muscle else muscleFill(p, levels[m] ?: 0f))
                            drawPath(v.outline, p.fg.copy(alpha = 0.22f), style = Stroke(1.dp.toPx() / f.s))
                            picked?.let { sel ->
                                for ((m, path) in v.parts) if (m == sel.muscle) drawPath(path, p.fg, style = Stroke(2.dp.toPx() / f.s))
                            }
                        }
                    }
                }
            }
        }
        picked?.let { sel ->
            Callout(sel) {
                Row(
                    Modifier.shadow(8.dp, CircleShape).background(p.elevated, CircleShape).padding(horizontal = Space.md, vertical = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Txt(sel.muscle.label, Type.headline, maxLines = 1)
                    Txt(sets(work[sel.muscle]?.sets ?: 0), Type.headline.tabular(), color = p.fg2, maxLines = 1)
                }
            }
        }
    }
}

/** The map's key in one line: a swatch from untrained to fully shaded, and the sets the full shade stands for; with no sets, just that. */
@Composable
fun MuscleScale(work: Map<Muscle, MuscleWork>, days: Long, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val none = work.values.none { it.sets > 0 }
    val full = Muscles.full(work, days).roundToInt().coerceAtLeast(1)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (!none) Box(Modifier.size(48.dp, 8.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(p.muscle, muscleFill(p, 1f)))))
        Txt(if (none) sets(0) else "0 to ${sets(full)}", Type.footnote.tabular(), color = p.fg2)
    }
}

private fun sets(n: Int) = when (n) { 0 -> "No sets"; 1 -> "1 set"; else -> "$n sets" }

/** Side [i] of the chart (0 front, 1 rear) fitted into its half of a [w] by [h] canvas: its scale and offset. */
internal class Fit(val s: Float, val dx: Float, val dy: Float)

internal fun fit(v: BodyView, i: Int, w: Float, h: Float, gap: Float): Fit {
    val half = (w - gap) / 2
    val s = min(half / v.w, h / v.h)
    return Fit(s, i * (half + gap) + (half - v.w * s) / 2, (h - v.h * s) / 2)
}

/** Whether ([x], [y]) in the path's own units lies inside it. */
private fun Path.contains(x: Float, y: Float): Boolean {
    val b = getBounds()
    if (!b.contains(Offset(x, y))) return false
    val clip = Region(b.left.toInt() - 1, b.top.toInt() - 1, b.right.toInt() + 1, b.bottom.toInt() + 1)
    return Region().apply { setPath(asAndroidPath(), clip) }.contains(x.toInt(), y.toInt())
}

/** Places [content] beside the picked muscle, level with the tap: past its right edge, or its left when there is no room; kept inside the chart. */
@Composable
private fun Callout(at: Picked, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val c = measurables.single().measure(Constraints())
        val gap = Space.md.roundToPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            val right = at.right.toInt() + gap
            val x = if (right + c.width <= constraints.maxWidth) right else (at.left.toInt() - gap - c.width).coerceAtLeast(0)
            c.place(x, (at.y.toInt() - c.height / 2).coerceIn(0, (constraints.maxHeight - c.height).coerceAtLeast(0)))
        }
    }
}
