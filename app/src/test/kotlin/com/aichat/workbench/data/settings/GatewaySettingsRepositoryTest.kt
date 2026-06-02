package com.aichat.workbench.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.data.crypto.SecretStore
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
class GatewaySettingsRepositoryTest {
    private lateinit var context: Context
    private lateinit var preferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun saveSettingsStoresApiTokenInSecretStore() = runTest {
        val secretStore = FakeSecretStore()
        val repository = GatewaySettingsRepository(context, secretStore)

        repository.saveSettings(
            enabled = true,
            baseUrl = " http://127.0.0.1:8080/ ",
            apiToken = " token-1 ",
        )

        val settings = repository.currentSettings()

        assertTrue(settings.enabled)
        assertEquals("http://127.0.0.1:8080", settings.baseUrl)
        assertEquals("token-1", settings.apiToken)
        assertFalse(preferences.contains("api_token"))
        assertTrue(preferences.contains("api_token_ref"))
        assertEquals(listOf("token-1"), secretStore.values.values.toList())
    }

    @Test
    fun currentSettingsMigratesLegacyPlaintextApiToken() = runTest {
        preferences.edit()
            .putBoolean("enabled", true)
            .putString("base_url", "http://127.0.0.1:8080")
            .putString("api_token", " legacy-token ")
            .commit()
        val secretStore = FakeSecretStore()
        val repository = GatewaySettingsRepository(context, secretStore)

        val settings = repository.currentSettings()

        assertEquals("legacy-token", settings.apiToken)
        assertFalse(preferences.contains("api_token"))
        assertTrue(preferences.contains("api_token_ref"))
        assertEquals(listOf("legacy-token"), secretStore.values.values.toList())
    }

    @Test
    fun saveSettingsWithBlankApiTokenDeletesStoredSecret() = runTest {
        val secretStore = FakeSecretStore()
        val repository = GatewaySettingsRepository(context, secretStore)

        repository.saveSettings(
            enabled = true,
            baseUrl = "http://127.0.0.1:8080",
            apiToken = "token-1",
        )
        repository.saveSettings(
            enabled = true,
            baseUrl = "http://127.0.0.1:8080",
            apiToken = " ",
        )

        val settings = repository.currentSettings()

        assertEquals("", settings.apiToken)
        assertFalse(preferences.contains("api_token_ref"))
        assertTrue(secretStore.values.isEmpty())
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
}
