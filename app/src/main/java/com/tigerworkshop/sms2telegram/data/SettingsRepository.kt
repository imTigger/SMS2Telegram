package com.tigerworkshop.sms2telegram.data

import android.content.Context
import androidx.core.content.edit

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSettings(token: String, chatId: String) {
        prefs.edit {
            putString(KEY_API_TOKEN, token.trim())
            putString(KEY_CHAT_ID, chatId.trim())
        }
    }

    fun loadSettings(): TelegramSettings? {
        val token = prefs.getString(KEY_API_TOKEN, null)?.takeIf { it.isNotBlank() }
        val chatId = prefs.getString(KEY_CHAT_ID, null)?.takeIf { it.isNotBlank() }
        return if (token != null && chatId != null) {
            TelegramSettings(token, chatId)
        } else {
            null
        }
    }

    fun isFirstLaunch(): Boolean {
        return prefs.getString(KEY_API_TOKEN, null).isNullOrBlank()
    }

    fun saveLastForwardStatus(status: String) {
        prefs.edit {
            putString(KEY_LAST_STATUS, status)
        }
    }

    fun loadLastForwardStatus(): String? = prefs.getString(KEY_LAST_STATUS, null)

    fun isForwardingEnabled(): Boolean = prefs.getBoolean(KEY_FORWARDING_ENABLED, true)

    fun setForwardingEnabled(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_FORWARDING_ENABLED, enabled)
        }
    }

    data class TelegramSettings(
        val apiToken: String,
        val chatId: String
    )

    companion object {
        private const val PREFS_NAME = "sms_forwarder_prefs"
        private const val KEY_API_TOKEN = "api_token"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_LAST_STATUS = "last_forward_status"
        private const val KEY_FORWARDING_ENABLED = "forwarding_enabled"
    }
}
