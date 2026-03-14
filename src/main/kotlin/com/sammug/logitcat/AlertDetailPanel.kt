package com.sammug.logitcat

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.TitledBorder

class AlertDetailPanel : JPanel(BorderLayout()) {
    private val contentPanel = JPanel()
    private val scrollPane = JBScrollPane(contentPanel)
    
    init {
        add(scrollPane, BorderLayout.CENTER)
        showEmptyState()
    }

    fun showAlert(alert: Alert?) {
        contentPanel.removeAll()
        
        if (alert == null) {
            showEmptyState()
            return
        }
        
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = JBUI.Borders.empty(16)
        
        // Header
        val headerPanel = createHeaderPanel(alert)
        contentPanel.add(headerPanel)
        contentPanel.add(Box.createVerticalStrut(16))
        
        // Details table
        val detailsPanel = createDetailsPanel(alert)
        contentPanel.add(detailsPanel)
        contentPanel.add(Box.createVerticalStrut(16))
        
        // Fields section
        if (alert.fields.isNotEmpty()) {
            val fieldsPanel = createFieldsPanel(alert.fields)
            contentPanel.add(fieldsPanel)
            contentPanel.add(Box.createVerticalStrut(16))
        }
        
        // Raw data section
        val rawPanel = createRawPanel(alert.raw)
        contentPanel.add(rawPanel)
        
        contentPanel.add(Box.createVerticalGlue())
        
        revalidate()
        repaint()
    }
    
    private fun showEmptyState() {
        contentPanel.removeAll()
        contentPanel.layout = BorderLayout()
        val emptyLabel = JBLabel("Select an alert to view details", SwingConstants.CENTER)
        emptyLabel.foreground = JBColor.GRAY
        contentPanel.add(emptyLabel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }
    
    private fun createHeaderPanel(alert: Alert): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT))
        
        val severityColor = when (alert.severity) {
            "CRITICAL" -> JBColor(0xf85149, 0xf85149)
            "WARN" -> JBColor(0xe3b341, 0xe3b341) 
            "INFO" -> JBColor(0x58a6ff, 0x58a6ff)
            else -> JBColor.GRAY
        }
        
        val severityLabel = JBLabel(alert.severity)
        severityLabel.foreground = Color.WHITE
        severityLabel.background = severityColor
        severityLabel.isOpaque = true
        severityLabel.border = JBUI.Borders.empty(4, 8)
        
        val ruleLabel = JBLabel(alert.rule)
        ruleLabel.font = ruleLabel.font.deriveFont(Font.BOLD)
        
        val timeLabel = JBLabel(alert.time)
        timeLabel.foreground = JBColor.GRAY
        
        panel.add(severityLabel)
        panel.add(Box.createHorizontalStrut(8))
        panel.add(ruleLabel)
        panel.add(Box.createHorizontalStrut(8))
        panel.add(timeLabel)
        
        return panel
    }
    
    private fun createDetailsPanel(alert: Alert): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder("Details")
        
        val detailsData = arrayOf(
            arrayOf("Time", alert.time),
            arrayOf("Level", alert.level),
            arrayOf("Rule", alert.rule),
            arrayOf("Format", alert.format),
            arrayOf("Source", alert.source)
        )
        
        val table = JTable(detailsData, arrayOf("Property", "Value"))
        table.isEnabled = false
        table.tableHeader.isVisible = false
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 2)
        
        val scrollPane = JBScrollPane(table)
        scrollPane.preferredSize = Dimension(400, 120)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        val copyButton = JButton("Copy", AllIcons.Actions.Copy)
        copyButton.addActionListener {
            val text = detailsData.joinToString("\n") { "${it[0]}: ${it[1]}" }
            CopyPasteManager.getInstance().setContents(StringSelection(text))
        }
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.add(copyButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
    
    private fun createFieldsPanel(fields: Map<String, String>): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder("Parsed Fields")
        
        val fieldsData = fields.map { arrayOf(it.key, it.value) }.toTypedArray()
        
        val table = JTable(fieldsData, arrayOf("Field", "Value"))
        table.isEnabled = false
        table.tableHeader.isVisible = false
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 2)
        
        val scrollPane = JBScrollPane(table)
        scrollPane.preferredSize = Dimension(400, Math.min(120, fields.size * 20 + 20))
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    private fun createRawPanel(raw: String): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder("Raw Log Line")
        
        val textArea = JTextArea(raw)
        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        textArea.border = JBUI.Borders.empty(8)
        
        val scrollPane = JBScrollPane(textArea)
        scrollPane.preferredSize = Dimension(400, 80)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        val copyButton = JButton("Copy", AllIcons.Actions.Copy)
        copyButton.addActionListener {
            CopyPasteManager.getInstance().setContents(StringSelection(raw))
        }
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
        buttonPanel.add(copyButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        
        return panel
    }
}