package com.sammug.logitcat

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

class AlertsPanel(private val project: Project) : JPanel(BorderLayout()), LogitCatService.AlertListener {
    private val service = LogitCatService.getInstance(project)
    private val alertListModel = DefaultListModel<Alert>()
    private val alertList = JBList(alertListModel)
    private val alertDetailPanel = AlertDetailPanel()
    private val emptyLabel = JLabel("No alerts yet — watching for logs…", SwingConstants.CENTER)
    
    init {
        setupUI()
        service.addListener(this)
        refreshAlerts()
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
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)
        toolbar.border = JBUI.Borders.empty(8)

        val startButton = JButton("Start", AllIcons.Actions.Execute)
        startButton.addActionListener { service.startWatching() }

        val stopButton = JButton("Stop", AllIcons.Actions.Suspend)
        stopButton.addActionListener { service.stopWatching() }

        val clearButton = JButton("Clear", AllIcons.Actions.GC)
        clearButton.addActionListener { service.clearAlerts() }

        val dashboardButton = JButton("Dashboard", AllIcons.Ide.External_link_arrow)
        dashboardButton.addActionListener {
            val settings = LogitCatSettings.getInstance()
            BrowserUtil.browse("http://localhost:${settings.dashboardPort}")
        }

        toolbar.add(startButton)
        toolbar.add(Box.createHorizontalStrut(4))
        toolbar.add(stopButton)
        toolbar.add(Box.createHorizontalStrut(4))
        toolbar.add(clearButton)
        toolbar.add(Box.createHorizontalStrut(4))
        toolbar.add(dashboardButton)
        toolbar.add(Box.createHorizontalGlue())

        return toolbar
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
        // Could update UI to show connection status
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