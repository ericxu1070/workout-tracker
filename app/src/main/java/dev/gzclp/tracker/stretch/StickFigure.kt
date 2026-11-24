package dev.gzclp.tracker.stretch

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.gzclp.tracker.ui.LocalPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the original stick figure on Compose Canvas with joint-driven keyframed poses.
 * Themed with the app's colors [LocalPalette.current].
 */
@Composable
fun StickFigureCanvas(
    pose: Pose,
    targetArea: BodyArea? = null,
    modifier: Modifier = Modifier,
) {
    val p = LocalPalette.current
    Canvas(modifier = modifier) {
        drawStickFigure(
            pose = pose,
            targetArea = targetArea,
            fg = p.fg,
            fgDim = p.fg2,
            accent = p.accent,
            groundColor = p.separator,
        )
    }
}

/**
 * Animated stick figure that smoothly loops between [move.startPose] and [move.endPose]
 * when previewing, or holds [move.holdPose] with a gentle breathing micro-oscillation when active.
 */
@Composable
fun AnimatedStickFigure(
    move: StretchMove,
    isHolding: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "stick_figure")

    val loopFraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loop_fraction",
    )

    val breathFraction by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath_fraction",
    )

    val activePose = if (isHolding) {
        // Holding pose with subtle breathing oscillation
        val breathOffset = breathFraction * 1.5f
        move.holdPose.copy(
            torsoAngle = move.holdPose.torsoAngle + breathOffset,
            leftArmAngle = move.holdPose.leftArmAngle + breathOffset,
            rightArmAngle = move.holdPose.rightArmAngle - breathOffset,
        )
    } else {
        // Preview looping between start pose and end pose
        Pose.interpolate(move.startPose, move.endPose, loopFraction)
    }

    StickFigureCanvas(
        pose = activePose,
        targetArea = move.targetArea,
        modifier = modifier,
    )
}

