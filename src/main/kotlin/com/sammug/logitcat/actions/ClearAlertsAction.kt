package com.sammug.logitcat.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.sammug.logitcat.LogitCatService

class ClearAlertsAction : AnAction("Clear Alerts", "Clear all alerts", AllIcons.Actions.GC) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        LogitCatService.getInstance(project).clearAlerts()
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
}