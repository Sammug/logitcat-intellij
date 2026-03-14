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
    private val executablePathField    = TextFieldWithBrowseButton()
    private val configPathField        = TextFieldWithBrowseButton()
    private val dashboardPortField     = JBTextField()
    private val autoStartCheckBox      = JBCheckBox("Auto-connect to daemon when project opens")
    private val maxAlertsField         = JBTextField()
    private val autoStartOnRunCheckBox = JBCheckBox("Auto-capture logcat when running Android app")
    private val androidConfigPathField = TextFieldWithBrowseButton()
    private val adbPathField           = TextFieldWithBrowseButton()

    init {
        executablePathField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )
        )
        
        configPathField.addBrowseFolderListener(
            TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor())
        )
        androidConfigPathField.addBrowseFolderListener(
            TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor())
        )
        adbPathField.addBrowseFolderListener(
            TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor())
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
            .addSeparator()
            .addComponent(autoStartOnRunCheckBox, 1)
            .addTooltip("Pipes adb logcat through LogitCat automatically when you hit Run")
            .addLabeledComponent(JBLabel("Android config:"), androidConfigPathField, 1, false)
            .addTooltip("Path to android.ini rules file (auto-detected if blank)")
            .addLabeledComponent(JBLabel("adb path:"), adbPathField, 1, false)
            .addTooltip("Path to adb binary (auto-detected from ANDROID_HOME if blank)")
            .addComponentFillVertically(JPanel(), 0)
            .panel
            
        panel.border = JBUI.Borders.empty(16)
        return panel
    }

    override fun isModified(): Boolean {
        val s = LogitCatSettings.getInstance()
        return executablePathField.text    != s.executablePath    ||
               configPathField.text        != s.configPath        ||
               dashboardPortField.text     != s.dashboardPort.toString() ||
               autoStartCheckBox.isSelected != s.autoStart        ||
               maxAlertsField.text         != s.maxAlerts.toString() ||
               autoStartOnRunCheckBox.isSelected != s.autoStartOnRun ||
               androidConfigPathField.text != s.androidConfigPath  ||
               adbPathField.text           != s.adbPath
    }

    override fun apply() {
        val s = LogitCatSettings.getInstance()
        s.executablePath    = executablePathField.text
        s.configPath        = configPathField.text
        s.dashboardPort     = dashboardPortField.text.toIntOrNull() ?: 9090
        s.autoStart         = autoStartCheckBox.isSelected
        s.maxAlerts         = maxAlertsField.text.toIntOrNull() ?: 200
        s.autoStartOnRun    = autoStartOnRunCheckBox.isSelected
        s.androidConfigPath = androidConfigPathField.text
        s.adbPath           = adbPathField.text
    }

    override fun reset() {
        val s = LogitCatSettings.getInstance()
        executablePathField.text        = s.executablePath
        configPathField.text            = s.configPath
        dashboardPortField.text         = s.dashboardPort.toString()
        autoStartCheckBox.isSelected    = s.autoStart
        maxAlertsField.text             = s.maxAlerts.toString()
        autoStartOnRunCheckBox.isSelected = s.autoStartOnRun
        androidConfigPathField.text     = s.androidConfigPath
        adbPathField.text               = s.adbPath
    }
}