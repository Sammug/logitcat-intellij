package com.sammug.logitcat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "LogitCatSettings",
    storages = [Storage("logitcat.xml")]
)
class LogitCatSettings : PersistentStateComponent<LogitCatSettings> {
    var executablePath: String = ""
    var configPath: String = ""
    var dashboardPort: Int = 9090
    var autoStart: Boolean = true
    var maxAlerts: Int = 200

    override fun getState(): LogitCatSettings = this

    override fun loadState(state: LogitCatSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): LogitCatSettings =
            ApplicationManager.getApplication().getService(LogitCatSettings::class.java)
    }
}