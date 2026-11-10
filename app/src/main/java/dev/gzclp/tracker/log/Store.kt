package dev.gzclp.tracker.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.time.OffsetDateTime

/**
 * The append-only JSONL log in app-private storage is the source of truth; [state] is its fold.
 * All writes go through [dispatch], which is serialized.
 */
class Store(private val file: File) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state

    @Synchronized
    fun dispatch(now: OffsetDateTime, build: (AppState) -> List<Event>): List<Entry> {
        val events = build(_state.value)
        if (events.isEmpty()) return emptyList()
        var seq = _state.value.lastSeq
        val entries = events.map { Entry(++seq, now, it) }
        FileOutputStream(file, true).use { out ->
            out.write(entries.joinToString("") { LogCodec.encode(it) + "\n" }.toByteArray())
            out.fd.sync()
        }
        _state.value = entries.fold(_state.value) { s, e -> s.apply(e) }
        return entries
    }

    @Synchronized
    fun bytes(): ByteArray = if (file.exists()) file.readBytes() else ByteArray(0)

    /** Replaces an empty local log with [text] from the synced folder. Returns entries read. */
    @Synchronized
    fun restore(text: String): Int {
        check(!_state.value.started) { "Local log is not empty" }
        val entries = text.lineSequence().filter { it.isNotBlank() }.mapNotNull { LogCodec.decode(it) }.toList()
        require(entries.any { it.event is ProgramStart }) { "No program found in that log" }
        val tmp = File(file.parentFile, file.name + ".restore")
        FileOutputStream(tmp).use { out ->
            out.write(text.trimEnd('\n').plus("\n").toByteArray())
            out.fd.sync()
        }
        check(tmp.renameTo(file)) { "Could not replace the local log" }
        _state.value = load()
        return entries.size
    }

    private fun load(): AppState {
        if (!file.exists()) return AppState()
        dropTornTail()
        val lines = file.readLines().filter { it.isNotBlank() }
        val entries = lines.mapNotNull { LogCodec.decode(it) }
        // A log from an older schema version reads as nothing; set it aside so new seq numbers never collide with it.
        if (lines.isNotEmpty() && entries.isEmpty()) file.renameTo(File(file.parentFile, file.name + ".old"))
        return AppState.fold(entries)
    }

    /** A crash mid-append can leave a partial last line; drop it so the next append starts clean. */
    private fun dropTornTail() {
        RandomAccessFile(file, "rw").use { raf ->
            val len = raf.length()
            if (len == 0L) return
            raf.seek(len - 1)
            if (raf.read() == '\n'.code) return
            var pos = len - 1
            while (pos > 0) {
                raf.seek(pos - 1)
                if (raf.read() == '\n'.code) break
                pos--
            }
            raf.setLength(pos)
        }
    }
}
