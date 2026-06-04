package com.aichat.workbench.data.settings

import android.content.Context
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.tool.search.SearchConfig
import com.aichat.workbench.tool.search.SearchProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SearchSettings(
    val enabled: Boolean,
    val provider: SearchProvider,
    val baseUrl: String,
    val apiKey: String,
    val maxResults: Int,
    val searchDepth: String,
    val topic: String,
)

class SearchSettingsRepository(
    context: Context,
    private val secretStore: SecretStore,
) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settings = MutableStateFlow(readSettingsWithoutKey())

    fun observeSettings(): StateFlow<SearchSettings> =
        settings.asStateFlow()

    suspend fun loadSettings() {
        settings.value = readSettings()
    }

    suspend fun currentSettings(): SearchSettings =
        readSettings().also { settings.value = it }

    suspend fun saveSettings(
        enabled: Boolean,
        provider: SearchProvider,
        baseUrl: String,
        apiKey: String,
        maxResults: Int,
        searchDepth: String,
        topic: String,
    ) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            secretStore.deleteSecret(SEARCH_API_KEY_REF)
        } else {
            secretStore.putSecret(SEARCH_API_KEY_REF, trimmedKey)
        }
        preferences.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_PROVIDER, provider.name)
            .putString(KEY_BASE_URL, baseUrl.normalizedBaseUrl())
            .putInt(KEY_MAX_RESULTS, maxResults.coerceIn(MIN_MAX_RESULTS, MAX_MAX_RESULTS))
            .putString(KEY_SEARCH_DEPTH, searchDepth.normalizedSearchDepth())
            .putString(KEY_TOPIC, topic.normalizedTopic())
            .apply {
                if (trimmedKey.isBlank()) {
                    remove(KEY_API_KEY_REF)
                } else {
                    putString(KEY_API_KEY_REF, SEARCH_API_KEY_REF)
                }
            }
            .apply()
        settings.value = readSettings()
    }

    private suspend fun readSettings(): SearchSettings =
        readSettingsWithoutKey().copy(apiKey = readApiKey())

    private fun readSettingsWithoutKey(): SearchSettings =
        SearchSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, false),
            provider = preferences.getString(KEY_PROVIDER, null).toSearchProvider(),
            baseUrl = preferences.getString(KEY_BASE_URL, DEFAULT_TAVILY_BASE_URL)
                .orEmpty()
                .normalizedBaseUrl(),
            apiKey = "",
            maxResults = preferences.getInt(KEY_MAX_RESULTS, DEFAULT_MAX_RESULTS)
                .coerceIn(MIN_MAX_RESULTS, MAX_MAX_RESULTS),
            searchDepth = preferences.getString(KEY_SEARCH_DEPTH, DEFAULT_SEARCH_DEPTH)
                .orEmpty()
                .normalizedSearchDepth(),
            topic = preferences.getString(KEY_TOPIC, DEFAULT_TOPIC)
                .orEmpty()
                .normalizedTopic(),
        )

    private suspend fun readApiKey(): String {
        val keyRef = preferences.getString(KEY_API_KEY_REF, null)
        val key = keyRef?.let { ref -> secretStore.getSecret(ref)?.trim() }.orEmpty()
        if (key.isBlank() && keyRef != null) {
            preferences.edit().remove(KEY_API_KEY_REF).apply()
        }
        return key
    }

    private fun String?.toSearchProvider(): SearchProvider =
        SearchProvider.values()
            .firstOrNull { it.name.equals(this.orEmpty(), ignoreCase = true) }
            ?: SearchProvider.Tavily

    private companion object {
        const val PREFS_NAME = "search_settings"
        const val KEY_ENABLED = "enabled"
        const val KEY_PROVIDER = "provider"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY_REF = "api_key_ref"
        const val KEY_MAX_RESULTS = "max_results"
        const val KEY_SEARCH_DEPTH = "search_depth"
        const val KEY_TOPIC = "topic"
        const val SEARCH_API_KEY_REF = "local_search_api_key"
        const val DEFAULT_TAVILY_BASE_URL = "https://api.tavily.com"
        const val DEFAULT_MAX_RESULTS = 5
        const val DEFAULT_SEARCH_DEPTH = "basic"
        const val DEFAULT_TOPIC = "general"
        const val MIN_MAX_RESULTS = 1
        const val MAX_MAX_RESULTS = 20
    }
}

fun String.normalizedBaseUrl(): String =
    trim().trimEnd('/')

fun String.normalizedSearchDepth(): String =
    trim().lowercase().takeIf { it in setOf("basic", "advanced", "fast", "ultra-fast") } ?: "basic"

fun String.normalizedTopic(): String =
    trim().lowercase().takeIf { it in setOf("general", "news", "finance") } ?: "general"

fun SearchSettings.toSearchConfig(): SearchConfig =
    SearchConfig(
        enabled = enabled,
        provider = provider,
        baseUrl = baseUrl,
        apiKey = apiKey,
        maxResults = maxResults,
        searchDepth = searchDepth,
        topic = topic,
    )
