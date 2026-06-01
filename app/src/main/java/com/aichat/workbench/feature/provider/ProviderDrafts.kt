package com.aichat.workbench.feature.provider

import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.ProviderRegistry
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
    ProviderRegistry.builtInDescriptor(this)?.displayName ?: value

internal fun providerKeyStatus(
    apiKey: String,
    hasStoredKey: Boolean,
    requiresApiKey: Boolean = true,
): ProviderKeyStatus =
    when {
        !requiresApiKey -> ProviderKeyStatus("无需 API Key", StatusTone.Neutral)
        apiKey.isNotBlank() -> ProviderKeyStatus("已输入 API Key", StatusTone.Success)
        hasStoredKey -> ProviderKeyStatus("已保存 API Key", StatusTone.Success)
        else -> ProviderKeyStatus("无 API Key", StatusTone.Warning)
    }

internal fun String.providerUrlStatus(allowHttp: Boolean): ProviderUrlStatus =
    when {
        isBlank() -> ProviderUrlStatus("需要 URL", StatusTone.Warning)
        isValidProviderBaseUrl(allowHttp) && trim().startsWith("http://") ->
            ProviderUrlStatus("已允许 HTTP", StatusTone.Warning)
        isValidProviderBaseUrl(allowHttp) -> ProviderUrlStatus("URL 有效", StatusTone.Success)
        trim().startsWith("http://") && !allowHttp -> ProviderUrlStatus("HTTP 已阻止", StatusTone.Critical)
        else -> ProviderUrlStatus("URL 无效", StatusTone.Critical)
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
    if (headerLines.isEmpty()) return HeaderStatus("无 Headers", StatusTone.Neutral)

    val invalidCount = headerLines.count { !it.isValidHeaderLine() }
    return if (invalidCount == 0) {
        HeaderStatus("${headerLines.size} 个 Headers", StatusTone.Accent)
    } else {
        HeaderStatus("$invalidCount 个无效 Headers", StatusTone.Critical)
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
