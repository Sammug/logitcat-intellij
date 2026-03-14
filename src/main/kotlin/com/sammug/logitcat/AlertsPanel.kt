package com.sammug.logitcat

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class AlertsPanel(private val project: Project) : JPanel(BorderLayout()), LogitCatService.AlertListener {
    private val service = LogitCatService.getInstance(project)
    private val alertListModel = DefaultListModel<Alert>()
    private val alertList = JBList(alertListModel)
    private val alertDetailPanel = AlertDetailPanel()
    private val emptyLabel = JLabel("No alerts yet — connect a device and start capturing", SwingConstants.CENTER)

    // ── Toolbar state ─────────────────────────────────────────────────────────
    private val deviceCombo   = JComboBox<DeviceInfo>()
    private val packageCombo  = JComboBox<ProcessInfo>()
    private val captureButton = JButton("▶  Start Capture", AllIcons.Actions.Execute)
    private val stopButton    = JButton(AllIcons.Actions.Suspend)
    private val refreshDevBtn = JButton(AllIcons.Actions.Refresh)
    private val statusLabel   = JLabel("○ offline")
    private var adb: String?  = null

    // ── Polling ───────────────────────────────────────────────────────────────
    private val poller = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "logitcat-device-poll").also { it.isDaemon = true }
    }

    init {
        setupUI()
        service.addListener(this)
        refreshAlerts()
        startDevicePolling()
    }

    private fun setupUI() {
        // Setup alert list
        alertList.cellRenderer = AlertCellRenderer()
        alertList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        alertList.addListSelectionListener(object : ListSelectionListener {
            override fun valueChanged(e: ListSelectionEvent) {
                if (!e.valueIsAdjusting) {
                    val selectedAlert = alertList.selectedValue
                    alertDetailPanel.showAlert(selectedAlert)
                }
            }
        })

        // Setup toolbar
        val toolbar = createToolbar()

        // Setup splitter
        val splitter = Splitter(true, 0.6f)
        splitter.firstComponent = JBScrollPane(alertList)
        splitter.secondComponent = alertDetailPanel

        add(toolbar, BorderLayout.NORTH)
        add(splitter, BorderLayout.CENTER)

        // Setup empty state
        emptyLabel.foreground = JBColor.GRAY
        showEmptyState()
    }

    private fun createToolbar(): JComponent {
        val settings = LogitCatSettings.getInstance()
        adb = DeviceManager.resolveAdb(settings.adbPath)

        // ── Device dropdown ───────────────────────────────────────────────────
        deviceCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                                      isSelected: Boolean, cellHasFocus: Boolean): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? DeviceInfo)?.label ?: "No devices"
                return this
            }
        }
        deviceCombo.preferredSize = Dimension(200, deviceCombo.preferredSize.height)
        deviceCombo.maximumSize   = Dimension(220, deviceCombo.preferredSize.height)
        deviceCombo.toolTipText   = "Select ADB device"
        deviceCombo.addActionListener {
            val dev = deviceCombo.selectedItem as? DeviceInfo ?: return@addActionListener
            service.selectedDevice = dev
            refreshPackages(dev)
        }

        // ── Package dropdown ──────────────────────────────────────────────────
        packageCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                                                      isSelected: Boolean, cellHasFocus: Boolean): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = when (value) {
                    is ProcessInfo -> "📦 ${value.packageName}"
                    null           -> "All processes"
                    else           -> value.toString()
                }
                return this
            }
        }
        packageCombo.preferredSize = Dimension(230, packageCombo.preferredSize.height)
        packageCombo.maximumSize   = Dimension(250, packageCombo.preferredSize.height)
        packageCombo.toolTipText   = "Filter by package/process"
        packageCombo.addItem(null)   // "All processes" option

        // ── Buttons ───────────────────────────────────────────────────────────
        captureButton.toolTipText = "Start logcat capture"
        captureButton.addActionListener {
            val dev = deviceCombo.selectedItem as? DeviceInfo
            val pkg = packageCombo.selectedItem as? ProcessInfo
            service.startAndroidCapture(device = dev, pkg = pkg)
        }

        stopButton.toolTipText = "Stop capture"
        stopButton.addActionListener { service.stopAndroidCapture() }

        refreshDevBtn.toolTipText = "Refresh devices & processes"
        refreshDevBtn.addActionListener { refreshDevices() }

        val clearButton = JButton(AllIcons.Actions.GC)
        clearButton.toolTipText = "Clear alerts"
        clearButton.addActionListener { service.clearAlerts() }

        val dashButton = JButton(AllIcons.Ide.External_link_arrow)
        dashButton.toolTipText = "Open dashboard"
        dashButton.addActionListener {
            BrowserUtil.browse("http://localhost:${settings.dashboardPort}")
        }

        // ── Status label ──────────────────────────────────────────────────────
        statusLabel.foreground = JBColor.GRAY
        statusLabel.font = statusLabel.font.deriveFont(11f)

        // ── Assemble ──────────────────────────────────────────────────────────
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)
        toolbar.border = JBUI.Borders.empty(4, 6)

        toolbar.add(deviceCombo)
        toolbar.add(Box.createHorizontalStrut(4))
        toolbar.add(packageCombo)
        toolbar.add(Box.createHorizontalStrut(4))
        toolbar.add(refreshDevBtn)
        toolbar.add(Box.createHorizontalStrut(6))
        toolbar.add(captureButton)
        toolbar.add(Box.createHorizontalStrut(2))
        toolbar.add(stopButton)
        toolbar.add(Box.createHorizontalStrut(6))
        toolbar.add(clearButton)
        toolbar.add(Box.createHorizontalStrut(2))
        toolbar.add(dashButton)
        toolbar.add(Box.createHorizontalStrut(8))
        toolbar.add(statusLabel)
        toolbar.add(Box.createHorizontalGlue())

        // Initial device load
        refreshDevices()
        return toolbar
    }

    private fun refreshDevices() {
        val a = adb ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val devices = DeviceManager.listDevices(a)
            SwingUtilities.invokeLater {
                val prev = deviceCombo.selectedItem as? DeviceInfo
                deviceCombo.removeAllItems()
                if (devices.isEmpty()) {
                    deviceCombo.addItem(null)
                    packageCombo.removeAllItems()
                    packageCombo.addItem(null)
                } else {
                    devices.forEach { deviceCombo.addItem(it) }
                    // Restore previous selection if still available
                    val restore = devices.firstOrNull { it.serial == prev?.serial } ?: devices.first()
                    deviceCombo.selectedItem = restore
                    refreshPackages(restore)
                }
            }
        }
    }

    private fun refreshPackages(device: DeviceInfo) {
        val a = adb ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val procs = DeviceManager.listRunningPackages(a, device.serial)
            SwingUtilities.invokeLater {
                val prev = (packageCombo.selectedItem as? ProcessInfo)?.packageName
                packageCombo.removeAllItems()
                packageCombo.addItem(null)   // "All processes"
                procs.forEach { packageCombo.addItem(it) }
                // Restore previous package selection
                val restore = procs.firstOrNull { it.packageName == prev }
                if (restore != null) packageCombo.selectedItem = restore
            }
        }
    }

    private fun startDevicePolling() {
        poller.scheduleAtFixedRate({
            val a = adb ?: return@scheduleAtFixedRate
            val devices = DeviceManager.listDevices(a)
            SwingUtilities.invokeLater {
                val currentSerials = (0 until deviceCombo.itemCount)
                    .mapNotNull { deviceCombo.getItemAt(it)?.serial }.toSet()
                val newSerials = devices.map { it.serial }.toSet()
                if (currentSerials != newSerials) refreshDevices()
            }
        }, 3, 3, TimeUnit.SECONDS)
    }

    private fun showEmptyState() {
        if (alertListModel.isEmpty) {
            removeAll()
            add(createToolbar(), BorderLayout.NORTH)
            add(emptyLabel, BorderLayout.CENTER)
            revalidate()
            repaint()
        }
    }

    private fun showAlertList() {
        if (componentCount == 1 || (componentCount == 2 && getComponent(1) == emptyLabel)) {
            removeAll()
            setupUI()
            revalidate()
            repaint()
        }
    }

    private fun refreshAlerts() {
        alertListModel.clear()
        service.getAlerts().forEach { alertListModel.addElement(it) }
        
        if (alertListModel.isEmpty) {
            showEmptyState()
        } else {
            showAlertList()
        }
    }

    override fun onAlertAdded(alert: Alert) {
        alertListModel.add(0, alert)
        if (alertListModel.size() == 1) {
            showAlertList()
        }
    }

    override fun onConnectionChanged(connected: Boolean) {
        SwingUtilities.invokeLater {
            statusLabel.text = if (connected) "● connected" else "○ offline"
            statusLabel.foreground = if (connected)
                JBColor(0x3fb950, 0x3fb950) else JBColor.GRAY
        }
    }

    override fun onCaptureStateChanged(capturing: Boolean, device: DeviceInfo?, pkg: ProcessInfo?) {
        SwingUtilities.invokeLater {
            val label = when {
                !capturing -> "○ offline"
                pkg != null -> "⚡ ${pkg.packageName}"
                device != null -> "⚡ ${device.serial}"
                else -> "⚡ capturing"
            }
            statusLabel.text = label
            statusLabel.foreground = if (capturing)
                JBColor(0x58a6ff, 0x58a6ff) else JBColor.GRAY
            captureButton.isEnabled = !capturing
            stopButton.isEnabled    = capturing
        }
    }

    override fun onAlertsCleared() {
        alertListModel.clear()
        alertDetailPanel.showAlert(null)
        showEmptyState()
    }

    private class AlertCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            if (value is Alert) {
                val severityColor = when (value.severity) {
                    "CRITICAL" -> JBColor(0xf85149, 0xf85149)
                    "WARN" -> JBColor(0xe3b341, 0xe3b341)
                    "INFO" -> JBColor(0x58a6ff, 0x58a6ff)
                    else -> JBColor.GRAY
                }
                
                text = "${value.severity} • ${value.message} • ${value.rule} • ${value.getFormattedTime()}"
                
                if (!isSelected) {
                    foreground = severityColor
                }
                
                border = JBUI.Borders.empty(4, 8)
            }
            
            return this
        }
    }
}