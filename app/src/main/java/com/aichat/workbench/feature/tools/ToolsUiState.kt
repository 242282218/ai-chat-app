package com.aichat.workbench.feature.tools

import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolRuntimeSetting
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.tool.model.runtimeSettingFor
import com.aichat.workbench.tool.registry.BuiltInToolRegistry
import com.aichat.workbench.tool.search.SearchProvider
import com.aichat.workbench.tool.search.SearchResult
import kotlinx.serialization.encodeToString

data class ToolsUiState(
    val gatewayEnabled: Boolean = false,
    val gatewayBaseUrlDraft: String = "",
    val gatewayHasApiToken: Boolean = false,
    val localSearchEnabled: Boolean = false,
    val localSearchProvider: SearchProvider = SearchProvider.Tavily,
    val localSearchBaseUrlDraft: String = "",
    val localSearchHasApiKey: Boolean = false,
    val localSearchMaxResultsDraft: String = "5",
    val localSearchDepthDraft: String = "basic",
    val localSearchTopicDraft: String = "general",
    val remoteTools: List<ToolDescriptor> = emptyList(),
    val isLoading: Boolean = false,
    val status: String? = null,
    val pendingConfirmation: ToolDescriptor? = null,
    val pendingSearchQuery: String? = null,
    val pendingSandboxCode: String? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val searchFetchedAt: String? = null,
    val searchError: ToolError? = null,
    val sandboxCode: String = "print(1 + 1)",
    val sandboxResult: SandboxRunResponse? = null,
    val sandboxError: ToolError? = null,
    val toolHistory: List<ToolResult> = emptyList(),
    val toolHistoryConversationFilter: String? = null,
    val toolHistoryToolFilter: String? = null,
    val toolHistoryStatusFilter: ToolStatus? = null,
    val refilledToolName: String? = null,
    val refilledToolInputJson: String? = null,
    val toolSettings: Map<String, ToolRuntimeSetting> = emptyMap(),
) {
    val gatewayApiTokenAvailable: Boolean
        get() = gatewayHasApiToken

    val localSearchApiKeyAvailable: Boolean
        get() = localSearchHasApiKey

    val tools: List<ToolDescriptor>
        get() = BuiltInToolRegistry.tools + remoteTools

    val enabledTools: List<ToolDescriptor>
        get() = tools.filter { tool -> toolSettings.runtimeSettingFor(tool).enabled }

    val filteredToolHistory: List<ToolResult>
        get() = toolHistory
            .filter { result ->
                toolHistoryConversationFilter == null ||
                    result.conversationId?.value == toolHistoryConversationFilter
            }
            .filter { result ->
                toolHistoryToolFilter == null ||
                    result.toolName.canonicalToolName() == toolHistoryToolFilter
            }
            .filter { result -> toolHistoryStatusFilter == null || result.status == toolHistoryStatusFilter }

    fun latestToolResultFor(tool: ToolDescriptor): ToolResult? {
        val canonicalName = tool.name.canonicalToolName()
        return toolHistory
            .filter { result -> result.toolName.canonicalToolName() == canonicalName }
            .maxByOrNull { result -> result.startedAt }
    }

    fun recentToolResultsFor(tool: ToolDescriptor, limit: Int = 10): List<ToolResult> {
        val canonicalName = tool.name.canonicalToolName()
        return toolHistory
            .filter { result -> result.toolName.canonicalToolName() == canonicalName }
            .sortedByDescending { result -> result.startedAt }
            .take(limit.coerceAtLeast(0))
    }

    fun latestRerunnableToolResultFor(tool: ToolDescriptor): ToolResult? =
        recentToolResultsFor(tool, limit = Int.MAX_VALUE).firstOrNull(::canRerunToolResult)

    fun latestRefillableToolResultFor(tool: ToolDescriptor): ToolResult? =
        recentToolResultsFor(tool, limit = Int.MAX_VALUE).firstOrNull(::canRefillToolResult)

    fun canRerunToolResult(result: ToolResult): Boolean =
        result.status in RERUNNABLE_STATUSES &&
            result.toolName.canonicalToolName() in RERUNNABLE_TOOL_NAMES &&
            (
                result.toolName.canonicalToolName() !in CHAT_REPLAY_TOOL_NAMES ||
                    !result.rawInputJson.isNullOrBlank()
                )

    fun canRefillToolResult(result: ToolResult): Boolean =
        result.status in REFILLABLE_STATUSES &&
            result.toolName.canonicalToolName() in REFILLABLE_TOOL_NAMES &&
            !result.rawInputJson.isNullOrBlank()

    fun canSendToolResultToChat(result: ToolResult): Boolean =
        result.status in CHAT_REPLAY_STATUSES

    fun chatInstructionForToolResult(result: ToolResult): String {
        val toolName = result.toolName.canonicalToolName()
        if (!canSendToolResultToChat(result)) {
            return """
            请只把这条工具历史作为诊断记录参考，不要据此重新执行工具。
            工具：$toolName
            状态：${result.status}
            输入摘要：${result.inputSummary.ifBlank { "(空)" }}
            """.trimIndent()
        }
        val rawInput = result.rawInputJson?.takeIf(String::isNotBlank)
        val baseInstruction = if (rawInput == null) {
            """
            请根据这条工具历史重新准备任务。
            工具：$toolName
            输入摘要：${result.inputSummary.ifBlank { "(空)" }}
            """.trimIndent()
        } else {
            chatInstructionForToolInput(toolName = toolName, rawInput = rawInput)
        }
        return result.failureContextForChat()?.let { failureContext ->
            listOfNotNull(
                baseInstruction,
                failureContext,
                result.recoveryHintForHistory()?.let { "恢复建议：$it" },
            ).joinToString(separator = "\n\n")
        } ?: baseInstruction
    }

    fun chatInstructionForRefilledTool(): String? {
        val toolName = refilledToolName?.canonicalToolName() ?: return null
        val rawInput = refilledToolInputJson?.takeIf(String::isNotBlank) ?: return null
        return chatInstructionForToolInput(toolName = toolName, rawInput = rawInput)
    }

    fun sampleInputForTool(tool: ToolDescriptor): String =
        sampleInputForToolName(tool.name)

    fun chatInstructionForTool(tool: ToolDescriptor): String {
        val toolName = tool.name.canonicalToolName()
        val sampleInput = sampleInputForTool(tool)
        if (toolName == "file_read") {
            return """
                请先让我通过聊天输入栏的附件按钮选择文件，然后用系统文件选择器返回的授权 URI 准备工具调用。
                不要手写本地路径，不要扫描文件夹，不要自动上传图片或文件内容。
                工具：file_read
                参数模板：$sampleInput
            """.trimIndent()
        }
        return chatInstructionForToolInput(toolName = toolName, rawInput = sampleInput)
    }

    fun searchWorkbenchInputJson(): String =
        toolsJson.encodeToString(SearchInputJson(query = searchQuery))

    fun searchWorkbenchOutputJson(): String? {
        if (searchResults.isEmpty() && searchFetchedAt == null) return null
        return toolsJson.encodeToString(
            SearchOutputJson(
                query = searchQuery,
                fetchedAt = searchFetchedAt.orEmpty(),
                results = searchResults.map { result ->
                    SearchResultOutputJson(
                        title = result.title,
                        summary = result.summary,
                        url = result.url,
                        source = result.source,
                        publishedAt = result.publishedAt?.toString(),
                    )
                },
            ),
        )
    }

    fun searchWorkbenchChatDraft(): String? =
        searchWorkbenchOutputJson()?.let { outputJson ->
            """
            请根据下面的本地搜索结果继续处理，先提炼关键事实，再给出结论和待确认信息。
            回答中的关键结论必须标注对应来源 URL；如果结果为空，请明确说明没有可引用来源。

            ```json
            $outputJson
            ```
            """.trimIndent()
        }

    fun sandboxWorkbenchInputJson(): String =
        toolsJson.encodeToString(
            SandboxInputJson(language = "python", code = sandboxCode, timeoutSeconds = 3),
        )

    fun sandboxWorkbenchOutputJson(): String? =
        sandboxResult?.let { result ->
            toolsJson.encodeToString(
                SandboxOutputJson(
                    language = result.language,
                    stdout = result.stdout,
                    stderr = result.stderr,
                    exitCode = result.exitCode,
                    durationMs = result.durationMs,
                    timedOut = result.timedOut,
                    truncated = result.truncated,
                ),
            )
        }

    fun sandboxWorkbenchChatDraft(): String? =
        sandboxWorkbenchOutputJson()?.let { outputJson ->
            """
            请根据下面的代码沙箱执行结果继续处理，先说明 stdout、stderr、退出码、超时和截断状态，再给出下一步建议。
            不要假设结果之外的文件、网络或系统状态。

            ```json
            $outputJson
            ```
            """.trimIndent()
        }
}
