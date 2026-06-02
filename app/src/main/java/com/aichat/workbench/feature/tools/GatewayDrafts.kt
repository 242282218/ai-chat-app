package com.aichat.workbench.feature.tools

import java.net.URI

internal data class GatewayUrlStatus(
    val label: String,
    val isValid: Boolean,
    val isWarning: Boolean,
)

internal data class GatewayActionStatus(
    val label: String,
    val isReady: Boolean,
    val isBusy: Boolean = false,
)

internal fun ToolsUiState.canSearch(): Boolean =
    searchActionStatus().isReady

internal fun ToolsUiState.canRunSandbox(): Boolean =
    sandboxActionStatus().isReady

internal fun ToolsUiState.canSaveGatewaySettings(): Boolean =
    !isLoading &&
        (!gatewayEnabled || gatewayBaseUrlDraft.isValidGatewayBaseUrl())

internal fun ToolsUiState.canCheckGatewayHealth(): Boolean =
    !isLoading &&
        gatewayBaseUrlDraft.isValidGatewayBaseUrl()

internal fun ToolsUiState.canFetchGatewayManifest(): Boolean =
    !isLoading &&
        gatewayEnabled &&
        gatewayBaseUrlDraft.isValidGatewayBaseUrl()

internal fun ToolsUiState.searchActionStatus(): GatewayActionStatus =
    when {
        isLoading -> GatewayActionStatus(label = "处理中", isReady = false, isBusy = true)
        !gatewayEnabled -> GatewayActionStatus(label = "网关关闭", isReady = false)
        !gatewayBaseUrlDraft.isValidGatewayBaseUrl() ->
            GatewayActionStatus(label = gatewayBaseUrlDraft.gatewayUrlStatus().label, isReady = false)
        gatewayApiTokenDraft.isBlank() -> GatewayActionStatus(label = "需要 Token", isReady = false)
        !hasSearchTool() -> GatewayActionStatus(label = "需要工具清单", isReady = false)
        searchQuery.isBlank() -> GatewayActionStatus(label = "需要关键词", isReady = false)
        else -> GatewayActionStatus(label = "就绪", isReady = true)
    }

internal fun ToolsUiState.sandboxActionStatus(): GatewayActionStatus =
    when {
        isLoading -> GatewayActionStatus(label = "处理中", isReady = false, isBusy = true)
        !gatewayEnabled -> GatewayActionStatus(label = "网关关闭", isReady = false)
        !gatewayBaseUrlDraft.isValidGatewayBaseUrl() ->
            GatewayActionStatus(label = gatewayBaseUrlDraft.gatewayUrlStatus().label, isReady = false)
        gatewayApiTokenDraft.isBlank() -> GatewayActionStatus(label = "需要 Token", isReady = false)
        !hasSandboxTool() -> GatewayActionStatus(label = "需要工具清单", isReady = false)
        sandboxCode.isBlank() -> GatewayActionStatus(label = "需要代码", isReady = false)
        else -> GatewayActionStatus(label = "就绪", isReady = true)
    }

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
        isValidGatewayBaseUrl() && trim().startsWith("http://", ignoreCase = true) -> GatewayUrlStatus(
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
