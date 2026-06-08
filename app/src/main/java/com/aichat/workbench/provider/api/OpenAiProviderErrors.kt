package com.aichat.workbench.provider.api

import kotlinx.serialization.decodeFromString

internal fun parseOpenAiHttpError(
    statusCode: Int,
    body: String,
    fallbackMessage: String = "Provider 请求失败。",
): ProviderError {
    val rawMessage = runCatching {
        providerJson.decodeFromString<ProviderErrorEnvelope>(body).error?.message
    }.getOrNull()?.takeIf { it.isNotBlank() }
    val message = rawMessage ?: fallbackMessage
    val code = when {
        statusCode == 401 -> "authentication_failed"
        statusCode == 429 -> "rate_limited"
        rawMessage?.contains("model", ignoreCase = true) == true -> "invalid_model"
        rawMessage?.contains("quota", ignoreCase = true) == true -> "quota_exceeded"
        statusCode in 500..599 -> "provider_unavailable"
        else -> "provider_error"
    }
    return ProviderError(
        code = code,
        message = message,
        statusCode = statusCode,
        retryable = statusCode == 429 || statusCode in 500..599,
    )
}

internal fun ProviderErrorBody.toProviderError(statusCode: Int? = null): ProviderError =
    ProviderError(
        code = code ?: "provider_error",
        message = message ?: "Provider 请求失败。",
        statusCode = statusCode,
        retryable = statusCode == 429 || (statusCode != null && statusCode in 500..599),
    )
