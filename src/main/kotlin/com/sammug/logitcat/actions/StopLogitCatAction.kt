package com.sammug.logitcat.actions

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.sammug.logitcat.LogitCatService

class StopLogitCatAction : AnAction("Stop LogitCat", "Stop LogitCat daemon", AllIcons.Actions.Suspend) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        try {
            // Stop watching for SSE events
            LogitCatService.getInstance(project).stopWatching()
            
            // Try to stop the daemon process (this is a simple approach)
            // In a real implementation, you might want to track the process PID
            val processBuilder = ProcessBuilder("pkill", "-f", "logitcat")
            processBuilder.start()
            
            showNotification(project, "LogitCat daemon stopped", NotificationType.INFORMATION)
            
        } catch (e: Exception) {
            showNotification(project, "Failed to stop LogitCat: ${e.message}", NotificationType.WARNING)
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
    }
    
    private fun showNotification(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("LogitCat")
            .createNotification(content, type)
            .notify(project)
    }
}