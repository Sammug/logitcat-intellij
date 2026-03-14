package com.sammug.logitcat

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class LogitCatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val alertsPanel = AlertsPanel(project)
        val content = ContentFactory.getInstance().createContent(alertsPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}