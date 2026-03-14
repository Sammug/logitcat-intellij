package com.sammug.logitcat

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Alert(
    val time: String,
    val severity: String,
    val rule: String,
    val level: String,
    val source: String,
    val message: String,
    val format: String,
    val raw: String,
    val fields: Map<String, String> = emptyMap()
) {
    fun getFormattedTime(): String {
        return try {
            val dateTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        } catch (e: Exception) {
            time
        }
    }

    companion object {
        private val gson = Gson()
        
        fun fromJson(json: String): Alert? {
            return try {
                gson.fromJson(json, Alert::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}