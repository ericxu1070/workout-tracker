package dev.gzclp.tracker.ui

import androidx.compose.runtime.LaunchedEffect

import kotlinx.coroutines.delay

import androidx.compose.ui.draw.drawWithContent

import androidx.compose.ui.layout.positionInRoot

import androidx.compose.ui.layout.onGloballyPositioned

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.LogCodec
import dev.gzclp.tracker.log.WaterLog
import dev.gzclp.tracker.ui.Type.tabular
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.math.roundToInt

data class Point(val x: Float, val y: Float)

val KEYFRAME_SIZES = listOf(100, 325, 550, 775, 1000)

val KEYFRAMES = listOf(
    // 0: Espresso cup (100 ml)
    listOf(
        Point(0.28f, 0.50f), // 0: rim left
        Point(0.38f, 0.50f), // 1
        Point(0.50f, 0.50f), // 2: rim center
        Point(0.62f, 0.50f), // 3
        Point(0.72f, 0.50f), // 4: rim right
        Point(0.71f, 0.55f), // 5: handle root top
        Point(0.85f, 0.58f), // 6: handle outer upper
        Point(0.92f, 0.67f), // 7: handle apex
        Point(0.85f, 0.76f), // 8: handle outer lower
        Point(0.67f, 0.78f), // 9: handle root bottom
        Point(0.66f, 0.82f), // 10
        Point(0.64f, 0.86f), // 11
        Point(0.62f, 0.90f), // 12: base right
        Point(0.54f, 0.90f), // 13: base center right
        Point(0.46f, 0.90f), // 14: base center left
        Point(0.38f, 0.90f), // 15: base left
        Point(0.36f, 0.86f), // 16
        Point(0.34f, 0.82f), // 17
        Point(0.31f, 0.68f), // 18
        Point(0.28f, 0.55f), // 19
    ),
    // 1: Tumbler (325 ml)
    listOf(
        Point(0.25f, 0.35f), // 0: rim left
        Point(0.37f, 0.35f), // 1
        Point(0.50f, 0.35f), // 2: rim center
        Point(0.63f, 0.35f), // 3
        Point(0.75f, 0.35f), // 4: rim right
        Point(0.74f, 0.44f), // 5: collapsed handle points along right wall
        Point(0.73f, 0.53f), // 6
        Point(0.72f, 0.62f), // 7
        Point(0.71f, 0.71f), // 8
        Point(0.70f, 0.80f), // 9
        Point(0.69f, 0.85f), // 10
        Point(0.685f, 0.88f), // 11
        Point(0.68f, 0.92f), // 12: base right
        Point(0.56f, 0.92f), // 13: base center right
        Point(0.44f, 0.92f), // 14: base center left
        Point(0.32f, 0.92f), // 15: base left
        Point(0.315f, 0.88f), // 16
        Point(0.31f, 0.85f), // 17
        Point(0.29f, 0.68f), // 18
        Point(0.27f, 0.52f), // 19
    ),
    // 2: Tall glass (550 ml)
    listOf(
        Point(0.28f, 0.15f), // 0: rim left
        Point(0.39f, 0.15f), // 1
        Point(0.50f, 0.15f), // 2: rim center
        Point(0.61f, 0.15f), // 3
        Point(0.72f, 0.15f), // 4: rim right
        Point(0.71f, 0.28f), // 5: slender right wall
        Point(0.70f, 0.41f), // 6
        Point(0.69f, 0.54f), // 7
        Point(0.68f, 0.67f), // 8
        Point(0.67f, 0.80f), // 9
        Point(0.66f, 0.85f), // 10
        Point(0.655f, 0.90f), // 11
        Point(0.65f, 0.94f), // 12: base right
        Point(0.55f, 0.94f), // 13: base center right
        Point(0.45f, 0.94f), // 14: base center left
        Point(0.35f, 0.94f), // 15: base left
        Point(0.345f, 0.90f), // 16
        Point(0.34f, 0.85f), // 17
        Point(0.32f, 0.61f), // 18
        Point(0.30f, 0.38f), // 19
    ),
    // 3: Bottle (775 ml)
    listOf(
        Point(0.44f, 0.08f), // 0: cap left
        Point(0.47f, 0.08f), // 1
        Point(0.50f, 0.08f), // 2: cap center
        Point(0.53f, 0.08f), // 3
        Point(0.56f, 0.08f), // 4: cap right
        Point(0.56f, 0.22f), // 5: neck right
        Point(0.62f, 0.30f), // 6: shoulder right
        Point(0.70f, 0.38f), // 7: body right upper
        Point(0.72f, 0.48f), // 8
        Point(0.72f, 0.62f), // 9: body right mid
        Point(0.72f, 0.76f), // 10
        Point(0.71f, 0.88f), // 11
        Point(0.70f, 0.94f), // 12: base right
        Point(0.57f, 0.94f), // 13: base center right
        Point(0.43f, 0.94f), // 14: base center left
        Point(0.30f, 0.94f), // 15: base left
        Point(0.29f, 0.88f), // 16
        Point(0.28f, 0.76f), // 17
        Point(0.28f, 0.48f), // 18: shoulder left
        Point(0.44f, 0.22f), // 19: neck left
    ),
    // 4: Jug (1000 ml)
    listOf(
        Point(0.15f, 0.12f), // 0: spout tip left
        Point(0.28f, 0.16f), // 1
        Point(0.42f, 0.19f), // 2
        Point(0.56f, 0.21f), // 3
        Point(0.68f, 0.22f), // 4: rim right
        Point(0.70f, 0.28f), // 5: handle root top
        Point(0.86f, 0.36f), // 6: handle outer top
        Point(0.95f, 0.50f), // 7: handle apex
        Point(0.88f, 0.66f), // 8: handle outer bottom
        Point(0.76f, 0.72f), // 9: handle root bottom
        Point(0.78f, 0.78f), // 10
        Point(0.76f, 0.86f), // 11
        Point(0.74f, 0.92f), // 12: base right
        Point(0.58f, 0.92f), // 13: base center right
        Point(0.42f, 0.92f), // 14: base center left
        Point(0.26f, 0.92f), // 15: base left
        Point(0.24f, 0.86f), // 16
        Point(0.22f, 0.74f), // 17
        Point(0.18f, 0.48f), // 18: belly left
        Point(0.14f, 0.26f), // 19: under-spout neck
    ),
)

