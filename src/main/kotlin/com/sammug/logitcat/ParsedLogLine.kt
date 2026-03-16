package com.sammug.logitcat

import java.util.concurrent.atomic.AtomicLong

private val _idGen = AtomicLong(0)

/**
 * A fully-parsed log line ready for display.
 * Extracted from raw LogLine — android logcat format or generic fallback.
 */
data class ParsedLogLine(
    val id: Long = _idGen.incrementAndGet(),
    val timestamp: String,   // "MM-DD HH:MM:SS.mmm"
    val pidTid: String,      // "12839-13013" or ""
    val tag: String,         // "OkHttp", "MainActivity", ...
    val source: String,      // package / source name
    val level: Char,         // V D I W E F ?
    val message: String,
    val raw: String
) {
    companion object {
        // Android logcat brief/threadtime format:
        // MM-DD HH:MM:SS.mmm  PID  TID  L  Tag  : Message
        private val ANDROID = Regex(
            """^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+(.+?)\s*:\s*(.*)$"""
        )
        // ISO timestamp prefix (from our own pipeline): 2026-03-16T19:15:22+03:00
        // We strip the date and just show time
        private val ISO_TS = Regex("""^\d{4}-\d{2}-\d{2}T(\d{2}:\d{2}:\d{2})""")

        fun from(line: LogLine): ParsedLogLine {
            // Try Android logcat format first (from raw line)
            val m = ANDROID.find(line.raw)
            if (m != null) {
                val (ts, pid, tid, lv, tag, msg) = m.destructured
                return ParsedLogLine(
                    timestamp = ts,
                    pidTid    = "$pid-$tid",
                    tag       = tag.trim().take(23),
                    source    = line.source,
                    level     = lv[0],
                    message   = msg.trim(),
                    raw       = line.raw
                )
            }
            // Generic fallback
            val ts = ISO_TS.find(line.time)?.groupValues?.get(1) ?: line.time.take(19)
            return ParsedLogLine(
                timestamp = ts,
                pidTid    = "",
                tag       = line.tag.ifBlank { line.source }.take(23),
                source    = line.source,
                level     = line.levelChar(),
                message   = line.message,
                raw       = line.raw
            )
        }
    }
}
