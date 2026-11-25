package dev.gzclp.tracker.food

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import dev.gzclp.tracker.Prefs
import java.io.File
import java.io.OutputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Interface for writing images to media storage (such as MediaStore),
 * allowing pure unit testing and mock/fake implementations.
 */
interface MediaStoreWriter {
    fun insert(name: String, at: OffsetDateTime): Uri?
    fun openOutputStream(uri: Uri): OutputStream?
    fun finish(uri: Uri)
    fun delete(uri: Uri): Boolean
}

/**
 * Android 10+ (API 29+) owned-media writer writing to MediaStore.Images under Pictures/Food
 * with no storage permissions required.
 */
class ContentResolverMediaStoreWriter(private val resolver: ContentResolver) : MediaStoreWriter {
    override fun insert(name: String, at: OffsetDateTime): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, GalleryExport.RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.DATE_TAKEN, at.toInstant().toEpochMilli())
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    override fun openOutputStream(uri: Uri): OutputStream? =
        resolver.openOutputStream(uri)

    override fun finish(uri: Uri) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }
        resolver.update(uri, values, null, null)
    }

    override fun delete(uri: Uri): Boolean = runCatching {
        resolver.delete(uri, null, null) > 0
    }.getOrDefault(false)

}

/**
 * Handles exporting meal photos to the system gallery (Pictures/Food)
 * and deleting gallery copies when a meal is deleted from the app.
 *
 * The app's own copy is the source of truth. Deleting a meal in the app deletes
 * the gallery copy it created (tracked via private sidecar .uri file).
 * Deleting in the gallery does not delete the meal in the app.
 */
class GalleryExport(
    private val writer: MediaStoreWriter,
    private val isEnabled: () -> Boolean = { true },
) {
    constructor(context: Context, prefs: Prefs = Prefs(context)) :
        this(ContentResolverMediaStoreWriter(context.contentResolver), { prefs.saveToGallery })

    /**
     * Exports [meal] to the gallery if enabled.
     * Ensures EXIF capture time (TAG_DATETIME_ORIGINAL, TAG_OFFSET_TIME_ORIGINAL) is preserved,
     * writes the copy to MediaStore under Pictures/Food, and records the content URI.
     */
    fun export(meal: Meal): Uri? {
        if (!isEnabled()) return null
        val file = meal.file
        val at = meal.at
        ensureExif(file, at)
        val uri = writer.insert(file.name, at) ?: return null
        try {
            val out = writer.openOutputStream(uri) ?: error("Could not open gallery output stream for $uri")
            out.use { o ->
                file.inputStream().use { i -> i.copyTo(o) }
            }
            writer.finish(uri)
            saveUriMapping(file, uri)
            return uri
        } catch (e: Exception) {
            runCatching { writer.delete(uri) }
            throw e
        }
    }

    /**
     * Deletes the gallery copy for [meal] if one was created, and removes the URI tracking.
     * Does NOT delete the meal's private file.
     * Returns true if a gallery copy was found and deleted.
     */
    fun delete(meal: Meal): Boolean {
        val uri = uriFor(meal)
        uriFileFor(meal.file).delete()
        return if (uri != null) writer.delete(uri) else false
    }

    fun uriFor(meal: Meal): Uri? {
        val uriFile = uriFileFor(meal.file)
        if (!uriFile.exists()) return null
        val text = runCatching { uriFile.readText().trim() }.getOrNull() ?: return null
        return if (text.isNotEmpty()) runCatching { Uri.parse(text) }.getOrNull() else null
    }

    private fun saveUriMapping(file: File, uri: Uri) {
        val uriFile = uriFileFor(file)
        uriFile.parentFile?.mkdirs()
        uriFile.writeText(uri.toString())
    }

    companion object {
        const val RELATIVE_PATH = "Pictures/Food"
        val exifDateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss")
        val exifOffsetFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("xxx")

        fun uriFileFor(mealFile: File): File =
            File(mealFile.parentFile ?: File("."), "${mealFile.name}.uri")

        fun ensureExif(file: File, at: OffsetDateTime) {
            runCatching {
                val exif = ExifInterface(file.path)
                val dt = at.format(exifDateTimeFmt)
                val off = at.format(exifOffsetFmt)
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dt)
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, off)
                exif.setAttribute(ExifInterface.TAG_DATETIME, dt)
                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, dt)
                exif.saveAttributes()
            }
        }
    }
}
