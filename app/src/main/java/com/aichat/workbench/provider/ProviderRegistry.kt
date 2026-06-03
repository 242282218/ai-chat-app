package com.aichat.workbench.provider

import com.aichat.workbench.domain.model.ProviderAuthMode
import com.aichat.workbench.domain.model.ProviderCapabilities
import com.aichat.workbench.domain.model.ProviderDescriptor
import com.aichat.workbench.domain.model.ProviderModelDiscovery
import com.aichat.workbench.domain.model.ProviderModelDiscoveryFormat
import com.aichat.workbench.domain.model.ProviderProtocol
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.api.ChatProvider

class ProviderRegistry {
    private val providers = mutableMapOf<String, ChatProvider>()
    private val descriptors = mutableMapOf<String, ProviderDescriptor>()

    fun register(type: String, provider: ChatProvider) {
        val providerType = ProviderType.fromStorage(type)
        providers[providerType.value] = provider
        descriptors[providerType.value] = builtInDescriptor(providerType) ?: customDescriptor(providerType)
    }

    fun register(descriptor: ProviderDescriptor, provider: ChatProvider) {
        providers[descriptor.type.value] = provider
        descriptors[descriptor.type.value] = descriptor
    }

    fun get(type: String): ChatProvider {
        val providerType = ProviderType.fromStorage(type)
        return providers[providerType.value] ?: error("No ChatProvider registered for type: $type")
    }

    fun descriptor(type: ProviderType): ProviderDescriptor =
        descriptors[type.value] ?: builtInDescriptor(type) ?: customDescriptor(type)

    fun descriptors(): List<ProviderDescriptor> =
        descriptors.values.sortedBy { it.displayName }

    fun registeredTypes(): Set<String> = providers.keys.toSet()

    fun isRegistered(type: ProviderType): Boolean =
        providers.containsKey(type.value)

    private fun customDescriptor(type: ProviderType): ProviderDescriptor =
        ProviderDescriptor(
            type = type,
            displayName = type.value,
            authMode = ProviderAuthMode.ApiKey,
            supportsCustomBaseUrl = true,
            defaultBaseUrl = null,
            modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
            protocol = ProviderProtocol.OpenAiChatCompletions,
            capabilities = ProviderCapabilities(
                text = true,
                vision = false,
                imageGeneration = false,
                toolCalling = false,
                structuredOutput = false,
                longContext = false,
            ),
        )

    companion object {
        fun builtInDescriptor(type: ProviderType): ProviderDescriptor? =
            builtInDescriptorList.firstOrNull { it.type == type }

        fun builtInDescriptors(): List<ProviderDescriptor> = builtInDescriptorList

        fun supportedBuiltInChatDescriptors(): List<ProviderDescriptor> =
            builtInDescriptorList.filter { it.type in supportedBuiltInChatProviderTypes }

        fun isSupportedBuiltInChatProvider(type: ProviderType): Boolean =
            type in supportedBuiltInChatProviderTypes

        private val supportedBuiltInChatProviderTypes = setOf(
            ProviderType.OpenAI,
            ProviderType.OpenAICompatible,
            ProviderType.NewApi,
            ProviderType.Sub2Api,
            ProviderType.Custom,
            ProviderType.OpenRouter,
            ProviderType.Ollama,
        )

        private val openAiCapabilities = ProviderCapabilities(
            text = true,
            vision = true,
            imageGeneration = true,
            toolCalling = true,
            structuredOutput = true,
            longContext = true,
        )

        private val builtInDescriptorList = listOf(
            ProviderDescriptor(
                type = ProviderType.OpenAI,
                displayName = "OpenAI",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = false,
                defaultBaseUrl = "https://api.openai.com/v1",
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                protocol = ProviderProtocol.OpenAiResponses,
                capabilities = openAiCapabilities,
            ),
            ProviderDescriptor(
                type = ProviderType.OpenAICompatible,
                displayName = "兼容 OpenAI",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = null,
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                protocol = ProviderProtocol.OpenAiChatCompletions,
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = false,
                    toolCalling = true,
                    structuredOutput = true,
                    longContext = true,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.NewApi,
                displayName = "New API",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = null,
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                protocol = ProviderProtocol.OpenAiChatCompletions,
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = true,
                    toolCalling = true,
                    structuredOutput = true,
                    longContext = true,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.Sub2Api,
                displayName = "Sub2 API",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = null,
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                protocol = ProviderProtocol.OpenAiChatCompletions,
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = true,
                    toolCalling = true,
                    structuredOutput = true,
                    longContext = true,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.Custom,
                displayName = "自定义兼容接口",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = null,
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                protocol = ProviderProtocol.OpenAiChatCompletions,
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = true,
                    toolCalling = true,
                    structuredOutput = true,
                    longContext = true,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.OpenRouter,
                displayName = "OpenRouter",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = "https://openrouter.ai/api/v1",
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                protocol = ProviderProtocol.OpenAiChatCompletions,
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = false,
                    toolCalling = true,
                    structuredOutput = true,
                    longContext = true,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.Anthropic,
                displayName = "Anthropic",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = "https://api.anthropic.com/v1",
                modelDiscovery = null,
                protocol = ProviderProtocol.AnthropicMessages,
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = false,
                    toolCalling = true,
                    structuredOutput = true,
                    longContext = true,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.Gemini,
                displayName = "Gemini",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta",
                modelDiscovery = null,
                protocol = ProviderProtocol.GeminiGenerateContent,
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = false,
                    toolCalling = true,
                    structuredOutput = true,
                    longContext = true,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.Ollama,
                displayName = "Ollama",
                authMode = ProviderAuthMode.None,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = "http://10.0.2.2:11434",
                modelDiscovery = ProviderModelDiscovery("/api/tags", ProviderModelDiscoveryFormat.OllamaTags),
                protocol = ProviderProtocol.OllamaOpenAiCompatible,
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = false,
                    toolCalling = false,
                    structuredOutput = false,
                    longContext = true,
                ),
            ),
        )
    }
}
