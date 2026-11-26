package dev.gzclp.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import dev.gzclp.tracker.form.Heat
import dev.gzclp.tracker.form.AT_EASE
import dev.gzclp.tracker.log.Muscle
import kotlin.math.min

/** One drawn side of the body: its view box, each region's muscle (null for skin the map never shades) and path, and the silhouette. */
private class BodyView(val x: Float, val y: Float, val w: Float, val h: Float, val parts: List<Pair<Muscle?, Path>>, val outline: Path)

/**
 * The front and rear anatomy charts, male and female: react-native-body-highlighter 3.2.0's paths (MIT, see NOTICE),
 * extracted by tools/muscle-paths.py. Each line of the file is "view <id> <x> <y> <w> <h>" or "<id> <slug> <side> <d>".
 */
private object BodyPaths {
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

/** An untrained muscle's fill, and skin's. */
fun muscleBase(p: Palette): Color = lerp(p.bg, p.fg, if (p.dark) 0.16f else 0.12f)

/** A muscle's fill at [level] (0..1) of a full week: the base when untrained, then from a pale accent tint to the full accent. */
fun muscleFill(p: Palette, level: Float): Color = if (level <= 0f) muscleBase(p) else lerp(muscleBase(p), p.accent, 0.25f + 0.75f * level.coerceIn(0f, 1f))

/** The flat anatomy chart: front and rear side by side, each muscle filled by its [levels] share (0..1). */
@Composable
fun MuscleChart(levels: Map<Muscle, Float>, female: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val g = if (female) "f" else "m"
    Row(modifier.semantics { contentDescription = "Muscles worked, front and back" }, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        for (side in listOf("f", "b")) {
            val v = BodyPaths.views.getValue(g + side)
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val s = min(size.width / v.w, size.height / v.h)
                translate((size.width - v.w * s) / 2, (size.height - v.h * s) / 2) {
                    scale(s, s, Offset.Zero) {
                        translate(-v.x, -v.y) {
                            for ((m, path) in v.parts) drawPath(path, if (m == null) muscleBase(p) else muscleFill(p, levels[m] ?: 0f))
                            drawPath(v.outline, p.fg.copy(alpha = 0.22f), style = Stroke(1.dp.toPx() / s))
                        }
                    }
                }
            }
        }
    }
}

/** The muscle map on the 3D figure, seen from the front and from behind; each muscle tinted by its [levels] share. [still] false redraws it every frame (the frame-time bench). */
@Composable
fun MuscleBody(levels: Map<Muscle, Float>, modifier: Modifier = Modifier, still: Boolean = true) {
    val heat = remember(levels) { Heat(levels) }
    Row(modifier.semantics { contentDescription = "Muscles worked, front and back" }, horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
        for (yaw in listOf(0f, 180f)) {
            val orbit = remember(yaw) { Orbit(yaw).apply { pitch = 4f; held = true } }
            FormView(AT_EASE, Modifier.weight(1f).fillMaxHeight(), focus = heat, still = if (still) 0f else null, orbit = orbit, zoom = 1.75f, skin = muscleBase(LocalPalette.current))
        }
    }
}
