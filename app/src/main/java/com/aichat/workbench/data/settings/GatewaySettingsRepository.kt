package com.aichat.workbench.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GatewaySettings(
    val enabled: Boolean,
    val baseUrl: String,
    val apiToken: String,
)

class GatewaySettingsRepository(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settings = MutableStateFlow(readSettings())

    fun observeSettings(): StateFlow<GatewaySettings> =
        settings.asStateFlow()

    fun currentSettings(): GatewaySettings =
        settings.value

    fun saveSettings(enabled: Boolean, baseUrl: String, apiToken: String) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(KEY_API_TOKEN, apiToken.trim())
            .apply()
        settings.value = readSettings()
    }

    private fun readSettings(): GatewaySettings =
        GatewaySettings(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            baseUrl = preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty(),
            apiToken = preferences.getString(KEY_API_TOKEN, "").orEmpty(),
        )

    private companion object {
        const val PREFS_NAME = "gateway_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_TOKEN = "api_token"
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
    }
}
