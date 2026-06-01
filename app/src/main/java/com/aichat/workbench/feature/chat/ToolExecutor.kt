package com.aichat.workbench.feature.chat

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.gateway.GatewayHttpException
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.gateway.SearchResponse
import com.aichat.workbench.tool.gateway.SearchResult
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.registry.BuiltInToolRegistry
import java.net.URI
import java.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ToolExecution(
    val result: ToolResult,
    val messageContent: String,
)

class ToolExecutor(
    private val gatewaySettingsProvider: () -> GatewaySettings,
    gatewayClientProvider: () -> GatewayClient,
    private val toolInvocationRepository: ToolInvocationRepository,
    private val clock: Clock,
) {
    private val gatewayClient: GatewayClient by lazy(gatewayClientProvider)

    suspend fun availableTools(): List<ToolDescriptor> =
        localTools() + remoteTools()

    suspend fun descriptorFor(name: String): ToolDescriptor? =
        availableTools().firstOrNull { it.name == name }

    suspend fun execute(conversationId: ConversationId, toolCall: ToolCall): ToolExecution {
        val descriptor = descriptorFor(toolCall.name)
            ?: return saveFailure(conversationId, toolCall, ToolPermissionLevel.HighRisk, "unknown_tool", "未知 Tool。")
        val startedAt = clock.instant()
        return runCatching {
            when (toolCall.name) {
                "time" -> ToolOutput.Json(timeOutputJson())
                "web_search" -> ToolOutput.Json(executeSearch(toolCall.arguments).toJson())
                "code_sandbox" -> ToolOutput.Json(executeSandbox(toolCall.arguments).toJson())
                else -> error("Tool 尚未实现：${toolCall.name}")
            }
        }.fold(
            onSuccess = { output ->
                val result = ToolResult(
                    id = toolCall.id,
                    toolName = toolCall.name,
                    permissionLevel = descriptor.permissionLevel,
                    inputSummary = toolCall.arguments.toInputSummary(),
                    output = output,
                    status = ToolStatus.Completed,
                    startedAt = startedAt,
                    finishedAt = clock.instant(),
                    error = null,
                )
                toolInvocationRepository.saveToolResult(conversationId, result)
                ToolExecution(result, output.asModelContent())
            },
            onFailure = { error ->
                saveFailure(
                    conversationId = conversationId,
                    toolCall = toolCall,
                    permissionLevel = descriptor.permissionLevel,
                    code = error.toToolErrorCode(),
                    message = error.message ?: "Tool 执行失败。",
                    startedAt = startedAt,
                )
            },
        )
    }

    suspend fun deny(conversationId: ConversationId, toolCall: ToolCall): ToolExecution =
        saveFailure(
            conversationId = conversationId,
            toolCall = toolCall,
            permissionLevel = descriptorFor(toolCall.name)?.permissionLevel ?: ToolPermissionLevel.HighRisk,
            code = "tool_denied",
            message = "用户拒绝执行 Tool。",
        )

    private fun localTools(): List<ToolDescriptor> =
        BuiltInToolRegistry.tools.filter { it.name == "time" }

    private suspend fun remoteTools(): List<ToolDescriptor> {
        val settings = gatewaySettingsProvider()
        if (!settings.enabled || !settings.baseUrl.isValidGatewayUrl()) return emptyList()
        return runCatching { gatewayClient.toolManifest(settings.baseUrl).tools }.getOrDefault(emptyList())
    }

    private suspend fun executeSearch(arguments: String): SearchResponse {
        val settings = requireGatewaySettings()
        val args = toolJson.decodeFromString<SearchArguments>(arguments)
        require(args.query.isNotBlank()) { "web_search.query 不能为空。" }
        return gatewayClient.search(settings.baseUrl, args.query, settings.apiToken)
    }

    private suspend fun executeSandbox(arguments: String): SandboxRunResponse {
        val settings = requireGatewaySettings()
        val args = toolJson.decodeFromString<SandboxArguments>(arguments)
        require(args.code.isNotBlank()) { "code_sandbox.code 不能为空。" }
        return gatewayClient.runSandbox(
            baseUrl = settings.baseUrl,
            language = args.language.ifBlank { "python" },
            code = args.code,
            timeoutSeconds = args.timeoutSeconds ?: 3,
            apiToken = settings.apiToken,
        )
    }

    private fun requireGatewaySettings(): GatewaySettings {
        val settings = gatewaySettingsProvider()
        require(settings.enabled) { "Gateway 未启用。" }
        require(settings.baseUrl.isValidGatewayUrl()) { "Gateway URL 无效。" }
        return settings
    }

    private suspend fun saveFailure(
        conversationId: ConversationId,
        toolCall: ToolCall,
        permissionLevel: ToolPermissionLevel,
        code: String,
        message: String,
        startedAt: java.time.Instant = clock.instant(),
    ): ToolExecution {
        val output = ToolOutput.Json(toolJson.encodeToString(ToolErrorOutput(code, message)))
        val result = ToolResult(
            id = toolCall.id,
            toolName = toolCall.name,
            permissionLevel = permissionLevel,
            inputSummary = toolCall.arguments.toInputSummary(),
            output = output,
            status = ToolStatus.Failed,
            startedAt = startedAt,
            finishedAt = clock.instant(),
            error = ToolError(code, message),
        )
        toolInvocationRepository.saveToolResult(conversationId, result)
        return ToolExecution(result, output.asModelContent())
    }

    private fun timeOutputJson(): String =
        toolJson.encodeToString(TimeOutput(clock.instant().toString()))

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

    private fun ToolOutput.asModelContent(): String =
        when (this) {
            is ToolOutput.Text -> text
            is ToolOutput.Json -> value
        }

    private fun String.toInputSummary(): String {
        val normalized = replace(Regex("\\s+"), " ").trim()
        val preview = normalized.take(120)
        return if (normalized.length > preview.length) "$preview..." else preview
    }

    private fun Throwable.toToolErrorCode(): String =
        when (this) {
            is GatewayHttpException -> gatewayCode
            else -> "tool_failed"
        }

    private fun String.isValidGatewayUrl(): Boolean {
        val uri = runCatching { URI(trim()) }.getOrNull() ?: return false
        return uri.host != null && uri.scheme in setOf("http", "https")
    }
}

private val toolJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
private data class SearchArguments(val query: String = "")

@Serializable
private data class SandboxArguments(
    val language: String = "python",
    val code: String = "",
    val timeoutSeconds: Int? = null,
)

@Serializable
private data class TimeOutput(val currentTime: String)

@Serializable
private data class ToolErrorOutput(val code: String, val message: String)

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
