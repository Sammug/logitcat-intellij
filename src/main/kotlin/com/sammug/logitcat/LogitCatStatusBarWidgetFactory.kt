package com.sammug.logitcat

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

class LogitCatStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "LogitCatStatusWidget"
    
    override fun getDisplayName(): String = "LogitCat"
    
    override fun isAvailable(project: Project): Boolean = true
    
    override fun createWidget(project: Project): StatusBarWidget = LogitCatStatusWidget(project)
    
    override fun disposeWidget(widget: StatusBarWidget) {}
    
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    class LogitCatStatusWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation, LogitCatService.AlertListener {
        private val service = LogitCatService.getInstance(project)
        private var myStatusBar: StatusBar? = null
        
        init {
            service.addListener(this)
        }

        override fun ID(): String = "LogitCatStatusWidget"

        override fun install(statusBar: StatusBar) {
            myStatusBar = statusBar
            statusBar.updateWidget(ID())
        }

        override fun dispose() {
            service.removeListener(this)
            myStatusBar = null
        }

        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

        override fun getText(): String {
            return when {
                service.isConnected() -> {
                    val criticalCount = service.getCriticalCount()
                    if (criticalCount > 0) {
                        "🔴 LogitCat: $criticalCount alerts"
                    } else {
                        "⚡ LogitCat: watching"
                    }
                }
                else -> "○ LogitCat: offline"
            }
        }

        override fun getAlignment(): Float = 0.5f

        override fun getTooltipText(): String {
            val totalAlerts = service.getAlertCount()
            val criticalAlerts = service.getCriticalCount()
            return when {
                service.isConnected() -> "LogitCat: $totalAlerts alerts ($criticalAlerts critical) - Click to view"
                else -> "LogitCat: Not connected - Click to view"
            }
        }

        override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("LogitCat")
            toolWindow?.activate(null)
        }

        override fun onAlertAdded(alert: Alert) {
            myStatusBar?.updateWidget(ID())
        }

        override fun onConnectionChanged(connected: Boolean) {
            myStatusBar?.updateWidget(ID())
        }

        override fun onAlertsCleared() {
            myStatusBar?.updateWidget(ID())
        }
    }
}