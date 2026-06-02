package com.aichat.workbench.feature.tools

import java.net.URI

internal data class GatewayUrlStatus(
    val label: String,
    val isValid: Boolean,
    val isWarning: Boolean,
)

internal fun ToolsUiState.canSearch(): Boolean =
    !isLoading &&
        searchQuery.isNotBlank() &&
        gatewayEnabled &&
        gatewayBaseUrlDraft.isValidGatewayBaseUrl() &&
        gatewayApiTokenDraft.isNotBlank() &&
        hasSearchTool()

internal fun ToolsUiState.canRunSandbox(): Boolean =
    !isLoading &&
        sandboxCode.isNotBlank() &&
        gatewayEnabled &&
        gatewayBaseUrlDraft.isValidGatewayBaseUrl() &&
        gatewayApiTokenDraft.isNotBlank() &&
        hasSandboxTool()

internal fun ToolsUiState.hasSearchTool(): Boolean =
    remoteTools.any { it.name == "web_search" }

internal fun ToolsUiState.hasSandboxTool(): Boolean =
    remoteTools.any { it.name == "code_sandbox" }

internal fun String.gatewayUrlStatus(): GatewayUrlStatus =
    when {
        isBlank() -> GatewayUrlStatus(
            label = "需要 URL",
            isValid = false,
            isWarning = true,
        )
        isValidGatewayBaseUrl() && trim().startsWith("http://") -> GatewayUrlStatus(
            label = "HTTP 网关",
            isValid = true,
            isWarning = true,
        )
        isValidGatewayBaseUrl() -> GatewayUrlStatus(
            label = "URL 有效",
            isValid = true,
            isWarning = false,
        )
        else -> GatewayUrlStatus(
            label = "URL 无效",
            isValid = false,
            isWarning = false,
        )
    }

internal fun String.isValidGatewayBaseUrl(): Boolean {
    val uri = runCatching { URI(trim()) }.getOrNull() ?: return false
    return when (uri.scheme?.lowercase()) {
        "https",
        "http",
        -> uri.host != null
        else -> false
    }
}