fun interpolatePoints(cupSizeMl: Int): List<Point> {
    val size = cupSizeMl.coerceIn(100, 1000)
    for (i in 0 until KEYFRAME_SIZES.size - 1) {
        val s0 = KEYFRAME_SIZES[i]
        val s1 = KEYFRAME_SIZES[i + 1]
        if (size in s0..s1) {
            val t = (size - s0).toFloat() / (s1 - s0)
            if (t <= 0f) return KEYFRAMES[i]
            if (t >= 1f) return KEYFRAMES[i + 1]
            val k0 = KEYFRAMES[i]
            val k1 = KEYFRAMES[i + 1]
            return List(k0.size) { idx ->
                Point(
                    x = k0[idx].x + (k1[idx].x - k0[idx].x) * t,
                    y = k0[idx].y + (k1[idx].y - k0[idx].y) * t,
                )
            }
        }
    }
    return KEYFRAMES.last()
}

fun vesselName(cupSizeMl: Int): String = when {
    cupSizeMl <= 175 -> "Espresso cup"
    cupSizeMl <= 425 -> "Tumbler"
    cupSizeMl <= 650 -> "Tall glass"
    cupSizeMl <= 875 -> "Bottle"
    else -> "Jug"
}

@Composable
fun MorphingCup(
    cupSizeMl: Int,
    fillFraction: Float,
    modifier: Modifier = Modifier,
    wavePhase: Float = 0f,
    isDragging: Boolean = false,
) {
    val p = LocalPalette.current
    val points = remember(cupSizeMl) { interpolatePoints(cupSizeMl) }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val cupPath = Path()
        if (points.isNotEmpty()) {
            cupPath.moveTo(points[0].x * w, points[0].y * h)
            for (i in 1 until points.size) {
                cupPath.lineTo(points[i].x * w, points[i].y * h)
            }
            cupPath.close()
        }

        clipPath(cupPath) {
            drawPath(cupPath, color = p.fill)

            if (fillFraction > 0.001f) {
                val minY = points.minOf { it.y } * h
                val maxY = points.maxOf { it.y } * h
                val waterY = maxY - fillFraction.coerceIn(0f, 1f) * (maxY - minY)

                val wavePath = Path()
                wavePath.moveTo(0f, h + 10f)
                wavePath.lineTo(0f, waterY)

                val waveSteps = 40
                val amp = if (fillFraction > 0.02f && fillFraction < 0.98f) 5.dp.toPx() else 0f
                for (s in 0..waveSteps) {
                    val px = s * (w / waveSteps)
                    val py = waterY + amp * kotlin.math.sin(2.0 * Math.PI * (px / w) + wavePhase).toFloat()
                    wavePath.lineTo(px, py)
                }
                wavePath.lineTo(w, h + 10f)
                wavePath.close()

                drawPath(wavePath, color = p.accent.copy(alpha = 0.65f))

                val surfacePath = Path()
                for (s in 0..waveSteps) {
                    val px = s * (w / waveSteps)
                    val py = waterY + amp * kotlin.math.sin(2.0 * Math.PI * (px / w) + wavePhase).toFloat()
                    if (s == 0) surfacePath.moveTo(px, py) else surfacePath.lineTo(px, py)
                }
                drawPath(surfacePath, color = p.accent, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        drawPath(
            cupPath,
            color = if (isDragging) p.accent else p.fg2,
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
fun CupSizeSlider(
    valueMl: Int,
    onValueChange: (Int) -> Unit,
    minMl: Int = 100,
    maxMl: Int = 1000,
    stepMl: Int = 50,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt(vesselName(valueMl), Type.headline)
            Txt(WaterLog.formatVolume(valueMl), Type.body.tabular(), color = p.accent)
        }
        Spacer(Modifier.height(Space.xs))
        StepSlider(
            stop = (valueMl - minMl) / stepMl,
            count = (maxMl - minMl) / stepMl,
            onStop = { onValueChange(minMl + it * stepMl) },
            description = "Cup size slider",
        )
    }
}

/**
 * A slider of [count] + 1 stops. The fill runs from the start, or from stop [from] with a tick there for a value that
 * goes both ways. A press on the track jumps there; a drag that starts on the thumb keeps its grab offset.
 */
@Composable
fun StepSlider(
    stop: Int,
    count: Int,
    onStop: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    from: Int? = null,
) {
    val p = LocalPalette.current
    val frac = (stop.toFloat() / count.coerceAtLeast(1)).coerceIn(0f, 1f)
    val current by rememberUpdatedState(stop)
    val change by rememberUpdatedState(onStop)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(stop.toFloat(), 0f..count.toFloat(), count - 1)
                setProgress { change(it.roundToInt().coerceIn(0, count)); true }
            }
            .pointerInput(count) {
                val thumb = 28.dp.toPx()
                val available = (size.width - thumb).coerceAtLeast(1f)
                fun stopAt(center: Float) = ((center - thumb / 2) / available * count).roundToInt().coerceIn(0, count)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val thumbCenter = thumb / 2 + available * current / count.coerceAtLeast(1)
                    val grab = if (kotlin.math.abs(down.position.x - thumbCenter) <= thumb) thumbCenter - down.position.x else 0f
                    change(stopAt(down.position.x + grab))
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() } ?: return@awaitEachGesture
                    change(stopAt(slop.position.x + grab))
                    horizontalDrag(slop.id) {
                        change(stopAt(it.position.x + grab))
                        it.consume()
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val track = maxWidth - 28.dp
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(p.fillPressed))
        if (from == null) {
            Box(Modifier.width(14.dp + track * frac).height(4.dp).clip(CircleShape).background(p.accent))
        } else {
            val origin = (from.toFloat() / count.coerceAtLeast(1)).coerceIn(0f, 1f)
            Box(Modifier.padding(start = 14.dp + track * origin - 1.dp).size(2.dp, 12.dp).clip(CircleShape).background(p.fg3))
            Box(
                Modifier.padding(start = 14.dp + track * minOf(frac, origin)).width(track * kotlin.math.abs(frac - origin))
                    .height(4.dp).clip(CircleShape).background(p.accent),
            )
        }
        SliderThumb(Modifier.padding(start = track * frac))
    }
}

@Composable
fun InteractiveCup(
    cupSizeMl: Int,
    onLogWater: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pour: Pour? = null,
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var fillFraction by remember { mutableFloatStateOf(0f) }
    var lastTickStep by remember { mutableIntStateOf(0) }
    // After release the water settles out of the cup instead of vanishing: drains on a log, sinks back on a cancel.
    val settle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shownFill = if (isDragging) fillFraction else settle.value
    // The wave only runs while there is water to move, so an idle cup costs no frames.
    val animWavePhase = if (shownFill > 0f) {
        val infiniteTransition = rememberInfiniteTransition(label = "wave")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "phase",
        ).value
    } else 0f
    // The whole cup, from a tap on it or on its pill: it fills and drains as the amount logs.
    fun logFull() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onLogWater(cupSizeMl)
        scope.launch {
            settle.snapTo(1f)
            settle.animateTo(0f, tween(420, easing = EaseOutQuart))
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (isDragging && fillFraction > 0.05f) {
                val currentMl = (fillFraction * cupSizeMl).roundToInt()
                Txt(
                    "+ ${WaterLog.formatVolume(currentMl)}",
                    Type.headline.tabular(),
                    color = LocalPalette.current.accent,
                )
            }
        }

        Spacer(Modifier.height(Space.xs))

        Box(
            modifier = Modifier
                .size(240.dp)
                // Loaded: the logged water leaves from the cup's base.
                .then(if (pour == null) Modifier else Modifier.onGloballyPositioned { pour.from = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height * 0.9f) })
                .semantics {
                    contentDescription = "Water cup"
                    customActions = listOf(CustomAccessibilityAction("Log $cupSizeMl ml") { onLogWater(cupSizeMl); true })
                }
                .pointerInput(cupSizeMl) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val h = size.height.toFloat()
                        val bottomY = h * 0.92f
                        val topY = h * 0.15f
                        val range = (bottomY - topY).coerceAtLeast(1f)

                        fun updateFraction(touchY: Float) {
                            val frac = ((bottomY - touchY) / range).coerceIn(0f, 1f)
                            fillFraction = frac
                            val currentMl = (frac * cupSizeMl).roundToInt()
                            val tick = currentMl / 50
                            if (tick != lastTickStep) {
                                lastTickStep = tick
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }

                        // A tap logs the whole cup; only a drag past the touch slop pours a partial amount.
                        val slop = awaitVerticalTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                        if (slop == null) {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !up.isConsumed) logFull()
                            return@awaitEachGesture
                        }
                        scope.launch { settle.snapTo(0f) }
                        isDragging = true
                        updateFraction(slop.position.y)
                        verticalDrag(slop.id) {
                            updateFraction(it.position.y)
                            it.consume()
                        }

                        val logged = fillFraction >= 0.10f
                        if (logged) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLogWater((fillFraction * cupSizeMl).roundToInt())
                        }
                        val from = fillFraction
                        isDragging = false
                        fillFraction = 0f
                        lastTickStep = 0
                        scope.launch {
                            settle.snapTo(from)
                            settle.animateTo(0f, tween(if (logged) 420 else 220, easing = EaseOutQuart))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            MorphingCup(
                cupSizeMl = cupSizeMl,
                fillFraction = shownFill,
                modifier = Modifier.fillMaxSize(),
                wavePhase = animWavePhase,
                isDragging = isDragging,
            )
        }
        Spacer(Modifier.height(Space.md))
        PillButton("Log ${WaterLog.formatVolume(cupSizeMl)}", ::logFull, primary = true)
    }
}

