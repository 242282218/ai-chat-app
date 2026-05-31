package com.aichat.workbench.feature.provider

import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.ui.component.StatusTone
import java.net.URI

internal data class ProviderUrlStatus(
    val label: String,
    val tone: StatusTone,
)

internal data class ProviderKeyStatus(
    val label: String,
    val tone: StatusTone,
)

internal data class HeaderStatus(
    val label: String,
    val tone: StatusTone,
)

internal fun ProviderType.providerTypeLabel(): String =
    when (this) {
        ProviderType.OpenAI -> "OpenAI"
        ProviderType.OpenAICompatible -> "Compatible"
    }

internal fun providerKeyStatus(
    apiKey: String,
    hasStoredKey: Boolean,
): ProviderKeyStatus =
    when {
        apiKey.isNotBlank() -> ProviderKeyStatus("Key entered", StatusTone.Success)
        hasStoredKey -> ProviderKeyStatus("Key stored", StatusTone.Success)
        else -> ProviderKeyStatus("No key", StatusTone.Warning)
    }

internal fun String.providerUrlStatus(allowHttp: Boolean): ProviderUrlStatus =
    when {
        isBlank() -> ProviderUrlStatus("URL required", StatusTone.Warning)
        isValidProviderBaseUrl(allowHttp) && trim().startsWith("http://") ->
            ProviderUrlStatus("HTTP allowed", StatusTone.Warning)
        isValidProviderBaseUrl(allowHttp) -> ProviderUrlStatus("URL valid", StatusTone.Success)
        trim().startsWith("http://") && !allowHttp -> ProviderUrlStatus("HTTP blocked", StatusTone.Critical)
        else -> ProviderUrlStatus("URL invalid", StatusTone.Critical)
    }

internal fun String.isValidProviderBaseUrl(allowHttp: Boolean): Boolean {
    val uri = runCatching { URI(trim()) }.getOrNull() ?: return false
    return when (uri.scheme?.lowercase()) {
        "https" -> uri.host != null
        "http" -> allowHttp && uri.host != null
        else -> false
    }
}

internal fun String.headerStatus(): HeaderStatus {
    val headerLines = lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
    if (headerLines.isEmpty()) return HeaderStatus("No headers", StatusTone.Neutral)

    val invalidCount = headerLines.count { !it.isValidHeaderLine() }
    return if (invalidCount == 0) {
        HeaderStatus("${headerLines.size} headers", StatusTone.Accent)
    } else {
        HeaderStatus("$invalidCount invalid headers", StatusTone.Critical)
    }
}

internal fun String.hasValidHeaderLines(): Boolean =
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .all { it.isValidHeaderLine() }

internal fun parseHeaderLines(value: String): Map<String, String> =
    value.lineSequence()
        .mapNotNull { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank() || !trimmedLine.isValidHeaderLine()) return@mapNotNull null

            val separator = trimmedLine.indexOf(':')
            val name = trimmedLine.substring(0, separator).trim()
            val headerValue = trimmedLine.substring(separator + 1).trim()
            name to headerValue
        }
        .toMap()

private fun String.isValidHeaderLine(): Boolean {
    val separator = indexOf(':')
    if (separator <= 0) return false

    val name = substring(0, separator).trim()
    val headerValue = substring(separator + 1).trim()
    return name.isNotBlank() && headerValue.isNotBlank()
}