private fun DrawScope.drawStickFigure(
    pose: Pose,
    targetArea: BodyArea?,
    fg: Color,
    fgDim: Color,
    accent: Color,
    groundColor: Color,
) {
    val w = size.width
    val h = size.height
    val strokeWidth = (h * 0.022f).coerceIn(4f, 10f)
    val jointRadius = strokeWidth * 0.7f

    // Subtle ground floor reference line
    val groundY = h * 0.90f
    drawLine(
        color = groundColor,
        start = Offset(w * 0.10f, groundY),
        end = Offset(w * 0.90f, groundY),
        strokeWidth = 2f,
        cap = StrokeCap.Round,
    )

    // Compute joint coordinates
    val pelvis = Offset(pose.rootX * w, pose.rootY * h)

    val torsoRad = Math.toRadians(pose.torsoAngle.toDouble())
    val neck = Offset(
        pelvis.x + (pose.torsoLen * h * cos(torsoRad)).toFloat(),
        pelvis.y + (pose.torsoLen * h * sin(torsoRad)).toFloat(),
    )

    val headRadius = h * 0.052f
    val headAngleRad = torsoRad + Math.toRadians(pose.headTiltAngle.toDouble())
    val headCenter = Offset(
        neck.x + (headRadius * 1.35f * cos(headAngleRad)).toFloat(),
        neck.y + (headRadius * 1.35f * sin(headAngleRad)).toFloat(),
    )

    // Arms
    val lArmRad = Math.toRadians(pose.leftArmAngle.toDouble())
    val lElbow = Offset(
        neck.x + (pose.armLen * h * cos(lArmRad)).toFloat(),
        neck.y + (pose.armLen * h * sin(lArmRad)).toFloat(),
    )
    val lForeRad = Math.toRadians(pose.leftForearmAngle.toDouble())
    val lHand = Offset(
        lElbow.x + (pose.forearmLen * h * cos(lForeRad)).toFloat(),
        lElbow.y + (pose.forearmLen * h * sin(lForeRad)).toFloat(),
    )

    val rArmRad = Math.toRadians(pose.rightArmAngle.toDouble())
    val rElbow = Offset(
        neck.x + (pose.armLen * h * cos(rArmRad)).toFloat(),
        neck.y + (pose.armLen * h * sin(rArmRad)).toFloat(),
    )
    val rForeRad = Math.toRadians(pose.rightForearmAngle.toDouble())
    val rHand = Offset(
        rElbow.x + (pose.forearmLen * h * cos(rForeRad)).toFloat(),
        rElbow.y + (pose.forearmLen * h * sin(rForeRad)).toFloat(),
    )

    // Legs
    val lThighRad = Math.toRadians(pose.leftThighAngle.toDouble())
    val lKnee = Offset(
        pelvis.x + (pose.thighLen * h * cos(lThighRad)).toFloat(),
        pelvis.y + (pose.thighLen * h * sin(lThighRad)).toFloat(),
    )
    val lCalfRad = Math.toRadians(pose.leftCalfAngle.toDouble())
    val lFoot = Offset(
        lKnee.x + (pose.calfLen * h * cos(lCalfRad)).toFloat(),
        lKnee.y + (pose.calfLen * h * sin(lCalfRad)).toFloat(),
    )

    val rThighRad = Math.toRadians(pose.rightThighAngle.toDouble())
    val rKnee = Offset(
        pelvis.x + (pose.thighLen * h * cos(rThighRad)).toFloat(),
        pelvis.y + (pose.thighLen * h * sin(rThighRad)).toFloat(),
    )
    val rCalfRad = Math.toRadians(pose.rightCalfAngle.toDouble())
    val rFoot = Offset(
        rKnee.x + (pose.calfLen * h * cos(rCalfRad)).toFloat(),
        rKnee.y + (pose.calfLen * h * sin(rCalfRad)).toFloat(),
    )

    // Target highlight styling
    val torsoColor = if (targetArea in listOf(BodyArea.SPINE, BodyArea.UPPER_BACK, BodyArea.CHEST, BodyArea.LATS)) accent else fg
    val hipColor = if (targetArea == BodyArea.HIPS) accent else fg
    val quadColor = if (targetArea == BodyArea.QUADS) accent else fg
    val hamstringColor = if (targetArea == BodyArea.HAMSTRINGS) accent else fg
    val calfColor = if (targetArea == BodyArea.CALVES) accent else fg
    val ankleColor = if (targetArea == BodyArea.ANKLES) accent else fg
    val shoulderColor = if (targetArea in listOf(BodyArea.SHOULDERS, BodyArea.LATS, BodyArea.UPPER_BACK)) accent else fg

    // 1. Far arm (right arm, slightly dimmer)
    drawLine(fgDim, neck, rElbow, strokeWidth, StrokeCap.Round)
    drawLine(fgDim, rElbow, rHand, strokeWidth, StrokeCap.Round)
    drawCircle(fgDim, jointRadius, rElbow)

    // 2. Far leg (right leg)
    val legRColor = if (targetArea == BodyArea.QUADS || targetArea == BodyArea.HAMSTRINGS) accent.copy(alpha = 0.7f) else fgDim
    drawLine(legRColor, pelvis, rKnee, strokeWidth, StrokeCap.Round)
    drawLine(if (targetArea == BodyArea.CALVES) accent.copy(alpha = 0.7f) else fgDim, rKnee, rFoot, strokeWidth, StrokeCap.Round)
    drawCircle(fgDim, jointRadius, rKnee)

    // 3. Near leg (left leg)
    val legLColor = when (targetArea) {
        BodyArea.QUADS -> quadColor
        BodyArea.HAMSTRINGS -> hamstringColor
        else -> fg
    }
    drawLine(legLColor, pelvis, lKnee, strokeWidth, StrokeCap.Round)
    drawLine(if (targetArea == BodyArea.CALVES) calfColor else fg, lKnee, lFoot, strokeWidth, StrokeCap.Round)
    drawCircle(fg, jointRadius, lKnee)
    if (targetArea == BodyArea.ANKLES) {
        drawCircle(ankleColor, jointRadius * 1.8f, lFoot)
    }

    // 4. Torso & Pelvis
    drawLine(torsoColor, pelvis, neck, strokeWidth * 1.15f, StrokeCap.Round)
    drawCircle(hipColor, jointRadius * (if (targetArea == BodyArea.HIPS) 1.8f else 1.2f), pelvis)

    // 5. Near arm (left arm)
    val armLColor = if (targetArea == BodyArea.SHOULDERS) shoulderColor else fg
    drawLine(armLColor, neck, lElbow, strokeWidth, StrokeCap.Round)
    drawLine(fg, lElbow, lHand, strokeWidth, StrokeCap.Round)
    drawCircle(fg, jointRadius, lElbow)
    drawCircle(shoulderColor, jointRadius * (if (targetArea == BodyArea.SHOULDERS) 1.8f else 1.1f), neck)

    // 6. Head
    drawCircle(
        color = fg,
        radius = headRadius,
        center = headCenter,
        style = Stroke(width = strokeWidth * 0.9f),
    )
    // Small target area accent halo if relevant
    if (targetArea == BodyArea.HIPS) {
        drawCircle(accent.copy(alpha = 0.25f), radius = headRadius * 1.6f, center = pelvis)
    } else if (targetArea == BodyArea.SPINE) {
        drawCircle(accent.copy(alpha = 0.25f), radius = headRadius * 1.4f, center = Offset((pelvis.x + neck.x) / 2f, (pelvis.y + neck.y) / 2f))
    }
}
