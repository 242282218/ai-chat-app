package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderType

internal fun ProviderConfig.openAiApiBaseUrl(): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    return when {
        type == ProviderType.Ollama && !trimmed.endsWith("/v1") -> "$trimmed/v1"
        type.requiresOpenAiVersionSuffix() && !trimmed.endsWith("/v1") -> "$trimmed/v1"
        else -> trimmed
    }
}

internal fun ProviderConfig.modelDiscoveryBaseUrl(): String {
    val trimmed = openAiApiBaseUrl()
    return if (type == ProviderType.Ollama && trimmed.endsWith("/v1")) {
        trimmed.removeSuffix("/v1")
    } else {
        trimmed
    }
}

private fun ProviderType.requiresOpenAiVersionSuffix(): Boolean =
    this == ProviderType.OpenAI ||
        this == ProviderType.OpenAICompatible ||
        this == ProviderType.NewApi ||
        this == ProviderType.Sub2Api ||
        this == ProviderType.Custom ||
        this == ProviderType.OpenRouter
