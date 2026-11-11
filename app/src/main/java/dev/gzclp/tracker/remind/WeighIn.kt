package dev.gzclp.tracker.remind

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.gzclp.tracker.AppModel
import dev.gzclp.tracker.MainActivity
import dev.gzclp.tracker.Prefs
import dev.gzclp.tracker.R
import dev.gzclp.tracker.cardio.CardioNotice
import dev.gzclp.tracker.log.hasWeighIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The daily "Log your weight" notification. One inexact alarm is armed for the next reminder time;
 * when it fires it posts (unless today already has a weigh-in) and arms the next one.
 * Boot, app update and clock changes re-arm it through [WeighInReceiver].
 */
object WeighIn {
    const val ACTION_LOG = "dev.gzclp.tracker.LOG_WEIGHT"
    private const val CHANNEL = "weigh_in"
    private const val ID = 2
    private const val WINDOW_MS = 10 * 60 * 1000L

    /** The first reminder time strictly after [now]. */
    fun next(now: ZonedDateTime, minutes: Int): ZonedDateTime {
        val today = now.with(LocalTime.of(minutes / 60, minutes % 60))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun schedule(context: Context) {
        val prefs = Prefs(context)
        val am = context.getSystemService(AlarmManager::class.java)
        if (!prefs.weighInReminder) return am.cancel(alarmIntent(context))
        val at = next(ZonedDateTime.now(), prefs.weighInMinutes).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, alarmIntent(context))
    }

    internal fun fire(context: Context) {
        val prefs = Prefs(context)
        schedule(context)
        if (!prefs.weighInReminder || AppModel.get(context).store.state.value.hasWeighIn(LocalDate.now())) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Weigh-in reminder", NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(
            ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_weight)
                .setContentTitle("Log your weight")
                .setContentText("Weigh in before breakfast for a steady number.")
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(logIntent(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    /** Clears the reminder once today's weigh-in is in. */
    fun dismiss(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID)

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 1, Intent(context, WeighInReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun logIntent(context: Context) = PendingIntent.getActivity(
        context, 1, Intent(context, MainActivity::class.java).setAction(ACTION_LOG).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}

class WeighInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) return WeighIn.fire(context)
        WeighIn.schedule(context)
        // Boot clears notifications; a cardio session still running in the log gets its notification back.
        CardioNotice.sync(context, AppModel.get(context).store.state.value.cardioActive)
    }
}
