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
        hasSearchTool()

internal fun ToolsUiState.canRunSandbox(): Boolean =
    !isLoading &&
        sandboxCode.isNotBlank() &&
        gatewayEnabled &&
        gatewayBaseUrlDraft.isValidGatewayBaseUrl() &&
        hasSandboxTool()

internal fun ToolsUiState.hasSearchTool(): Boolean =
    remoteTools.any { it.name == "web_search" }

internal fun ToolsUiState.hasSandboxTool(): Boolean =
    remoteTools.any { it.name == "code_sandbox" }

internal fun String.gatewayUrlStatus(): GatewayUrlStatus =
    when {
        isBlank() -> GatewayUrlStatus(
            label = "URL required",
            isValid = false,
            isWarning = true,
        )
        isValidGatewayBaseUrl() && trim().startsWith("http://") -> GatewayUrlStatus(
            label = "HTTP gateway",
            isValid = true,
            isWarning = true,
        )
        isValidGatewayBaseUrl() -> GatewayUrlStatus(
            label = "URL valid",
            isValid = true,
            isWarning = false,
        )
        else -> GatewayUrlStatus(
            label = "URL invalid",
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
