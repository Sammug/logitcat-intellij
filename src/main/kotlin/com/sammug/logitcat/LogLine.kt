package com.sammug.logitcat

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class LogLine(
    val time: String = "",
    val level: String = "",
    val source: String = "",
    val message: String = "",
    val format: String = "",
    val raw: String = "",
    val tag: String = "",
    @SerializedName("fields") val fields: Map<String, String> = emptyMap()
) {
    fun levelChar(): Char = when (level.lowercase()) {
        "verbose", "v"   -> 'V'
        "debug", "d"     -> 'D'
        "info", "i"      -> 'I'
        "warn","warning","w" -> 'W'
        "error","e"      -> 'E'
        "fatal","f","assert","a" -> 'F'
        else -> '?'
    }

    companion object {
        private val gson = Gson()
        fun fromJson(json: String): LogLine? = try {
            gson.fromJson(json, LogLine::class.java)
        } catch (_: Exception) { null }
    }
}
