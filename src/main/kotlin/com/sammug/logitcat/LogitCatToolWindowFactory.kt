package com.sammug.logitcat

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class LogitCatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // Tab 1: Alerts (rule-matched)
        val alertsPanel = AlertsPanel(project)
        toolWindow.contentManager.addContent(
            contentFactory.createContent(alertsPanel, "Alerts", false)
        )

        // Tab 2: Logs (all lines with filters)
        val logPanel = LogPanel(project)
        toolWindow.contentManager.addContent(
            contentFactory.createContent(logPanel, "Logcat", false)
        )
    }
}