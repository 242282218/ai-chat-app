package com.aichat.workbench.provider

import com.aichat.workbench.domain.model.ProviderAuthMode
import com.aichat.workbench.domain.model.ProviderCapabilities
import com.aichat.workbench.domain.model.ProviderDescriptor
import com.aichat.workbench.domain.model.ProviderModelDiscovery
import com.aichat.workbench.domain.model.ProviderModelDiscoveryFormat
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.api.ChatProvider

const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
const val DEFAULT_OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
const val DEFAULT_OLLAMA_BASE_URL = "http://10.0.2.2:11434"

class ProviderRegistry {
    private val providers = java.util.concurrent.ConcurrentHashMap<String, ChatProvider>()
    private val descriptors = java.util.concurrent.ConcurrentHashMap<String, ProviderDescriptor>()

    fun register(type: String, provider: ChatProvider) {
        val providerType = ProviderType.fromStorage(type)
        require(!providers.containsKey(providerType.value)) {
            "ChatProvider already registered for type: ${providerType.value}"
        }
        providers[providerType.value] = provider
        descriptors[providerType.value] = builtInDescriptor(providerType) ?: customDescriptor(providerType)
    }

    fun register(descriptor: ProviderDescriptor, provider: ChatProvider) {
        require(!providers.containsKey(descriptor.type.value)) {
            "ChatProvider already registered for type: ${descriptor.type.value}"
        }
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
            capabilities = ProviderCapabilities(
                text = true,
                vision = false,
                imageGeneration = false,
            ),
        )

    companion object {
        fun builtInDescriptor(type: ProviderType): ProviderDescriptor? =
            builtInDescriptorList.firstOrNull { it.type == type }

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
        )

        private val builtInDescriptorList = listOf(
            ProviderDescriptor(
                type = ProviderType.OpenAI,
                displayName = "OpenAI",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = false,
                defaultBaseUrl = DEFAULT_OPENAI_BASE_URL,
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                capabilities = openAiCapabilities,
            ),
            ProviderDescriptor(
                type = ProviderType.OpenAICompatible,
                displayName = "兼容 OpenAI",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = null,
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = false,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.NewApi,
                displayName = "New API",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = null,
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = true,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.Sub2Api,
                displayName = "Sub2 API",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = null,
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = true,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.Custom,
                displayName = "自定义兼容接口",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = null,
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = true,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.OpenRouter,
                displayName = "OpenRouter",
                authMode = ProviderAuthMode.ApiKey,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = DEFAULT_OPENROUTER_BASE_URL,
                modelDiscovery = ProviderModelDiscovery("/models", ProviderModelDiscoveryFormat.OpenAiModels),
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = false,
                ),
            ),
            ProviderDescriptor(
                type = ProviderType.Ollama,
                displayName = "Ollama",
                authMode = ProviderAuthMode.None,
                supportsCustomBaseUrl = true,
                defaultBaseUrl = DEFAULT_OLLAMA_BASE_URL,
                modelDiscovery = ProviderModelDiscovery("/api/tags", ProviderModelDiscoveryFormat.OllamaTags),
                capabilities = ProviderCapabilities(
                    text = true,
                    vision = true,
                    imageGeneration = false,
                ),
            ),
        )
    }
}
