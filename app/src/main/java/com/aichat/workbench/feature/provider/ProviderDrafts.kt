package com.aichat.workbench.feature.provider

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.isPersistableProviderHeader
import com.aichat.workbench.domain.model.persistableProviderHeaderDisplayNames
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

internal data class ProviderHealthStats(
    val totalCount: Int,
    val enabledChatCount: Int,
    val defaultChatProviderName: String?,
    val encryptedKeyCount: Int,
    val httpCount: Int,
    val customHeaderCount: Int,
    val unsupportedEnabledCount: Int,
)

internal val providerHeaderPolicyText: String =
    "仅保存 ${persistableProviderHeaderDisplayNames.joinToString("、")}。"

internal fun ProviderType.providerTypeLabel(): String =
    ProviderRegistry.builtInDescriptor(this)?.displayName ?: value

internal fun List<ProviderConfig>.providerHealthStats(): ProviderHealthStats {
    val enabledChatProviders = filter { it.enabled && it.supportsChatProvider() }
    return ProviderHealthStats(
        totalCount = size,
        enabledChatCount = enabledChatProviders.size,
        defaultChatProviderName = enabledChatProviders.firstOrNull()?.name,
        encryptedKeyCount = count { it.apiKeyRef != null },
        httpCount = count { it.baseUrl.startsWith("http://") },
        customHeaderCount = count { it.headers.isNotEmpty() },
        unsupportedEnabledCount = count { it.enabled && !it.supportsChatProvider() },
    )
}

internal fun ProviderConfig.connectionSummary(): String {
    val statusText = when {
        !supportsChatProvider() -> "暂不可用"
        enabled -> "已启用"
        else -> "已停用"
    }
    val modelText = defaultModel ?: "无默认模型"
    val keyText = when {
        ProviderRegistry.builtInDescriptor(type)?.requiresApiKey == false -> "无需密钥"
        apiKeyRef != null -> "密钥已保存"
        else -> "缺少密钥"
    }
    return "$statusText · ${type.providerTypeLabel()} · $modelText · $keyText"
}

private fun ProviderConfig.supportsChatProvider(): Boolean =
    ProviderRegistry.isSupportedBuiltInChatProvider(type)

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
        isBlank() -> ProviderUrlStatus("需要接口地址", StatusTone.Warning)
        isValidProviderBaseUrl(allowHttp) && trim().startsWith("http://") ->
            ProviderUrlStatus("已允许 HTTP", StatusTone.Warning)
        isValidProviderBaseUrl(allowHttp) -> ProviderUrlStatus("接口地址有效", StatusTone.Success)
        trim().startsWith("http://") && !allowHttp -> ProviderUrlStatus("HTTP 已阻止", StatusTone.Critical)
        else -> ProviderUrlStatus("接口地址无效", StatusTone.Critical)
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
    if (headerLines.isEmpty()) return HeaderStatus("无请求头", StatusTone.Neutral)

    val invalidCount = headerLines.count { !it.isValidHeaderLine() }
    if (invalidCount > 0) {
        return HeaderStatus("$invalidCount 个无效请求头", StatusTone.Critical)
    }
    val blockedCount = headerLines.count { !it.isPersistableHeaderLine() }
    return when {
        blockedCount > 0 -> HeaderStatus("$blockedCount 个不允许保存", StatusTone.Critical)
        else -> HeaderStatus("${headerLines.size} 个请求头", StatusTone.Accent)
    }
}

internal fun String.hasValidHeaderLines(): Boolean =
    lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .all { it.isValidHeaderLine() && it.isPersistableHeaderLine() }

internal fun parseHeaderLines(value: String): Map<String, String> =
    value.lineSequence()
        .mapNotNull { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isBlank() || !trimmedLine.isValidHeaderLine() || !trimmedLine.isPersistableHeaderLine()) {
                return@mapNotNull null
            }

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

private fun String.isPersistableHeaderLine(): Boolean {
    val separator = indexOf(':')
    if (separator <= 0) return false
    return isPersistableProviderHeader(substring(0, separator))
}
