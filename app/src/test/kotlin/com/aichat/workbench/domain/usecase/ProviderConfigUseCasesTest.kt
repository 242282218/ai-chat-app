package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class ProviderConfigUseCasesTest {
    @Test
    fun saveProviderAllowsDisabledProviderWithInvalidBaseUrl() = runTest {
        val repository = RecordingProviderConfigRepository()
        val saveProvider = SaveProviderConfigUseCase(repository)
        val provider = provider(enabled = false, baseUrl = " broken.local/ ")

        saveProvider(provider, plaintextApiKey = null, allowInsecureHttp = false)

        assertEquals("broken.local", repository.savedProvider?.baseUrl)
        assertEquals(false, repository.savedProvider?.enabled)
        assertNull(repository.savedApiKey)
    }

    @Test
    fun saveProviderRejectsEnabledProviderWithInvalidBaseUrl() = runTest {
        val repository = RecordingProviderConfigRepository()
        val saveProvider = SaveProviderConfigUseCase(repository)

        try {
            saveProvider(
                provider(enabled = true, baseUrl = "broken.local"),
                plaintextApiKey = null,
                allowInsecureHttp = false,
            )
            fail("Expected invalid enabled provider to be rejected.")
        } catch (error: IllegalArgumentException) {
            assertEquals("Provider base URL must be HTTPS unless HTTP is explicitly allowed.", error.message)
        }

        assertNull(repository.savedProvider)
    }

    private fun provider(
        enabled: Boolean,
        baseUrl: String,
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = " Test Provider ",
            type = ProviderType.OpenAICompatible,
            baseUrl = baseUrl,
            apiKeyRef = null,
            headers = emptyMap(),
            models = listOf(ModelConfig(" model-a ", " Model A ", capability = null)),
            defaultModel = " model-a ",
            enabled = enabled,
        )
}

private class RecordingProviderConfigRepository : ProviderConfigRepository {
    private val providers = MutableStateFlow<List<ProviderConfig>>(emptyList())
    var savedProvider: ProviderConfig? = null
    var savedApiKey: String? = null

    override fun observeProviders(): Flow<List<ProviderConfig>> =
        providers

    override suspend fun getProvider(id: ProviderId): ProviderConfig? =
        providers.value.firstOrNull { it.id == id }

    override suspend fun saveProvider(
        provider: ProviderConfig,
        plaintextApiKey: String?,
        preserveExistingApiKey: Boolean,
        deleteReplacedApiKey: Boolean,
    ) {
        savedProvider = provider
        savedApiKey = plaintextApiKey
        providers.value = listOf(provider)
    }

    override suspend fun getApiKey(providerId: ProviderId): String? =
        null

    override suspend fun deleteApiKeyRef(ref: String) = Unit

    override suspend fun deleteProvider(id: ProviderId) {
        providers.value = providers.value.filterNot { it.id == id }
    }
}
