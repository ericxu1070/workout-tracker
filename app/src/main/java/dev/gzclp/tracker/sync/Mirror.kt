package dev.gzclp.tracker.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.gzclp.tracker.Prefs

/**
 * Mirrors the local log into the user-picked SAF folder that Syncthing shares.
 * Converges: appends only the new tail when the remote length matches what we last wrote,
 * otherwise rewrites the whole file. Safe to call any number of times.
 */
class Mirror(private val context: Context, private val prefs: Prefs) {
    fun sync(local: ByteArray): Boolean {
        val uri = prefs.folderUri?.let(Uri::parse) ?: return false
        return try {
            val doc = file(uri, create = true) ?: error("Folder is not writable")
            val mirrored = prefs.mirroredBytes
            val resolver = context.contentResolver
            if (mirrored in 0..local.size.toLong() && doc.length() == mirrored) {
                if (mirrored < local.size) {
                    resolver.openOutputStream(doc.uri, "wa")!!.use { it.write(local, mirrored.toInt(), local.size - mirrored.toInt()) }
                }
            } else {
                resolver.openOutputStream(doc.uri, "rwt")!!.use { it.write(local) }
            }
            check(doc.length() == local.size.toLong()) { "Mirror length mismatch" }
            prefs.mirroredBytes = local.size.toLong()
            prefs.lastSyncMillis = System.currentTimeMillis()
            prefs.lastSyncError = null
            true
        } catch (e: Exception) {
            prefs.mirroredBytes = -1
            prefs.lastSyncError = e.message ?: e.javaClass.simpleName
            false
        }
    }

    /** The text of [name] in the folder (the mirrored log by default), or null if the folder has none. */
    fun read(uri: Uri, name: String = NAME): String? {
        val doc = DocumentFile.fromTreeUri(context, uri)?.findFile(name) ?: return null
        return context.contentResolver.openInputStream(doc.uri)?.use { it.readBytes().decodeToString() }
    }

    fun folderName(): String? = prefs.folderUri?.let { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }

    private fun file(tree: Uri, create: Boolean): DocumentFile? {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return null
        if (!dir.canWrite() && create) return null
        return dir.findFile(NAME) ?: if (create) dir.createFile("application/octet-stream", NAME) else null
    }

    companion object {
        const val NAME = "gzclp-log.jsonl"
    }
}