@Composable
fun WaterDailyRing(
    totalMl: Int,
    goalMl: Int,
    deficitMl: Int? = 0,
    dayLabel: String = "Today",
    modifier: Modifier = Modifier,
    pour: Pour? = null,
) {
    val p = LocalPalette.current
    val target = if (goalMl > 0) (totalMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f
    val eased by animateFloatAsState(target, tween(300, easing = EaseOutQuart), label = "ring")
    // Loaded: the arc waits for the poured water to arrive, then surges to its new length just past it; reaching the goal rings out.
    val surge = remember { Animatable(target) }
    if (pour != null) LaunchedEffect(target) {
        if (pour.flow.isRunning) delay(POUR_LAND_MS)
        val reached = target >= 1f && surge.value < 1f
        if (reached) launch { pour.goal.snapTo(0f); pour.goal.animateTo(1f, tween(700, easing = EaseOutQuart)) }
        surge.animateTo(target, landSpring())
    }
    val frac = if (pour != null) surge.value else eased
    val pct = if (goalMl > 0) (totalMl * 100 / goalMl) else 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(p.card)
            .padding(Space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(68.dp).then(if (pour == null) Modifier else Modifier.pourInto(pour, p.accent)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val strokeW = 6.dp.toPx()
                val inset = strokeW / 2
                val arcSize = Size(size.width - strokeW, size.height - strokeW)
                val tl = Offset(inset, inset)
                drawArc(p.separator, 0f, 360f, false, tl, arcSize, style = Stroke(strokeW))
                drawArc(p.accent, -90f, 360f * frac, false, tl, arcSize, style = Stroke(strokeW, cap = StrokeCap.Round))
            }
            Txt("$pct%", Type.headline.tabular())
        }

        Spacer(Modifier.width(Space.lg))

        Column(Modifier.weight(1f)) {
            Txt(dayLabel, Type.footnote, color = p.fg2)
            Txt(
                "${totalMl} / ${goalMl} ml",
                Type.title2.tabular(),
            )
            // Pace is a status, not a control: fg2, red only when far behind. Null [deficitMl] means no pace to show; a full ring says the goal is reached.
            val paceText = when {
                totalMl >= goalMl -> null
                deficitMl == null -> null
                deficitMl > 0 -> "$deficitMl ml behind pace"
                else -> "On pace"
            }
            paceText?.let { Txt(it, Type.footnote, color = if (totalMl < goalMl && (deficitMl ?: 0) > 500) p.red else p.fg2) }
        }
    }
}

