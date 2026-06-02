package com.aichat.workbench.provider

import com.aichat.workbench.domain.model.ProviderAuthMode
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryTest {
    @Test
    fun returnsRegisteredProviderByType() {
        val provider = ProviderRegistryTestChatProvider()
        val registry = ProviderRegistry().apply {
            register("custom", provider)
        }

        assertSame(provider, registry.get("custom"))
        assertEquals(setOf("custom"), registry.registeredTypes())
        assertTrue(registry.isRegistered(ProviderType("custom")))
    }

    @Test
    fun normalizesLegacyProviderTypeNamesWhenRegistering() {
        val provider = ProviderRegistryTestChatProvider()
        val registry = ProviderRegistry().apply {
            register("OpenAI", provider)
        }

        assertSame(provider, registry.get(ProviderType.OpenAI.value))
        assertEquals(setOf(ProviderType.OpenAI.value), registry.registeredTypes())
    }

    @Test
    fun returnsBuiltInProviderDescriptors() {
        val openAi = ProviderRegistry.builtInDescriptor(ProviderType.OpenAI)
        val ollama = ProviderRegistry.builtInDescriptor(ProviderType.Ollama)

        assertEquals("OpenAI", openAi?.displayName)
        assertEquals("https://api.openai.com/v1", openAi?.defaultBaseUrl)
        assertEquals(ProviderAuthMode.ApiKey, openAi?.authMode)
        assertEquals("Ollama", ollama?.displayName)
        assertEquals(ProviderAuthMode.None, ollama?.authMode)
        assertEquals("http://10.0.2.2:11434", ollama?.defaultBaseUrl)
        assertEquals("/api/tags", ollama?.modelDiscovery?.path)
    }

    @Test
    fun listsOnlyBuiltInProvidersWithChatImplementationsForSelection() {
        val supportedTypes = ProviderRegistry.supportedBuiltInChatDescriptors().map { it.type }

        assertEquals(
            listOf(
                ProviderType.OpenAI,
                ProviderType.OpenAICompatible,
                ProviderType.OpenRouter,
                ProviderType.Ollama,
            ),
            supportedTypes,
        )
        assertTrue(ProviderRegistry.isSupportedBuiltInChatProvider(ProviderType.OpenAI))
        assertFalse(ProviderRegistry.isSupportedBuiltInChatProvider(ProviderType.Anthropic))
        assertFalse(ProviderRegistry.isSupportedBuiltInChatProvider(ProviderType.Gemini))
    }

    @Test
    fun fallsBackToCustomDescriptorForUnknownType() {
        val descriptor = ProviderRegistry().descriptor(ProviderType("custom"))

        assertEquals("custom", descriptor.displayName)
        assertEquals(ProviderAuthMode.ApiKey, descriptor.authMode)
        assertEquals("/models", descriptor.modelDiscovery?.path)
    }

    @Test(expected = IllegalStateException::class)
    fun throwsForUnknownProviderType() {
        ProviderRegistry().get("missing")
    }
}

private class ProviderRegistryTestChatProvider : ChatProvider {
    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse =
        ProviderTextResponse("")

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> =
        emptyFlow()
}
