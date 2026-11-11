package dev.gzclp.tracker.food

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max

/** One meal: its photo, named for the time it was taken. */
data class Meal(val file: File, val at: OffsetDateTime)

/**
 * Meal photos in app-private storage, one JPEG per meal; the file name is the meal's time, so the folder is the log.
 * Never mirrored or synced: nothing here touches the event log.
 */
class FoodLog(private val dir: File) {
    fun meals(): List<Meal> =
        dir.listFiles().orEmpty().mapNotNull { f -> parse(f.name)?.let { Meal(f, it) } }.sortedBy { it.at }

    /** Downscales [photo] (from the camera or the photo picker) into the log as a meal at [at], then deletes [photo]. */
    fun add(photo: File, at: OffsetDateTime): Meal {
        dir.mkdirs()
        var t = at.withNano(0)
        while (File(dir, name(t)).exists()) t = t.plusSeconds(1)
        val out = File(dir, name(t))
        val bmp = downscale(photo) ?: error("Could not read the photo")
        val tmp = File(dir, out.name + ".tmp")
        tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        check(tmp.renameTo(out)) { "Could not save the photo" }
        photo.delete()
        return Meal(out, t)
    }

    fun delete(meal: Meal) = meal.file.delete()

    companion object {
        /** Long side of a saved photo, in pixels: sharp on a phone, about 150 KB. */
        const val MAX_SIDE = 1280
        private val fmt = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmssxx")
        private val exifFmt = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss")
        /** How old a picked photo's own time may be and still be trusted as when the meal was eaten. */
        val MAX_AGE: Duration = Duration.ofDays(7)

        fun name(at: OffsetDateTime) = fmt.format(at) + ".jpg"
        fun parse(name: String): OffsetDateTime? =
            if (!name.endsWith(".jpg")) null else runCatching { OffsetDateTime.parse(name.removeSuffix(".jpg"), fmt) }.getOrNull()

        /** When [file] was taken, from its EXIF; null when it has no readable capture time. */
        fun takenAt(file: File, zone: ZoneId): OffsetDateTime? = runCatching {
            val exif = ExifInterface(file.path)
            takenAt(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL), exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL), zone)
        }.getOrNull()

        /** EXIF DateTimeOriginal ("2026:11:10 12:30:05") at its OffsetTimeOriginal ("-08:00"), or in [zone] when that is missing. */
        fun takenAt(dateTime: String?, offset: String?, zone: ZoneId): OffsetDateTime? {
            val local = dateTime?.let { runCatching { LocalDateTime.parse(it.trim(), exifFmt) }.getOrNull() } ?: return null
            val off = offset?.let { runCatching { ZoneOffset.of(it.trim()) }.getOrNull() }
            return if (off != null) local.atOffset(off) else local.atZone(zone).toOffsetDateTime()
        }

        /** A photo time can be the meal's when it is not in the future and at most [MAX_AGE] old. */
        fun feasible(at: OffsetDateTime, now: OffsetDateTime) = !at.isAfter(now) && !at.isBefore(now.minus(MAX_AGE))

        /** Decodes [file] upright (the camera's EXIF rotation applied) with its long side at most [maxSide]. */
        fun downscale(file: File, maxSide: Int = MAX_SIDE): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val long = max(bounds.outWidth, bounds.outHeight)
            if (long <= 0) return null
            var sample = 1
            while (long / (sample * 2) >= maxSide) sample *= 2
            val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val scale = minOf(1f, maxSide.toFloat() / max(raw.width, raw.height))
            val m = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotation(file).toFloat())
            }
            return if (m.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        }

        private fun rotation(file: File): Int = runCatching {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }
}
