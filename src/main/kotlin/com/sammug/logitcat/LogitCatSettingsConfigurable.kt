package com.sammug.logitcat

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class LogitCatSettingsConfigurable : Configurable {
    private val executablePathField = TextFieldWithBrowseButton()
    private val configPathField = TextFieldWithBrowseButton()
    private val dashboardPortField = JBTextField()
    private val autoStartCheckBox = JBCheckBox("Auto-start LogitCat with project")
    private val maxAlertsField = JBTextField()

    init {
        executablePathField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )
        )
        
        configPathField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )
        )
        
        dashboardPortField.columns = 10
        maxAlertsField.columns = 10
    }

    override fun getDisplayName(): String = "LogitCat"

    override fun createComponent(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Executable path:"), executablePathField, 1, false)
            .addTooltip("Path to the LogitCat executable")
            .addLabeledComponent(JBLabel("Config path:"), configPathField, 1, false)
            .addTooltip("Path to the LogitCat configuration file (optional)")
            .addLabeledComponent(JBLabel("Dashboard port:"), dashboardPortField, 1, false)
            .addTooltip("Port for LogitCat dashboard (default: 9090)")
            .addLabeledComponent(JBLabel("Max alerts:"), maxAlertsField, 1, false)
            .addTooltip("Maximum number of alerts to keep (default: 200)")
            .addComponent(autoStartCheckBox, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            
        panel.border = JBUI.Borders.empty(16)
        return panel
    }

    override fun isModified(): Boolean {
        val settings = LogitCatSettings.getInstance()
        return executablePathField.text != settings.executablePath ||
                configPathField.text != settings.configPath ||
                dashboardPortField.text != settings.dashboardPort.toString() ||
                autoStartCheckBox.isSelected != settings.autoStart ||
                maxAlertsField.text != settings.maxAlerts.toString()
    }

    override fun apply() {
        val settings = LogitCatSettings.getInstance()
        settings.executablePath = executablePathField.text
        settings.configPath = configPathField.text
        settings.dashboardPort = dashboardPortField.text.toIntOrNull() ?: 9090
        settings.autoStart = autoStartCheckBox.isSelected
        settings.maxAlerts = maxAlertsField.text.toIntOrNull() ?: 200
    }

    override fun reset() {
        val settings = LogitCatSettings.getInstance()
        executablePathField.text = settings.executablePath
        configPathField.text = settings.configPath
        dashboardPortField.text = settings.dashboardPort.toString()
        autoStartCheckBox.isSelected = settings.autoStart
        maxAlertsField.text = settings.maxAlerts.toString()
    }
}