package dev.gzclp.tracker

import android.animation.ValueAnimator
import android.view.animation.PathInterpolator
import android.window.SplashScreenView

/** How the launch screen hands over to the first frame, which is already drawn under it. */
enum class SplashExit(val iconScale: Float, val millis: Long) {
    /** The ε grows a little as the screen fades to the app. */
    Fade(1.08f, 250),
    /** The ε expands into the screen as it fades. */
    Zoom(5f, 350),
}

/** Fades this launch screen out to the app the way [exit] says, or drops it at once when the system has animations off. */
fun SplashScreenView.leave(exit: SplashExit) {
    if (!ValueAnimator.areAnimatorsEnabled()) return remove()
    val ease = PathInterpolator(0.32f, 0.72f, 0f, 1f)
    iconView?.animate()?.scaleX(exit.iconScale)?.scaleY(exit.iconScale)?.setDuration(exit.millis)?.setInterpolator(ease)
    animate().alpha(0f).setDuration(exit.millis).setInterpolator(ease).withEndAction { remove() }
}
