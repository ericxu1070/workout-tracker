package dev.gzclp.tracker.cardio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import dev.gzclp.tracker.MainActivity
import dev.gzclp.tracker.R
import dev.gzclp.tracker.log.CardioRun
import dev.gzclp.tracker.ui.Fmt

/**
 * The ongoing notification for the cardio session in progress: a system chronometer counts up from the logged start,
 * so nothing has to run in the background. Stop opens the app on the stop sheet.
 */
object CardioNotice {
    const val ACTION_STOP = "dev.gzclp.tracker.STOP_CARDIO"
    private const val CHANNEL = "cardio"
    private const val ID = 3

    /** Shows [run], or clears the notification when none is running. */
    fun sync(context: Context, run: CardioRun?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (run == null) return nm.cancel(ID)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Cardio in progress", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
        val stop = activity(context, 4, ACTION_STOP)
        nm.notify(
            ID,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_rest)
                .setContentTitle(run.name)
                .setContentText("Started ${Fmt.time(run.startedAt)}")
                .setWhen(run.startedAt.toInstant().toEpochMilli())
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_WORKOUT)
                .setContentIntent(activity(context, 3, null))
                .addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_stat_rest), "Stop", stop).build())
                .build(),
        )
    }

    private fun activity(context: Context, code: Int, action: String?) = PendingIntent.getActivity(
        context, code, Intent(context, MainActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