/**
 * Main Water tracking screen.
 */
@Composable
fun WaterScreen(
    state: AppState,
    day: LocalDate = LocalDate.now(),
    goalMl: Int = WaterLog.DEFAULT_GOAL_ML,
    cupSizeMl: Int = WaterLog.DEFAULT_CUP_ML,
    onBack: (() -> Unit)? = null,
    onLogWater: (Int) -> Unit = {},
    onClearWater: (String) -> Unit = {},
    onCupSizeChange: ((Int) -> Unit)? = null,
    pace: Boolean = true,
) {
    val p = LocalPalette.current
    var currentCupSize by rememberSaveable { mutableIntStateOf(cupSizeMl) }
    val todayEntries = remember(state.water, day) { WaterLog.entriesForDay(state, day) }
    val totalToday = remember(todayEntries) { todayEntries.sumOf { it.ml } }
    // Pace follows the pace reminder: with it off, or on a past day, there is no pace to keep.
    val deficit = remember(totalToday, goalMl, day, pace) {
        if (!pace || day != LocalDate.now()) null
        else WaterLog.intakeDeficitMl(totalToday, LocalTime.now(), goalMl, WaterLog.predictedWakeTime(day))
    }

    ScreenFrame {
        Spacer(Modifier.height(52.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("Water", Type.largeTitle)
            if (onBack != null) {
                SmallPill("Done", onBack)
            }
        }

        Spacer(Modifier.height(Space.lg))

        // 1. Top slider for cup size (100 to 1000 ml, snap to 50 ml)
        CupSizeSlider(
            valueMl = currentCupSize,
            onValueChange = {
                currentCupSize = it
                onCupSizeChange?.invoke(it)
            },
        )

        Spacer(Modifier.height(Space.md))

        // 2 & 3. Interactive morphing cup with drag-to-fill
        val pour = if (loaded(Effect.WaterPour)) remember { Pour() } else null
        val scope = rememberCoroutineScope()
        InteractiveCup(
            cupSizeMl = currentCupSize,
            onLogWater = { ml ->
                pour?.let { scope.launch { it.play() } }
                onLogWater(ml)
            },
            pour = pour,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.lg))

        // 4. Daily ring against goal
        WaterDailyRing(
            totalMl = totalToday,
            goalMl = goalMl,
            deficitMl = deficit,
            dayLabel = Fmt.day(day, LocalDate.now()),
            pour = pour,
        )

        Spacer(Modifier.height(Space.lg))

        // 5. Entries list with undo
        if (todayEntries.isNotEmpty()) {
            GroupHeader("Log (${todayEntries.size})")
            Group {
                todayEntries.asReversed().forEachIndexed { idx, entry ->
                    val atText = try {
                        Fmt.time(OffsetDateTime.parse(entry.at, LogCodec.TS))
                    } catch (_: Exception) {
                        entry.at
                    }
                    GroupRow(first = idx == 0) {
                        Txt(atText, Type.body.tabular(), Modifier.weight(1f))
                        Txt(WaterLog.formatVolume(entry.ml), Type.body.tabular())
                    }
                }
                // An action row, as in iOS lists, so every amount keeps one right edge.
                val newest = todayEntries.last()
                GroupRow(first = false, onClick = { onClearWater(newest.id) }) { Txt("Undo ${newest.ml} ml", Type.body, color = p.accent) }
            }
        }
    }
}

