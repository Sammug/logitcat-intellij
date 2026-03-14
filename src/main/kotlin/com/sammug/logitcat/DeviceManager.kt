package com.sammug.logitcat

import java.io.File

data class DeviceInfo(
    val serial: String,
    val type: String,        // "device" | "emulator" | "offline"
    val label: String        // display name
)

data class ProcessInfo(
    val pid: String,
    val packageName: String
)

/**
 * DeviceManager wraps adb commands to enumerate devices and running processes.
 */
object DeviceManager {

    // ── adb binary ─────────────────────────────────────────────────────────────

    fun resolveAdb(configured: String = ""): String? {
        if (configured.isNotBlank() && File(configured).exists()) return configured
        val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (sdk != null) {
            val p = "$sdk/platform-tools/adb"
            if (File(p).exists()) return p
        }
        return listOf(
            "${System.getProperty("user.home")}/Library/Android/sdk/platform-tools/adb",
            "/opt/homebrew/bin/adb",
            "/usr/local/bin/adb"
        ).firstOrNull { File(it).exists() }
    }

    // ── Devices ────────────────────────────────────────────────────────────────

    fun listDevices(adb: String): List<DeviceInfo> {
        return try {
            val output = run(adb, "devices")
            output.lines()
                .drop(1)                         // skip "List of devices attached"
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size < 2) return@mapNotNull null
                    val serial = parts[0]
                    val state  = parts[1]
                    if (state == "offline" || state == "unauthorized") return@mapNotNull null
                    DeviceInfo(
                        serial = serial,
                        type   = if (serial.startsWith("emulator")) "emulator" else "device",
                        label  = friendlyLabel(adb, serial)
                    )
                }
        } catch (_: Exception) { emptyList() }
    }

    // ── Running packages ───────────────────────────────────────────────────────

    fun listRunningPackages(adb: String, serial: String): List<ProcessInfo> {
        return try {
            val output = run(adb, "-s", serial, "shell", "ps", "-A")
            output.lines()
                .filter { it.contains(".") }     // package names have dots
                .mapNotNull { line ->
                    val cols = line.trim().split("\\s+".toRegex())
                    if (cols.size < 9) return@mapNotNull null
                    val pid  = cols[1]
                    val name = cols.last()
                    if (!name.contains(".")) return@mapNotNull null
                    ProcessInfo(pid = pid, packageName = name)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.packageName }
        } catch (_: Exception) { emptyList() }
    }

    // ── PID lookup ─────────────────────────────────────────────────────────────

    fun getPid(adb: String, serial: String, packageName: String): String? {
        return try {
            val out = run(adb, "-s", serial, "shell", "pidof", packageName).trim()
            if (out.isBlank() || out.contains("not found")) null else out.split(" ").first()
        } catch (_: Exception) { null }
    }

    // ── Build logcat command ───────────────────────────────────────────────────

    /**
     * Returns a shell command string that pipes logcat through logitcat.
     * If packageName is set, filters by PID of that package.
     */
    fun buildPipeCommand(
        adb: String,
        serial: String,
        packageName: String?,
        logitcatExec: String,
        configPath: String
    ): String {
        val deviceFlag = "-s \"$serial\""

        val logcatCmd = if (!packageName.isNullOrBlank()) {
            // Get PID at pipe-start time via subshell; fall back to full logcat
            "LC_PID=\$(\"$adb\" $deviceFlag shell pidof \"$packageName\" 2>/dev/null | awk '{print \$1}'); " +
            "if [ -n \"\$LC_PID\" ]; then \"$adb\" $deviceFlag logcat --pid=\"\$LC_PID\"; else \"$adb\" $deviceFlag logcat; fi"
        } else {
            """"$adb" $deviceFlag logcat"""
        }

        val source = if (!packageName.isNullOrBlank()) packageName else serial
        return """$logcatCmd | "$logitcatExec" pipe "$configPath" --source "$source" --dashboard"""
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun friendlyLabel(adb: String, serial: String): String {
        if (serial.startsWith("emulator")) return "📱 $serial"
        return try {
            val model = run(adb, "-s", serial, "shell", "getprop", "ro.product.model").trim()
            if (model.isNotBlank()) "📱 $model ($serial)" else "📱 $serial"
        } catch (_: Exception) { "📱 $serial" }
    }

    private fun run(vararg cmd: String): String {
        val proc = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return out
    }
}
