package com.aichat.workbench.tool.runtime

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.search.SearchResponse
import com.aichat.workbench.tool.search.SearchResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

internal class GatewayToolRunner(
    private val gatewaySettingsProvider: suspend () -> GatewaySettings,
    gatewayClientProvider: () -> GatewayClient,
) {
    private val gatewayClient: GatewayClient by lazy(gatewayClientProvider)

    suspend fun run(toolName: String, arguments: String): ExecutedToolOutput =
        when (toolName) {
            "web_search" -> ExecutedToolOutput(ToolOutput.Json(executeSearch(arguments).toJson()))
            "code_sandbox" -> ExecutedToolOutput(ToolOutput.Json(executeSandbox(arguments).toJson()))
            else -> error("工具尚未实现：$toolName")
        }

    private suspend fun executeSearch(arguments: String): SearchResponse {
        val settings = requireGatewaySettings()
        val args = decodeToolArguments<SearchArguments>(arguments)
        val query = args.query.trim()
        if (query.isBlank()) {
            throw InvalidToolArgumentsException("搜索关键词不能为空。")
        }
        return gatewayClient.search(settings.baseUrl, query, settings.apiToken)
    }

    private suspend fun executeSandbox(arguments: String): SandboxRunResponse {
        val settings = requireGatewaySettings()
        val args = decodeToolArguments<SandboxArguments>(arguments)
        if (args.code.isBlank()) {
            throw InvalidToolArgumentsException("沙箱代码不能为空。")
        }
        val language = args.language.trim().lowercase().ifBlank { DEFAULT_SANDBOX_LANGUAGE }
        if (language != DEFAULT_SANDBOX_LANGUAGE) {
            throw InvalidToolArgumentsException("仅支持 python 沙箱运行。")
        }
        val timeoutSeconds = args.timeoutSeconds ?: DEFAULT_SANDBOX_TIMEOUT_SECONDS
        if (timeoutSeconds !in MIN_SANDBOX_TIMEOUT_SECONDS..MAX_SANDBOX_TIMEOUT_SECONDS) {
            throw InvalidToolArgumentsException("Sandbox timeoutSeconds 必须在 1 到 10 秒之间。")
        }
        return gatewayClient.runSandbox(
            baseUrl = settings.baseUrl,
            language = language,
            code = args.code,
            timeoutSeconds = timeoutSeconds,
            apiToken = settings.apiToken,
        )
    }

    private suspend fun requireGatewaySettings(): GatewaySettings {
        val settings = gatewaySettingsProvider()
        if (!settings.enabled) {
            throw GatewaySettingsException("gateway_disabled", "工具网关未启用。")
        }
        if (!settings.baseUrl.isValidGatewayUrl()) {
            throw GatewaySettingsException("invalid_gateway_url", "工具网关地址无效。")
        }
        if (settings.apiToken.isBlank()) {
            throw GatewaySettingsException("gateway_token_required", "Gateway API token 未配置。")
        }
        return settings
    }

    private fun SearchResponse.toJson(): String =
        toolJson.encodeToString(
            SearchOutput(
                query = query,
                fetchedAt = fetchedAt.toString(),
                results = results.map { it.toOutput() },
            ),
        )

    private fun SearchResult.toOutput(): SearchResultOutput =
        SearchResultOutput(
            title = title,
            summary = summary,
            url = url,
            source = source,
            publishedAt = publishedAt?.toString(),
        )

    private fun SandboxRunResponse.toJson(): String =
        toolJson.encodeToString(
            SandboxOutput(
                language = language,
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode,
                durationMs = durationMs,
                timedOut = timedOut,
                truncated = truncated,
            ),
        )
}

private const val DEFAULT_SANDBOX_LANGUAGE = "python"
private const val DEFAULT_SANDBOX_TIMEOUT_SECONDS = 3
private const val MIN_SANDBOX_TIMEOUT_SECONDS = 1
private const val MAX_SANDBOX_TIMEOUT_SECONDS = 10

@Serializable
private data class SearchArguments(val query: String = "")

@Serializable
private data class SandboxArguments(
    val language: String = "python",
    val code: String = "",
    val timeoutSeconds: Int? = null,
)

@Serializable
private data class SearchOutput(
    val query: String,
    val fetchedAt: String,
    val results: List<SearchResultOutput>,
)

@Serializable
private data class SearchResultOutput(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: String? = null,
)

@Serializable
private data class SandboxOutput(
    val language: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean,
    val truncated: Boolean,
)
