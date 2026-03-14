package com.sammug.logitcat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class LogitCatService(private val project: Project) {
    private val alerts = CopyOnWriteArrayList<Alert>()
    private val listeners = CopyOnWriteArrayList<AlertListener>()
    private var sseClient: SseClient? = null
    private var isConnected = false

    interface AlertListener {
        fun onAlertAdded(alert: Alert)
        fun onConnectionChanged(connected: Boolean)
        fun onAlertsCleared()
    }

    fun addListener(listener: AlertListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: AlertListener) {
        listeners.remove(listener)
    }

    fun startWatching() {
        if (sseClient?.isConnected() == true) return

        val settings = LogitCatSettings.getInstance()
        sseClient = SseClient(
            port = settings.dashboardPort,
            onAlert = { alert ->
                ApplicationManager.getApplication().invokeLater {
                    addAlert(alert)
                }
            },
            onConnected = {
                ApplicationManager.getApplication().invokeLater {
                    isConnected = true
                    listeners.forEach { it.onConnectionChanged(true) }
                }
            },
            onDisconnected = {
                ApplicationManager.getApplication().invokeLater {
                    isConnected = false
                    listeners.forEach { it.onConnectionChanged(false) }
                }
            }
        )
        sseClient?.connect()
    }

    fun stopWatching() {
        sseClient?.disconnect()
        sseClient = null
        isConnected = false
        listeners.forEach { it.onConnectionChanged(false) }
    }

    private fun addAlert(alert: Alert) {
        alerts.add(0, alert) // Add to beginning for newest first
        
        val settings = LogitCatSettings.getInstance()
        if (alerts.size > settings.maxAlerts) {
            alerts.removeAt(alerts.size - 1)
        }

        listeners.forEach { it.onAlertAdded(alert) }
    }

    fun clearAlerts() {
        alerts.clear()
        listeners.forEach { it.onAlertsCleared() }
    }

    fun getAlerts(): List<Alert> = ArrayList(alerts)

    fun isConnected(): Boolean = isConnected

    fun getAlertCount(): Int = alerts.size

    fun getCriticalCount(): Int = alerts.count { it.severity == "CRITICAL" }

    companion object {
        fun getInstance(project: Project): LogitCatService =
            project.getService(LogitCatService::class.java)
    }
}