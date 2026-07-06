package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

internal fun ProviderConfig.openAiApiBaseUrl(): String =
    openAiApiHttpUrl().toString().trimEnd('/')

internal fun ProviderConfig.openAiApiHttpUrl(): HttpUrl {
    val trimmed = baseUrl.trim().trimEnd('/')
    return when {
        type == ProviderType.Ollama && !trimmed.endsWith("/v1") -> "$trimmed/v1"
        type.requiresOpenAiVersionSuffix() && !trimmed.endsWith("/v1") -> "$trimmed/v1"
        else -> trimmed
    }.toHttpUrlWithoutUrlDecorators()
}

internal fun ProviderConfig.modelDiscoveryBaseUrl(): String =
    modelDiscoveryBaseHttpUrl().toString().trimEnd('/')

internal fun ProviderConfig.modelDiscoveryBaseHttpUrl(): HttpUrl {
    val url = openAiApiHttpUrl()
    return if (type == ProviderType.Ollama && url.encodedPath.trimEnd('/') == "/v1") {
        url.newBuilder()
            .removePathSegment(url.pathSegments.lastIndex)
            .build()
    } else {
        url
    }
}

private fun ProviderType.requiresOpenAiVersionSuffix(): Boolean =
    this == ProviderType.OpenAI ||
        this == ProviderType.OpenAICompatible ||
        this == ProviderType.NewApi ||
        this == ProviderType.Sub2Api ||
        this == ProviderType.Custom ||
        this == ProviderType.OpenRouter

private fun String.toHttpUrlWithoutUrlDecorators(): HttpUrl {
    val url = toHttpUrl()
    require(url.query == null && url.fragment == null && url.username.isEmpty() && url.password.isEmpty()) {
        "Provider URL must not contain user info, query, or fragment."
    }
    return url
}
