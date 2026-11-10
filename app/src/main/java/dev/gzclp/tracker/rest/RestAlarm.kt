package dev.gzclp.tracker.rest

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import dev.gzclp.tracker.MainActivity
import dev.gzclp.tracker.Prefs
import dev.gzclp.tracker.R

/**
 * Rest timer that fires with the screen off: an exact, idle-allowed alarm wakes [RestAlarmReceiver],
 * which vibrates and posts a notification. A countdown notification shows while resting.
 */
object RestAlarm {
    private const val CHANNEL_DONE = "rest_done"
    private const val CHANNEL_COUNT = "rest_countdown"
    private const val ID = 1
    private val pattern = longArrayOf(0, 450, 200, 450, 200, 450)

    fun start(context: Context, seconds: Int, label: String) {
        val prefs = Prefs(context)
        val end = System.currentTimeMillis() + seconds * 1000L
        prefs.restEndMillis = end
        prefs.restTotalSec = seconds
        prefs.restLabel = label
        val am = context.getSystemService(AlarmManager::class.java)
        val trigger = SystemClock.elapsedRealtime() + seconds * 1000L
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, alarmIntent(context))
        } else {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, alarmIntent(context))
        }
        channels(context)
        notify(context, countdown(context, end, label))
    }

    fun cancel(context: Context) {
        Prefs(context).restEndMillis = 0
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    internal fun fire(context: Context) {
        val prefs = Prefs(context)
        if (prefs.restEndMillis == 0L) return
        prefs.restEndMillis = 0
        channels(context)
        @Suppress("DEPRECATION")
        context.getSystemService(VibratorManager::class.java).defaultVibrator.vibrate(
            VibrationEffect.createWaveform(pattern, -1),
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
        )
        notify(
            context,
            Notification.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_rest)
                .setContentTitle("Rest over")
                .setContentText("Time for the next set")
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun countdown(context: Context, end: Long, label: String) =
        Notification.Builder(context, CHANNEL_COUNT)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("Resting")
            .setContentText(label)
            .setWhen(end)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(context))
            .build()

    private fun notify(context: Context, n: Notification) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.areNotificationsEnabled()) nm.notify(ID, n)
    }

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Rest over", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = pattern
                setSound(null, null)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_COUNT, "Rest countdown", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, RestAlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestAlarm.fire(context)
}
