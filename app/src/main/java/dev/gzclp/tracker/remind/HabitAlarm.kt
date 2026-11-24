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

    fun requestCode(kind: HabitKind): Int = when (kind) {
        HabitKind.WEIGH_IN -> 1
        HabitKind.MEAL -> 2
        HabitKind.STRETCH -> 3
        HabitKind.WATER -> 4
    }

    fun notificationId(kind: HabitKind): Int = when (kind) {
        HabitKind.WEIGH_IN -> 2 // matches historical WeighIn.ID
        HabitKind.MEAL -> 3
        HabitKind.STRETCH -> 4
        HabitKind.WATER -> 5
    }

    fun channelId(kind: HabitKind): String = when (kind) {
        HabitKind.WEIGH_IN -> "weigh_in"
        HabitKind.MEAL -> "meal"
        HabitKind.STRETCH -> "stretch"
        HabitKind.WATER -> "water"
    }

    fun channelName(kind: HabitKind): String = when (kind) {
        HabitKind.WEIGH_IN -> "Weigh-in reminder"
        HabitKind.MEAL -> "Meal reminder"
        HabitKind.STRETCH -> "Stretch reminder"
        HabitKind.WATER -> "Water reminder"
    }

    /** The first reminder time strictly after [now] using fixed [minutes]. */
    fun next(now: ZonedDateTime, minutes: Int): ZonedDateTime {
        val today = now.with(LocalTime.of(minutes / 60, minutes % 60))
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun rebuild(context: Context): HabitModel {
        return runCatching {
            val appModel = AppModel.get(context)
            val state = appModel.store.state.value
            val meals = FoodLog(File(context.filesDir, "food")).meals()
            updateHabitCache(state, meals)
        }.getOrDefault(cachedHabits)
    }

    /**
     * Determines the next firing time for [kind].
     * Falls back to fixed time if fewer than 3 events of that kind have occurred.
     */
    fun nextAlarmTime(context: Context, kind: HabitKind, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        rebuild(context)
        val prefs = Prefs(context)
        return when (kind) {
            HabitKind.WEIGH_IN -> {
                if (!prefs.weighInReminder) return null
                if (cachedHabits.count(HabitKind.WEIGH_IN) < 3) {
                    next(now, prefs.weighInMinutes)
                } else {
                    val predToday = predict(HabitKind.WEIGH_IN, now.toLocalDate()).firstOrNull()
                        ?: LocalTime.of(prefs.weighInMinutes / 60, prefs.weighInMinutes % 60)
                    val todayAt = now.with(predToday)
                    if (todayAt.isAfter(now)) todayAt else {
                        val tomorrow = now.toLocalDate().plusDays(1)
                        val predTomorrow = predict(HabitKind.WEIGH_IN, tomorrow).firstOrNull()
                            ?: LocalTime.of(prefs.weighInMinutes / 60, prefs.weighInMinutes % 60)
                        now.plusDays(1).with(predTomorrow)
                    }
                }
            }
            HabitKind.MEAL -> {
                if (!prefs.mealReminder) return null
                val todaySlots = predict(HabitKind.MEAL, now.toLocalDate())
                val todayCandidates = todaySlots.map { now.with(it).plusMinutes(20) }.filter { it.isAfter(now) }
                if (todayCandidates.isNotEmpty()) {
                    todayCandidates.minOrNull()
                } else {
                    val tomorrow = now.toLocalDate().plusDays(1)
                    val tomorrowSlots = predict(HabitKind.MEAL, tomorrow)
                    tomorrowSlots.map { now.plusDays(1).with(it).plusMinutes(20) }.minOrNull()
                }
            }
            HabitKind.STRETCH -> {
                if (!prefs.stretchReminder) return null
                if (cachedHabits.count(HabitKind.STRETCH) < 3) {
                    next(now, prefs.stretchReminderMinutes)
                } else {
                    val predToday = predict(HabitKind.STRETCH, now.toLocalDate()).firstOrNull()
                        ?: LocalTime.of(prefs.stretchReminderMinutes / 60, prefs.stretchReminderMinutes % 60)
                    val todayAt = now.with(predToday)
                    if (todayAt.isAfter(now)) todayAt else {
                        val tomorrow = now.toLocalDate().plusDays(1)
                        val predTomorrow = predict(HabitKind.STRETCH, tomorrow).firstOrNull()
                            ?: LocalTime.of(prefs.stretchReminderMinutes / 60, prefs.stretchReminderMinutes % 60)
                        now.plusDays(1).with(predTomorrow)
                    }
                }
            }
            HabitKind.WATER -> {
                if (!prefs.waterReminder) return null
                val fallbackMinutes = 14 * 60 // 2:00 PM
                if (cachedHabits.count(HabitKind.WATER) < 3) {
                    next(now, fallbackMinutes)
                } else {
                    val predToday = predict(HabitKind.WATER, now.toLocalDate()).firstOrNull()
                        ?: LocalTime.of(14, 0)
                    val todayAt = now.with(predToday)
                    if (todayAt.isAfter(now)) todayAt else {
                        val tomorrow = now.toLocalDate().plusDays(1)
                        val predTomorrow = predict(HabitKind.WATER, tomorrow).firstOrNull()
                            ?: LocalTime.of(14, 0)
                        now.plusDays(1).with(predTomorrow)
                    }
                }
            }
        }
    }

    fun schedule(context: Context, kind: HabitKind) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = nextAlarmTime(context, kind)
        if (at == null) return am.cancel(alarmIntent(context, kind))
        am.setWindow(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), WINDOW_MS, alarmIntent(context, kind))
    }

    fun scheduleAll(context: Context) {
        HabitKind.entries.forEach { schedule(context, it) }
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
                postNotification(
                    context, kind,
                    title = "Meal reminder",
                    text = "Lunch? Snap it before you eat",
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
                if (!prefs.waterReminder || AppModel.get(context).store.state.value.hasWater(LocalDate.now())) return
                postNotification(
                    context, kind,
                    title = "Hydrate",
                    text = "Drink some water.",
                    contentIntent = mainIntent(context),
                )
            }
        }
    }

    fun dismiss(context: Context, kind: HabitKind) =
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(kind))

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
        val cid = channelId(kind)
        nm.createNotificationChannel(NotificationChannel(cid, channelName(kind), NotificationManager.IMPORTANCE_DEFAULT))
        nm.notify(
            notificationId(kind),
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
        context, requestCode(kind),
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
