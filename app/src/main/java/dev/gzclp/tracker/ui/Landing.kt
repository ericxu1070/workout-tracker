package dev.gzclp.tracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * A logged set's check landing like a plate set down: the disc squashes and recovers, one ring runs out along the row (the whole row
 * for an exercise's last set), and a record adds a deeper squash, a crown of droplets, and a ring past the row's ends.
 * Every value is read while drawing, so it moves no row and recomposes nothing.
 */
class Landing {
    internal val squash = Animatable(0f)
    internal val ring = Animatable(1f)
    internal val splash = Animatable(1f)
    internal var reach = 0f
    internal var record = false

    suspend fun play(record: Boolean, last: Boolean) = coroutineScope {
        this@Landing.record = record
        reach = if (record) 1.2f else if (last) 1f else 0f
        launch {
            squash.snapTo(if (record) 1f else 0.6f)
            squash.animateTo(0f, landSpring())
        }
        launch {
            ring.snapTo(0f)
            ring.animateTo(1f, tween(if (record) 700 else 380, easing = EaseOutCubic))
        }
        if (record) launch {
            splash.snapTo(0f)
            splash.animateTo(1f, tween(650, easing = LinearEasing))
        }
    }
}

/** The disc's squash: wider and shorter as it lands. */
fun Modifier.landingSquash(l: Landing) = graphicsLayer {
    val s = l.squash.value * SQUASH
    scaleX = 1f + s
    scaleY = 1f - s
}

/** The ring (behind the row's fields, so their digits stay clear) and a record's droplets, from the check at the row's end, [check] across. */
fun Modifier.landingRing(l: Landing, color: Color, check: Dp) = drawWithContent {
    val c = Offset(size.width - check.toPx() / 2, size.height / 2)
    val t = l.ring.value
    if (t < 1f) {
        val from = check.toPx() / 2
        val to = if (l.reach > 0f) size.width * l.reach else LocalReach.toPx()
        drawCircle(color.copy(alpha = (if (l.record) 0.5f else 0.35f) * (1f - t)), from + (to - from) * t, c, style = Stroke(2.dp.toPx()))
    }
    drawContent()
    val k = l.splash.value
    if (k < 1f) {
        val secs = k * 0.65f
        val g = 1400.dp.toPx()
        val v = 330.dp.toPx()
        for (i in 0 until DROPS) {
            // Fanned up and out over 120 degrees, the outer ones a little slower.
            val a = Math.toRadians(-150.0 + 120.0 * i / (DROPS - 1)).toFloat()
            val speed = v * (if (i == 0 || i == DROPS - 1) 0.8f else 1f)
            val p = c + Offset(cos(a) * speed * secs, sin(a) * speed * secs + g * secs * secs / 2)
            drawCircle(color.copy(alpha = (1f - k).coerceAtMost(0.4f) / 0.4f), 3.dp.toPx() * (1f - k * 0.5f), p)
        }
    }
}

private const val SQUASH = 0.14f
private const val DROPS = 6
private val LocalReach = 84.dp
