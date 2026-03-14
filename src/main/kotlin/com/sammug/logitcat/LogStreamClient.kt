package com.sammug.logitcat

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Connects to /api/logs SSE endpoint and emits every log line.
 */
class LogStreamClient(
    private val port: Int,
    private val onLine: (LogLine) -> Unit,
    private val onConnected: () -> Unit,
    private val onDisconnected: () -> Unit
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    fun connect() {
        running = true
        thread = Thread({
            while (running) {
                try {
                    val url = URL("http://localhost:$port/api/logs")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("Accept", "text/event-stream")
                    conn.setRequestProperty("Cache-Control", "no-cache")
                    conn.connectTimeout = 3000
                    conn.readTimeout = 0

                    conn.connect()
                    onConnected()

                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    var eventType = ""
                    var dataLine = ""

                    var line: String?
                    while (reader.readLine().also { line = it } != null && running) {
                        when {
                            line!!.startsWith("event:") -> eventType = line!!.substring(6).trim()
                            line!!.startsWith("data:")  -> dataLine  = line!!.substring(5).trim()
                            line!!.isEmpty() && dataLine.isNotEmpty() -> {
                                if (eventType == "log") {
                                    LogLine.fromJson(dataLine)?.let { onLine(it) }
                                }
                                eventType = ""
                                dataLine  = ""
                            }
                            else -> {}
                        }
                    }
                } catch (_: Exception) {
                    if (running) {
                        onDisconnected()
                        Thread.sleep(3000)
                    }
                }
            }
        }, "logitcat-log-stream")
        thread!!.isDaemon = true
        thread!!.start()
    }

    fun disconnect() {
        running = false
        thread?.interrupt()
        thread = null
    }
}
