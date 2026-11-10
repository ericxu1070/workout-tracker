package dev.gzclp.tracker.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Keeps docs/log-format.md honest: every example line must decode and re-encode byte for byte. */
class LogFormatDocTest {
    @Test fun documentedExampleLinesRoundTrip() {
        val doc = File("../docs/log-format.md").readText()
        val lines = doc.substringAfter("```jsonl\n").substringBefore("```").lines().filter { it.isNotBlank() }
        assertTrue(lines.size >= 8)
        for (line in lines) {
            val entry = LogCodec.decode(line)
            assertNotNull("decodes: $line", entry)
            assertEquals(line, LogCodec.encode(entry!!))
        }
    }
}
