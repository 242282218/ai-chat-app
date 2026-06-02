package com.aichat.workbench.data.settings

import android.content.Context
import com.aichat.workbench.data.crypto.SecretStore
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
    private val secretStore: SecretStore,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settings = MutableStateFlow(readSettingsWithoutToken())

    fun observeSettings(): StateFlow<GatewaySettings> =
        settings.asStateFlow()

    suspend fun loadSettings() {
        settings.value = readSettings()
    }

    suspend fun currentSettings(): GatewaySettings =
        readSettings().also { settings.value = it }

    suspend fun saveSettings(enabled: Boolean, baseUrl: String, apiToken: String) {
        val trimmedToken = apiToken.trim()
        if (trimmedToken.isBlank()) {
            secretStore.deleteSecret(GATEWAY_API_TOKEN_REF)
        } else {
            secretStore.putSecret(GATEWAY_API_TOKEN_REF, trimmedToken)
        }
        preferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_BASE_URL, baseUrl.normalizedGatewayBaseUrl())
            .remove(KEY_API_TOKEN)
            .apply {
                if (trimmedToken.isBlank()) {
                    remove(KEY_API_TOKEN_REF)
                } else {
                    putString(KEY_API_TOKEN_REF, GATEWAY_API_TOKEN_REF)
                }
            }
            .apply()
        settings.value = readSettings()
    }

    private suspend fun readSettings(): GatewaySettings =
        readSettingsWithoutToken().copy(apiToken = readApiToken())

    private fun readSettingsWithoutToken(): GatewaySettings =
        GatewaySettings(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            baseUrl = preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty().normalizedGatewayBaseUrl(),
            apiToken = "",
        )

    private suspend fun readApiToken(): String {
        val tokenRef = preferences.getString(KEY_API_TOKEN_REF, null)
        tokenRef?.let { ref ->
            secretStore.getSecret(ref)?.takeIf { it.isNotBlank() }?.let { token ->
                clearLegacyPlaintextToken()
                return token
            }
        }

        val legacyToken = preferences.getString(KEY_API_TOKEN, null)?.trim().orEmpty()
        if (legacyToken.isBlank()) {
            if (tokenRef != null) {
                preferences.edit().remove(KEY_API_TOKEN_REF).apply()
            }
            return ""
        }

        secretStore.putSecret(GATEWAY_API_TOKEN_REF, legacyToken)
        preferences.edit()
            .putString(KEY_API_TOKEN_REF, GATEWAY_API_TOKEN_REF)
            .remove(KEY_API_TOKEN)
            .apply()
        return legacyToken
    }

    private fun clearLegacyPlaintextToken() {
        if (preferences.contains(KEY_API_TOKEN)) {
            preferences.edit().remove(KEY_API_TOKEN).apply()
        }
    }

    private fun String.normalizedGatewayBaseUrl(): String =
        trim().trimEnd('/')

    private companion object {
        const val PREFS_NAME = "gateway_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_TOKEN = "api_token"
        const val KEY_API_TOKEN_REF = "api_token_ref"
        const val GATEWAY_API_TOKEN_REF = "gateway_api_token"
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8080"
    }
}
