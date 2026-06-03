package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderEndpointTest {
    @Test
    fun openAiApiBaseUrl_appendsVersionForOpenAiCompatibleTypes() {
        val types = listOf(
            ProviderType.OpenAI,
            ProviderType.OpenAICompatible,
            ProviderType.NewApi,
            ProviderType.Sub2Api,
            ProviderType.Custom,
            ProviderType.OpenRouter,
        )

        types.forEach { type ->
            assertEquals(
                "https://zzshu.cc/v1",
                provider(type = type, baseUrl = "https://zzshu.cc").openAiApiBaseUrl(),
            )
        }
    }

    @Test
    fun openAiApiBaseUrl_keepsExistingVersionSuffix() {
        assertEquals(
            "https://zzshu.cc/v1",
            provider(type = ProviderType.Custom, baseUrl = "https://zzshu.cc/v1/").openAiApiBaseUrl(),
        )
    }

    @Test
    fun modelDiscoveryBaseUrl_removesOllamaOpenAiVersionSuffix() {
        assertEquals(
            "http://10.0.2.2:11434",
            provider(type = ProviderType.Ollama, baseUrl = "http://10.0.2.2:11434/v1").modelDiscoveryBaseUrl(),
        )
    }

    private fun provider(type: ProviderType, baseUrl: String): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = "Provider",
            type = type,
            baseUrl = baseUrl,
            apiKeyRef = null,
            headers = emptyMap(),
            models = emptyList(),
            defaultModel = null,
            enabled = true,
        )
}
