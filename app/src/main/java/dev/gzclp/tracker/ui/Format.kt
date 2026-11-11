package dev.gzclp.tracker.ui

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val date = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
    private val short = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun lb(w: Int) = "$w lb"
    /** Body weight, always one decimal: 182.4 lb, 181.0 lb. */
    fun lb(w: Double) = "${tenths(w)} lb"
    fun tenths(w: Double) = "%.1f".format(Locale.US, w)
    /** RPE without a trailing .0: 8, 8.5. */
    fun rpe(r: Double) = if (r % 1 == 0.0) "${r.toInt()}" else "$r"
    fun ratio(r: Double) = "%.2f×".format(Locale.US, r)
    /** 7:00 AM from minutes after midnight. */
    fun clockTime(minutes: Int): String = time.format(java.time.LocalTime.of(minutes / 60, minutes % 60))
    fun time(t: OffsetDateTime): String = time.format(t)
    fun date(t: OffsetDateTime): String = date.format(t)
    fun shortDate(t: java.time.temporal.TemporalAccessor): String = short.format(t)

    /** Today, 7:02 AM; Yesterday, 7:02 AM; Sun, Nov 8, 7:02 AM. */
    fun weighInTime(t: OffsetDateTime, today: java.time.LocalDate): String = when (t.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date(t)
    } + ", " + time(t)

    /** 1:47, or 1:04:18 past an hour. */
    fun clock(totalSec: Long): String {
        val s = totalSec.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** 52 min, 1 hr 20 min. */
    fun minutes(totalSec: Long): String {
        val m = (totalSec + 30) / 60
        return if (m >= 60) "${m / 60} hr ${m % 60} min" else "$m min"
    }
}
