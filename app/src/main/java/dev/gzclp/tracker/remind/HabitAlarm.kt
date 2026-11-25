package dev.gzclp.tracker.remind

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.gzclp.tracker.AppModel
import dev.gzclp.tracker.MainActivity
import dev.gzclp.tracker.Prefs
import dev.gzclp.tracker.R
import dev.gzclp.tracker.cardio.CardioNotice
import dev.gzclp.tracker.food.FoodLog
import dev.gzclp.tracker.log.AppState
import dev.gzclp.tracker.log.WaterLog
import dev.gzclp.tracker.log.hasWeighIn
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * Generalized habit reminders for WEIGH_IN, MEAL, STRETCH, and WATER.
 * Replaces and generalizes the single weigh-in alarm.
 */
object HabitAlarm {
    const val WINDOW_MS = 10 * 60 * 1000L
    private const val WATER_CHECK_MIN = 30L

    /**
     * The first of [times] (learned or fixed, per logical day) plus [delayMin] that falls after [now].
     * A time before 4 a.m. belongs to the logical day before it, so it lands on the next calendar date.
     */
    fun nextTime(now: ZonedDateTime, delayMin: Long = 0, times: (LocalDate) -> List<LocalTime>): ZonedDateTime? {
        val today = logicalDateOf(now.toLocalDateTime())
        return (0L..2L).asSequence().map(today::plusDays).flatMap { d ->
            times(d).map { t -> now.with(d.plusDays(if (t.hour < DAY_START_HOUR) 1 else 0)).with(t).plusMinutes(delayMin) }.sorted()
        }.firstOrNull { it.isAfter(now) }
    }

    /** Takes [state] rather than reaching for [AppModel], which schedules these while it is still being built. */
    fun rebuild(context: Context, state: AppState): HabitModel =
        updateHabitCache(state, FoodLog(File(context.filesDir, "food")).meals())

    /**
     * Determines the next firing time for [kind].
     * Falls back to fixed time if fewer than 3 events of that kind have occurred.
     */
    private fun nextAlarmTime(context: Context, kind: HabitKind, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val prefs = Prefs(context)
        return when (kind) {
            HabitKind.WEIGH_IN -> if (prefs.weighInReminder) learnedOrFixed(now, kind, prefs.weighInMinutes) else null
            // A meal reminder only helps before eating: 20 minutes after the usual time, if no photo came in.
            HabitKind.MEAL -> if (prefs.mealReminder) nextTime(now, 20) { predict(kind, it) } else null
            HabitKind.STRETCH -> if (prefs.stretchReminder) learnedOrFixed(now, kind, prefs.stretchReminderMinutes) else null
            // The pace check runs every [WATER_CHECK_MIN] through the waking window; [WaterLog.shouldRemind] decides whether to post.
            HabitKind.WATER -> {
                if (!prefs.waterReminder) return null
                val wake = now.with(WaterLog.predictedWakeTime(now.toLocalDate()))
                val check = now.plusMinutes(WATER_CHECK_MIN)
                when {
                    now.isBefore(wake) -> wake.plusMinutes(WATER_CHECK_MIN)
                    check.isBefore(wake.plusHours(WaterLog.ACTIVE_HOURS.toLong())) -> check
                    else -> now.plusDays(1).with(WaterLog.predictedWakeTime(now.toLocalDate().plusDays(1))).plusMinutes(WATER_CHECK_MIN)
                }
            }
        }
    }

    /** The learned time once [kind] has 3 events, the fixed [minutes] until then. */
    private fun learnedOrFixed(now: ZonedDateTime, kind: HabitKind, minutes: Int): ZonedDateTime? {
        val fixed = listOf(LocalTime.of(minutes / 60, minutes % 60))
        return nextTime(now) { d -> if (cachedHabits.count(kind) < 3) fixed else predict(kind, d).ifEmpty { fixed } }
    }

