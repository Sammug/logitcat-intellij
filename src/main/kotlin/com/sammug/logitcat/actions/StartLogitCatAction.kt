package com.sammug.logitcat.actions

import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.sammug.logitcat.LogitCatService
import com.sammug.logitcat.LogitCatSettings
import java.io.File

class StartLogitCatAction : AnAction("Start LogitCat", "Start LogitCat daemon", AllIcons.Actions.Execute) {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = LogitCatSettings.getInstance()
        
        if (settings.executablePath.isEmpty()) {
            showNotification(project, "LogitCat executable path not configured. Please check settings.", NotificationType.WARNING)
            return
        }
        
        if (!File(settings.executablePath).exists()) {
            showNotification(project, "LogitCat executable not found at: ${settings.executablePath}", NotificationType.ERROR)
            return
        }
        
        try {
            val command = mutableListOf(settings.executablePath)
            
            if (settings.configPath.isNotEmpty() && File(settings.configPath).exists()) {
                command.addAll(listOf("--config", settings.configPath))
            }
            
            if (settings.dashboardPort != 9090) {
                command.addAll(listOf("--port", settings.dashboardPort.toString()))
            }
            
            val processBuilder = ProcessBuilder(command)
            processBuilder.start()
            
            // Start watching for SSE events
            LogitCatService.getInstance(project).startWatching()
            
            showNotification(project, "LogitCat daemon started", NotificationType.INFORMATION)
            
        } catch (e: Exception) {
            showNotification(project, "Failed to start LogitCat: ${e.message}", NotificationType.ERROR)
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