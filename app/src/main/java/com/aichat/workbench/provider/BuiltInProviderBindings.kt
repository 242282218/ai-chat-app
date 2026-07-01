package com.aichat.workbench.provider

import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.api.ChatProvider

fun createBuiltInProviderRegistry(
    openAiProvider: ChatProvider,
    compatibleProvider: ChatProvider,
): ProviderRegistry =
    ProviderRegistry().apply {
        registerBuiltIn(ProviderType.OpenAI, openAiProvider)
        compatibleChatProviderTypes.forEach { type ->
            registerBuiltIn(type, compatibleProvider)
        }
    }

private fun ProviderRegistry.registerBuiltIn(
    type: ProviderType,
    provider: ChatProvider,
) {
    register(requireNotNull(ProviderRegistry.builtInDescriptor(type)), provider)
}

private val compatibleChatProviderTypes = listOf(
    ProviderType.OpenAICompatible,
    ProviderType.NewApi,
    ProviderType.Sub2Api,
    ProviderType.Custom,
    ProviderType.OpenRouter,
    ProviderType.Ollama,
)
