package com.sammug.logitcat

import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
class LogitCatService(private val project: Project) {
    private val alerts = CopyOnWriteArrayList<Alert>()
    private val listeners = CopyOnWriteArrayList<AlertListener>()
    private var sseClient: SseClient? = null
    private var isConnected = false

    init {
        subscribeToExecutions()
    }

    interface AlertListener {
        fun onAlertAdded(alert: Alert)
        fun onConnectionChanged(connected: Boolean)
        fun onAlertsCleared()
        fun onCaptureStateChanged(capturing: Boolean, device: DeviceInfo?, pkg: ProcessInfo?) {}
    }

    fun addListener(listener: AlertListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: AlertListener) {
        listeners.remove(listener)
    }

    // ── Android logcat capture ───────────────────────────────────────────────
    private var logcatProcess: Process? = null
    var selectedDevice: DeviceInfo? = null
    var selectedPackage: ProcessInfo? = null

    /** Start (or restart) logcat capture for the given device + optional package. */
    fun startAndroidCapture(
        device: DeviceInfo? = selectedDevice,
        pkg: ProcessInfo?   = selectedPackage,
        delayMs: Long       = 0L
    ) {
        stopAndroidCapture()

        val settings = LogitCatSettings.getInstance()
        val exec = resolveExec(settings.executablePath) ?: run {
            notify("LogitCat: binary not found — set path in Settings → Tools → LogitCat",
                NotificationType.WARNING); return
        }
        val cfg = resolveAndroidCfg(settings.androidConfigPath) ?: run {
            notify("LogitCat: android.ini not found — set path in Settings",
                NotificationType.WARNING); return
        }
        val adb = resolveAdb(settings.adbPath) ?: run {
            notify("LogitCat: adb not found — set path in Settings → Tools → LogitCat",
                NotificationType.WARNING); return
        }

        // Pick device — use selected, or first available
        val targetDevice = device ?: DeviceManager.listDevices(adb).firstOrNull() ?: run {
            notify("LogitCat: no ADB devices connected", NotificationType.WARNING); return
        }
        selectedDevice  = targetDevice
        selectedPackage = pkg

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (delayMs > 0) Thread.sleep(delayMs)
                val cmd = DeviceManager.buildPipeCommand(
                    adb, targetDevice.serial, pkg?.packageName, exec, cfg
                )
                logcatProcess = ProcessBuilder("sh", "-c", cmd)
                    .redirectErrorStream(true).start()

                val label = if (pkg != null) pkg.packageName else targetDevice.label
                notify("⚡ LogitCat: capturing $label", NotificationType.INFORMATION)
                listeners.forEach { it.onCaptureStateChanged(true, targetDevice, pkg) }
            } catch (e: Exception) {
                notify("LogitCat: logcat failed — ${e.message}", NotificationType.WARNING)
            }
        }
    }

    fun stopAndroidCapture() {
        logcatProcess?.destroy()
        logcatProcess = null
        listeners.forEach { it.onCaptureStateChanged(false, selectedDevice, selectedPackage) }
    }

    fun isCapturing(): Boolean = logcatProcess?.isAlive == true

    private fun subscribeToExecutions() {
        project.messageBus.connect().subscribe(ExecutionManager.EXECUTION_TOPIC,
            object : ExecutionListener {
                override fun processStarted(
                    executorId: String, env: ExecutionEnvironment, handler: ProcessHandler
                ) {
                    val isAndroid = env.runProfile?.javaClass?.name
                        ?.lowercase()?.contains("android") == true
                    if (isAndroid) startAndroidCapture()
                }
                override fun processTerminated(
                    executorId: String, env: ExecutionEnvironment,
                    handler: ProcessHandler, exitCode: Int
                ) { stopAndroidCapture() }
            })
    }

    private fun resolveExec(p: String) =
        p.takeIf { it.isNotBlank() && File(it).exists() }
            ?: listOf("/tmp/logitcat","/usr/local/bin/logitcat",
                "${System.getProperty("user.home")}/.local/bin/logitcat")
                .firstOrNull { File(it).exists() }

    private fun resolveAndroidCfg(p: String) =
        p.takeIf { it.isNotBlank() && File(it).exists() }
            ?: listOf(
                "${System.getProperty("user.home")}/projects/logitcat/config/android.ini",
                "/tmp/android.ini")
                .firstOrNull { File(it).exists() }

    private fun resolveAdb(p: String): String? {
        if (p.isNotBlank() && File(p).exists()) return p
        val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (sdk != null && File("$sdk/platform-tools/adb").exists()) return "$sdk/platform-tools/adb"
        return listOf(
            "${System.getProperty("user.home")}/Library/Android/sdk/platform-tools/adb",
            "/usr/local/bin/adb", "/opt/homebrew/bin/adb"
        ).firstOrNull { File(it).exists() }
    }

    private fun notify(msg: String, type: NotificationType) {
        ApplicationManager.getApplication().invokeLater {
            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("LogitCat")
                    .createNotification(msg, type).notify(project)
            } catch (_: Exception) {}
        }
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