/** Loaded's pour: water logged at the cup's base ([from]) arcs down into the day's ring ([to]), both in the root. */
class Pour {
    var from = Offset.Zero
    var to = Offset.Zero
    internal val flow = Animatable(1f)
    internal val goal = Animatable(1f)

    suspend fun play() {
        flow.snapTo(0f)
        flow.animateTo(1f, tween(POUR_MS, easing = LinearEasing))
    }
}

private const val POUR_MS = 620
/** When the head of the stream reaches the ring. */
private const val POUR_LAND_MS = 330L
private const val POUR_DROPS = 9

/** The stream into this ring while [pour] runs, drawn over everything above it on the way; and the ring a reached goal sends out. */
private fun Modifier.pourInto(pour: Pour, color: Color) = onGloballyPositioned { pour.to = it.positionInRoot() + Offset(it.size.width / 2f, it.size.height / 2f) }
    .drawWithContent {
        drawContent()
        val c = Offset(size.width / 2, size.height / 2)
        val g = pour.goal.value
        if (g < 1f) drawCircle(color.copy(alpha = 0.4f * (1f - g)), size.width / 2 + 60.dp.toPx() * g, c, style = Stroke(2.dp.toPx()))
        val f = pour.flow.value
        if (f >= 1f) return@drawWithContent
        // From the cup's base, falling in a curve that bends over to land on the ring from above.
        val a = pour.from - pour.to + c
        val ctrl = Offset(a.x, c.y - size.height)
        for (i in 0 until POUR_DROPS) {
            val t = (f * 1.9f - i * 0.1f).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) continue
            val u = t * t
            val pt = a * ((1 - u) * (1 - u)) + ctrl * (2 * u * (1 - u)) + c * (u * u)
            drawCircle(color.copy(alpha = 0.9f), (4.5f - i * 0.3f).dp.toPx(), pt)
        }
    }

