package com.sammug.logitcat

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class SseClient(
    private val port: Int = 9090,
    private val onAlert: (Alert) -> Unit,
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {}
) {
    private val running = AtomicBoolean(false)
    private var connectionThread: Thread? = null
    private val url = "http://localhost:$port/api/events"

    fun connect() {
        if (running.get()) return
        
        running.set(true)
        connectionThread = thread(isDaemon = true, name = "SSE-Client") {
            while (running.get()) {
                try {
                    connectAndListen()
                } catch (e: Exception) {
                    onDisconnected()
                    if (running.get()) {
                        Thread.sleep(3000) // Auto-reconnect after 3 seconds
                    }
                }
            }
        }
    }

    private fun connectAndListen() {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "text/event-stream")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.connect()

        if (connection.responseCode == 200) {
            onConnected()
            
            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            var line: String? = null
            var eventData = StringBuilder()
            
            while (running.get() && reader.readLine().also { line = it } != null) {
                line?.let { currentLine ->
                    when {
                        currentLine.startsWith("data: ") -> {
                            val data = currentLine.substring(6)
                            eventData.append(data)
                        }
                        currentLine.isEmpty() -> {
                            // End of event, process the data
                            val jsonData = eventData.toString().trim()
                            if (jsonData.isNotEmpty()) {
                                Alert.fromJson(jsonData)?.let { alert ->
                                    onAlert(alert)
                                }
                            }
                            eventData = StringBuilder()
                        }
                        currentLine.startsWith("event: ") -> {
                            // Event type line, we can ignore for now
                        }
                        else -> { /* ignore other lines */ }
                    }
                }
            }
        } else {
            throw Exception("HTTP ${connection.responseCode}")
        }
    }

    fun disconnect() {
        running.set(false)
        connectionThread?.interrupt()
        connectionThread = null
    }

    fun isConnected(): Boolean = running.get()
}