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
        assertTrue(registry.isRegistered(ProviderType("custom")))
    }

    @Test
    fun normalizesLegacyProviderTypeNamesWhenRegistering() {
        val provider = ProviderRegistryTestChatProvider()
        val registry = ProviderRegistry().apply {
            register("OpenAI", provider)
        }

        assertSame(provider, registry.get(ProviderType.OpenAI.value))
        assertSame(provider, registry.get("OpenAI"))
        assertTrue(registry.isRegistered(ProviderType.OpenAI))
    }

    @Test
    fun returnsBuiltInProviderDescriptors() {
        val openAi = ProviderRegistry.builtInDescriptor(ProviderType.OpenAI)
        val ollama = ProviderRegistry.builtInDescriptor(ProviderType.Ollama)

        assertEquals("OpenAI", openAi?.displayName)
        assertEquals("https://api.openai.com/v1", openAi?.defaultBaseUrl)
        assertEquals(ProviderAuthMode.ApiKey, openAi?.authMode)
        assertEquals("New API", ProviderRegistry.builtInDescriptor(ProviderType.NewApi)?.displayName)
        assertTrue(ProviderRegistry.builtInDescriptor(ProviderType.NewApi)?.capabilities?.imageGeneration == true)
        assertEquals("Sub2 API", ProviderRegistry.builtInDescriptor(ProviderType.Sub2Api)?.displayName)
        assertEquals("自定义兼容接口", ProviderRegistry.builtInDescriptor(ProviderType.Custom)?.displayName)
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
                ProviderType.NewApi,
                ProviderType.Sub2Api,
                ProviderType.Custom,
                ProviderType.OpenRouter,
                ProviderType.Ollama,
            ),
            supportedTypes,
        )
        assertTrue(ProviderRegistry.isSupportedBuiltInChatProvider(ProviderType.OpenAI))
        assertTrue(ProviderRegistry.isSupportedBuiltInChatProvider(ProviderType.NewApi))
        assertTrue(ProviderRegistry.isSupportedBuiltInChatProvider(ProviderType.Sub2Api))
        assertTrue(ProviderRegistry.isSupportedBuiltInChatProvider(ProviderType.Custom))
        assertFalse(ProviderRegistry.isSupportedBuiltInChatProvider(ProviderType("legacy_vendor")))
    }

    @Test
    fun createsBuiltInProviderRegistryWithExpectedProviderBindings() {
        val openAi = ProviderRegistryTestChatProvider()
        val compatible = ProviderRegistryTestChatProvider()

        val registry = createBuiltInProviderRegistry(openAi, compatible)

        assertSame(openAi, registry.get(ProviderType.OpenAI.value))
        assertSame(compatible, registry.get(ProviderType.OpenAICompatible.value))
        assertSame(compatible, registry.get(ProviderType.NewApi.value))
        assertSame(compatible, registry.get(ProviderType.Sub2Api.value))
        assertSame(compatible, registry.get(ProviderType.Custom.value))
        assertSame(compatible, registry.get(ProviderType.OpenRouter.value))
        assertSame(compatible, registry.get(ProviderType.Ollama.value))
    }

    @Test
    fun fallsBackToCustomDescriptorForUnknownType() {
        val descriptor = ProviderRegistry().descriptor(ProviderType("custom_vendor"))

        assertEquals("custom_vendor", descriptor.displayName)
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
