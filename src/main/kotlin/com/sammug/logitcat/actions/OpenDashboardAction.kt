package com.sammug.logitcat.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.sammug.logitcat.LogitCatSettings

class OpenDashboardAction : AnAction("Open Dashboard", "Open LogitCat dashboard in browser", AllIcons.Ide.External_link_arrow) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val settings = LogitCatSettings.getInstance()
        val url = "http://localhost:${settings.dashboardPort}"
        BrowserUtil.browse(url)
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
    }
}