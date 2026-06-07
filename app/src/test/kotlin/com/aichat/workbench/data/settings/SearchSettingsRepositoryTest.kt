package com.aichat.workbench.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.tool.search.SearchProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SearchSettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var preferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("search_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun saveSettingsStoresApiKeyInSecretStore() = runTest {
        val secretStore = FakeSecretStore()
        val repository = SearchSettingsRepository(context, secretStore)

        repository.saveSettings(
            enabled = true,
            provider = SearchProvider.Tavily,
            baseUrl = " https://api.tavily.com/ ",
            apiKey = " search-key ",
            maxResults = 7,
            searchDepth = "advanced",
            topic = "news",
        )

        val settings = repository.currentSettings()

        assertTrue(settings.enabled)
        assertEquals("https://api.tavily.com", settings.baseUrl)
        assertEquals("search-key", settings.apiKey)
        assertEquals(7, settings.maxResults)
        assertEquals("advanced", settings.searchDepth)
        assertEquals("news", settings.topic)
        assertTrue(preferences.contains("api_key_ref"))
        assertEquals(listOf("search-key"), secretStore.values.values.toList())
    }

    @Test
    fun observeSettingsMarksSavedApiKeyWithoutExposingPlaintext() = runTest {
        val secretStore = FakeSecretStore()
        val repository = SearchSettingsRepository(context, secretStore)

        repository.saveSettings(
            enabled = true,
            provider = SearchProvider.Tavily,
            baseUrl = "https://api.tavily.com",
            apiKey = "search-key",
            maxResults = 5,
            searchDepth = "basic",
            topic = "general",
        )

        val observed = repository.observeSettings().value

        assertTrue(observed.hasApiKey)
        assertEquals("", observed.apiKey)
        assertEquals("search-key", repository.currentSettings().apiKey)
        assertEquals("", repository.observeSettings().value.apiKey)
    }

    @Test
    fun saveSettingsWithBlankApiKeyDeletesStoredSecret() = runTest {
        val secretStore = FakeSecretStore()
        val repository = SearchSettingsRepository(context, secretStore)

        repository.saveSettings(
            enabled = true,
            provider = SearchProvider.Tavily,
            baseUrl = "https://api.tavily.com",
            apiKey = "search-key",
            maxResults = 5,
            searchDepth = "basic",
            topic = "general",
        )
        repository.saveSettings(
            enabled = true,
            provider = SearchProvider.Tavily,
            baseUrl = "https://api.tavily.com",
            apiKey = " ",
            maxResults = 5,
            searchDepth = "basic",
            topic = "general",
        )

        val settings = repository.currentSettings()

        assertEquals("", settings.apiKey)
        assertFalse(settings.hasApiKey)
        assertFalse(preferences.contains("api_key_ref"))
        assertTrue(secretStore.values.isEmpty())
    }

    @Test
    fun saveSettingsKeepsNonSensitiveFieldsVisibleWhenSecretStoreFails() = runTest {
        val repository = SearchSettingsRepository(context, FailingSecretStore())

        repository.saveSettings(
            enabled = true,
            provider = SearchProvider.Tavily,
            baseUrl = "https://api.tavily.com",
            apiKey = "search-key",
            maxResults = 9,
            searchDepth = "advanced",
            topic = "news",
        )

        val observed = repository.observeSettings().value

        assertTrue(observed.enabled)
        assertEquals("https://api.tavily.com", observed.baseUrl)
        assertEquals(9, observed.maxResults)
        assertEquals("advanced", observed.searchDepth)
        assertEquals("news", observed.topic)
        assertFalse(observed.hasApiKey)
        assertEquals("", observed.apiKey)
    }

    private class FakeSecretStore : SecretStore {
        val values = mutableMapOf<String, String>()

        override suspend fun putSecret(ref: String, value: String) {
            values[ref] = value
        }

        override suspend fun getSecret(ref: String): String? =
            values[ref]

        override suspend fun deleteSecret(ref: String) {
            values.remove(ref)
        }
    }

    private class FailingSecretStore : SecretStore {
        override suspend fun putSecret(ref: String, value: String) {
            error("secret store unavailable")
        }

        override suspend fun getSecret(ref: String): String? {
            error("secret store unavailable")
        }

        override suspend fun deleteSecret(ref: String) {
            error("secret store unavailable")
        }
    }
}