    fun schedule(context: Context, kind: HabitKind, state: AppState = AppModel.get(context).store.state.value, rebuilt: Boolean = false) {
        if (!rebuilt) rebuild(context, state)
        val am = context.getSystemService(AlarmManager::class.java)
        val at = nextAlarmTime(context, kind)
        if (at == null) return am.cancel(alarmIntent(context, kind))
        am.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), WINDOW_MS, alarmIntent(context, kind))
    }

    fun scheduleAll(context: Context, state: AppState = AppModel.get(context).store.state.value) {
        rebuild(context, state)
        HabitKind.entries.forEach { schedule(context, it, state, rebuilt = true) }
    }

    internal fun fire(context: Context, kind: HabitKind) {
        val prefs = Prefs(context)
        schedule(context, kind) // Arm next alarm for this kind

        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return

        when (kind) {
            HabitKind.WEIGH_IN -> {
                if (!prefs.weighInReminder || AppModel.get(context).store.state.value.hasWeighIn(LocalDate.now())) return
                postNotification(
                    context, kind,
                    title = "Log your weight",
                    text = "Weigh in before breakfast for a steady number.",
                    contentIntent = logIntent(context),
                )
            }
            HabitKind.MEAL -> {
                if (!prefs.mealReminder) return
                val meals = FoodLog(File(context.filesDir, "food")).meals()
                val now = OffsetDateTime.now()
                val slotEstimate = now.minusMinutes(20)
                val hasPhoto = meals.any { Duration.between(it.at, slotEstimate).abs().toMinutes() <= 90 }
                if (hasPhoto) return
                val meal = when (now.hour) {
                    in 4..10 -> "Breakfast"
                    in 11..15 -> "Lunch"
                    else -> "Dinner"
                }
                postNotification(
                    context, kind,
                    title = "Meal reminder",
                    text = "$meal? Snap it before you eat",
                    contentIntent = mainIntent(context),
                )
            }
            HabitKind.STRETCH -> {
                if (!prefs.stretchReminder || AppModel.get(context).store.state.value.hasStretch(LocalDate.now())) return
                postNotification(
                    context, kind,
                    title = "Time to stretch",
                    text = "Keep your mobility work consistent.",
                    contentIntent = mainIntent(context),
                )
            }
            HabitKind.WATER -> {
                if (!prefs.waterReminder) return
                val now = OffsetDateTime.now()
                val last = prefs.waterRemindedMillis.takeIf { it > 0 }?.let { OffsetDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), now.offset) }
                val state = AppModel.get(context).store.state.value
                rebuild(context, state)
                if (!WaterLog.shouldRemind(state, now, prefs.waterGoalMl, last)) return
                prefs.waterRemindedMillis = now.toInstant().toEpochMilli()
                postNotification(
                    context, kind,
                    title = "Behind on water",
                    text = "You're over 500 ml behind today's pace.",
                    contentIntent = mainIntent(context),
                )
            }
        }
    }

    fun dismiss(context: Context, kind: HabitKind) =
        context.getSystemService(NotificationManager::class.java).cancel(kind.notificationId)

    fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) {
            val kindStr = intent.getStringExtra("kind")
            val kind = if (kindStr != null) runCatching { HabitKind.valueOf(kindStr) }.getOrDefault(HabitKind.WEIGH_IN) else HabitKind.WEIGH_IN
            fire(context, kind)
        } else {
            scheduleAll(context)
            CardioNotice.sync(context, AppModel.get(context).store.state.value.cardioActive)
        }
    }

    private fun postNotification(
        context: Context,
        kind: HabitKind,
        title: String,
        text: String,
        contentIntent: PendingIntent,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val cid = kind.channelId
        nm.createNotificationChannel(NotificationChannel(cid, kind.channelName, NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(
            kind.notificationId,
            Notification.Builder(context, cid)
                .setSmallIcon(R.drawable.ic_stat_weight)
                .setContentTitle(title)
                .setContentText(text)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun alarmIntent(context: Context, kind: HabitKind) = PendingIntent.getBroadcast(
        context, kind.requestCode,
        Intent(context, WeighInReceiver::class.java).apply { putExtra("kind", kind.name) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun logIntent(context: Context) = PendingIntent.getActivity(
        context, 1,
        Intent(context, MainActivity::class.java).setAction(WeighIn.ACTION_LOG).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun mainIntent(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
