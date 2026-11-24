package dev.gzclp.tracker.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

/**
 * The daily "Log your weight" notification, migrated onto [HabitAlarm].
 * Kept for backward compatibility with existing callers and the ACTION_LOG intent.
 */
object WeighIn {
    const val ACTION_LOG = "dev.gzclp.tracker.LOG_WEIGHT"

    /** The first reminder time strictly after [now]. */
    fun next(now: ZonedDateTime, minutes: Int): ZonedDateTime = HabitAlarm.next(now, minutes)

    fun schedule(context: Context) = HabitAlarm.schedule(context, HabitKind.WEIGH_IN)

    internal fun fire(context: Context) = HabitAlarm.fire(context, HabitKind.WEIGH_IN)

    /** Clears the reminder once today's weigh-in is in. */
    fun dismiss(context: Context) = HabitAlarm.dismiss(context, HabitKind.WEIGH_IN)
}

class WeighInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        HabitAlarm.onReceive(context, intent)
    }
}
