package com.aichat.workbench.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.data.settings.GatewaySettingsRepository
import com.aichat.workbench.data.settings.SearchSettingsRepository
import com.aichat.workbench.data.settings.ToolSettingsRepository
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.gateway.GatewayHttpException
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRuntimeSetting
import com.aichat.workbench.tool.model.canUsePermissionPolicy
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.tool.model.requiresConfirmation
import com.aichat.workbench.tool.model.runtimeSettingFor
import com.aichat.workbench.tool.registry.BuiltInToolRegistry
import com.aichat.workbench.tool.search.LocalSearchClient
import com.aichat.workbench.tool.search.LocalSearchHttpException
import com.aichat.workbench.tool.search.SearchConfig
import com.aichat.workbench.tool.search.SearchProvider
import com.aichat.workbench.tool.search.SearchResponse
import com.aichat.workbench.tool.search.SearchResult
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ToolsUiState(
    val gatewayEnabled: Boolean = false,
    val gatewayBaseUrlDraft: String = "",
    val gatewayApiTokenDraft: String = "",
    val localSearchEnabled: Boolean = false,
    val localSearchProvider: SearchProvider = SearchProvider.Tavily,
    val localSearchBaseUrlDraft: String = "",
    val localSearchApiKeyDraft: String = "",
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

private fun chatInstructionForToolInput(
    toolName: String,
    rawInput: String,
): String {
    val canonicalName = toolName.canonicalToolName()
    return when (canonicalName) {
        "file_read" ->
            """
            请先确认这条文件读取任务仍来自用户通过系统文件选择器授权的 content:// URI。
            不要手写本地路径，不要扫描文件夹，不要自动上传图片或文件内容；如需多模态分析，先征得确认。
            工具：file_read
            参数：$rawInput
            """.trimIndent()
        "local_js" ->
            """
            请先审查这段本地 JavaScript 是否只做纯计算或文本处理，再准备工具调用。
            不要请求网络、文件系统、系统命令或 Android Context；执行前列出超时和输出截断设置。
            工具：local_js
            参数：$rawInput
            """.trimIndent()
        "web_search_local", "web_search" ->
            """
            请搜索这个主题并保留来源链接。
            回答中的关键结论必须标注对应来源 URL；如果没有结果，请明确说明没有可引用来源。
            工具：$canonicalName
            参数：$rawInput
            """.trimIndent()
        "text_transform" ->
            """
            请把这条任务作为本地文本转换处理，不调用 Provider，不上传文本。
            先确认转换类型、输入长度和输出截断设置，再准备工具调用。
            工具：text_transform
            参数：$rawInput
            """.trimIndent()
        "code_diff_preview" ->
            """
            请只用 code_diff_preview 生成 Diff 预览，不写入文件、不修改本机项目。
            执行前确认 original 和 modified 的差异，并在结果里只展示 diff。
            工具：code_diff_preview
            参数：$rawInput
            """.trimIndent()
        "image_generation" ->
            """
            请把这条图片生成任务作为需要用户确认的付费/联网调用来准备。
            执行前确认 Provider、模型、数量和尺寸；如果参数里包含 providerId，请先核对是否仍要使用该 Provider。不要自动上传本地图片。
            工具：image_generation
            参数：$rawInput
            """.trimIndent()
        "image_upload_to_model" ->
            """
            请先让我通过聊天输入栏选择图片，并在发送前说明图片会作为多模态内容发送给当前模型。
            不要手写本地路径，不要自动读取或上传本地图片；必须等待用户二次确认。
            工具：image_upload_to_model
            参数模板：$rawInput
            """.trimIndent()
        "provider_connection_test" ->
            """
            请准备一次 Provider 连接测试，并说明会使用已保存的 Provider 配置，不要输出或索要 API Key 明文。
            工具：provider_connection_test
            参数：$rawInput
            """.trimIndent()
        else ->
            """
            请准备执行这条工具任务，并基于工具结果继续回答。
            工具：$canonicalName
            参数：$rawInput
            """.trimIndent()
    }
}

private fun ToolResult.failureContextForChat(): String? {
    val error = error ?: return null
    val lines = buildList {
        add("上次执行失败：${error.code}: ${error.message}")
        error.statusCode?.let { add("HTTP 状态：$it") }
        error.retryable?.let { add("是否可重试：${if (it) "是" else "否"}") }
    }
    return lines.joinToString(separator = "\n")
}

internal fun ToolResult.recoveryHintForHistory(): String? {
    val error = error ?: return null
    val canonicalName = toolName.canonicalToolName()
    return when {
        error.code in TOOL_HISTORY_CONFIGURATION_ERROR_CODES ->
            "请打开工具中心检查工具是否启用、名称是否正确，或改用当前 App 支持的本地工具。"
        error.statusCode == 401 -> when (canonicalName) {
            "provider_connection_test",
            "image_generation",
            -> "检查 Provider API Key、Base URL 和模型配置后重试。"
            "web_search",
            "web_search_local",
            -> "检查搜索 API Key、Provider URL 或网关鉴权后重试。"
            else -> "检查 API Key、Provider 配置或网关鉴权后重试。"
        }
        error.statusCode == 429 -> when (canonicalName) {
            "image_generation" -> "图片生成请求被限流，稍后重试，或切换图片模型/Provider。"
            "provider_connection_test" -> "Provider 测试被限流，稍后重试，或切换 Provider/模型。"
            "web_search",
            "web_search_local",
            -> "搜索请求被限流，稍后重试，或切换搜索 Provider。"
            else -> "请求被限流，稍后重试，或切换相关 Provider。"
        }
        error.statusCode != null && error.statusCode in 500..599 -> when (canonicalName) {
            "image_generation" -> "图片服务端异常，稍后重试，或切换图片模型/Provider。"
            "web_search",
            "web_search_local",
            -> "搜索服务端异常，稍后重试，或切换搜索 Provider。"
            else -> "服务端异常，稍后重试，或切换 Provider。"
        }
        canonicalName == "image_upload_to_model" ->
            "请通过聊天输入栏选择图片，并在发送前确认图片会作为多模态内容发送给当前模型。"
        error.retryable == true -> "这是可重试错误，可以稍后重试或调整参数后重跑。"
        error.retryable == false -> "这通常不是自动重试能解决的问题，请先检查配置或参数。"
        else -> "检查工具参数和相关配置后重试。"
    }
}

private val TOOL_HISTORY_CONFIGURATION_ERROR_CODES = setOf(
    "tool_disabled",
    "unknown_tool",
    "hosted_tool_not_executable_locally",
)

class ToolsViewModel(
    private val settingsRepository: GatewaySettingsRepository,
    private val searchSettingsRepository: SearchSettingsRepository,
    private val toolSettingsRepository: ToolSettingsRepository,
    private val gatewayClient: GatewayClient,
    private val localSearchClient: LocalSearchClient,
    private val toolInvocationRepository: ToolInvocationRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(ToolsUiState())
    val state: StateFlow<ToolsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.loadSettings()
            settingsRepository.observeSettings().collect { settings ->
                _state.update {
                    it.copy(
                        gatewayEnabled = settings.enabled,
                        gatewayBaseUrlDraft = settings.baseUrl,
                        gatewayApiTokenDraft = settings.apiToken,
                    )
                }
            }
        }
        viewModelScope.launch {
            searchSettingsRepository.loadSettings()
            searchSettingsRepository.observeSettings().collect { settings ->
                _state.update {
                    it.copy(
                        localSearchEnabled = settings.enabled,
                        localSearchProvider = settings.provider,
                        localSearchBaseUrlDraft = settings.baseUrl,
                        localSearchApiKeyDraft = settings.apiKey,
                        localSearchMaxResultsDraft = settings.maxResults.toString(),
                        localSearchDepthDraft = settings.searchDepth,
                        localSearchTopicDraft = settings.topic,
                    )
                }
            }
        }
        viewModelScope.launch {
            toolInvocationRepository.observeToolInvocations().collect { history ->
                _state.update { it.copy(toolHistory = history) }
            }
        }
        viewModelScope.launch {
            toolSettingsRepository.loadSettings()
            toolSettingsRepository.observeSettings().collect { settings ->
                _state.update { it.copy(toolSettings = settings) }
            }
        }
    }

    fun updateGatewayEnabled(value: Boolean) {
        _state.update { it.copy(gatewayEnabled = value) }
    }

    fun updateGatewayBaseUrl(value: String) {
        _state.update { it.copy(gatewayBaseUrlDraft = value) }
    }

    fun updateGatewayApiToken(value: String) {
        _state.update { it.copy(gatewayApiTokenDraft = value) }
    }

    fun updateLocalSearchEnabled(value: Boolean) {
        _state.update { it.copy(localSearchEnabled = value) }
    }

    fun updateLocalSearchBaseUrl(value: String) {
        _state.update { it.copy(localSearchBaseUrlDraft = value) }
    }

    fun updateLocalSearchApiKey(value: String) {
        _state.update { it.copy(localSearchApiKeyDraft = value) }
    }

    fun updateLocalSearchMaxResults(value: String) {
        _state.update { it.copy(localSearchMaxResultsDraft = value.filter(Char::isDigit).take(2)) }
    }

    fun updateLocalSearchDepth(value: String) {
        _state.update { it.copy(localSearchDepthDraft = value) }
    }

    fun updateLocalSearchTopic(value: String) {
        _state.update { it.copy(localSearchTopicDraft = value) }
    }

    fun updateSearchQuery(value: String) {
        _state.update { it.copy(searchQuery = value, searchError = null) }
    }

    fun updateSandboxCode(value: String) {
        _state.update { it.copy(sandboxCode = value, sandboxError = null) }
    }

    fun updateToolHistoryConversationFilter(value: String?) {
        _state.update { it.copy(toolHistoryConversationFilter = value?.takeIf(String::isNotBlank)) }
    }

    fun updateToolHistoryToolFilter(value: String?) {
        _state.update { it.copy(toolHistoryToolFilter = value?.takeIf(String::isNotBlank)?.canonicalToolName()) }
    }

    fun updateToolHistoryStatusFilter(value: ToolStatus?) {
        _state.update { it.copy(toolHistoryStatusFilter = value) }
    }

    fun rerunToolResult(result: ToolResult) {
        if (!_state.value.canRerunToolResult(result)) {
            _state.update { it.copy(status = "${result.toolName.canonicalToolName()} 当前状态不支持重跑。") }
            return
        }
        when (result.toolName.canonicalToolName()) {
            "web_search_local" -> rerunLocalSearch(result)
            "web_search" -> rerunGatewaySearch(result)
            "code_sandbox" -> rerunSandbox(result)
            in CHAT_REPLAY_TOOL_NAMES -> refillToolResult(result)
            else -> _state.update { it.copy(status = "${result.toolName} 暂不支持从历史重跑。") }
        }
    }

    fun refillToolResult(result: ToolResult) {
        if (!_state.value.canRefillToolResult(result)) {
            _state.update { it.copy(status = "${result.toolName.canonicalToolName()} 当前状态不支持回填参数。") }
            return
        }
        val rawInput = result.rawInputJson?.takeIf(String::isNotBlank)
        if (rawInput == null) {
            _state.update { it.copy(status = "这条历史没有可回填的原始参数。") }
            return
        }
        val toolName = result.toolName.canonicalToolName()
        if (toolName !in REFILLABLE_TOOL_NAMES) {
            _state.update { it.copy(status = "$toolName 暂不支持回填参数。") }
            return
        }
        _state.update {
            it.copy(
                refilledToolName = toolName,
                refilledToolInputJson = rawInput,
                status = "已回填 $toolName 参数。",
            )
        }
    }

    fun updateToolEnabled(toolName: String, enabled: Boolean) {
        toolSettingsRepository.setToolEnabled(toolName, enabled)
    }

    fun updateToolPermissionPolicy(toolName: String, policy: ToolPermissionPolicy) {
        val canonicalName = toolName.canonicalToolName()
        val tool = _state.value.tools.firstOrNull { it.name.canonicalToolName() == canonicalName }
        if (tool != null && !tool.canUsePermissionPolicy()) {
            val fixedPolicy = tool.defaultPermissionPolicy
            toolSettingsRepository.setPermissionPolicy(canonicalName, fixedPolicy)
            _state.update { it.copy(status = tool.fixedPermissionPolicyStatus(fixedPolicy)) }
            return
        }
        toolSettingsRepository.setPermissionPolicy(canonicalName, policy)
    }

    fun saveGatewaySettings() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, status = null) }
            runCatching {
                settingsRepository.saveSettings(
                    enabled = current.gatewayEnabled,
                    baseUrl = current.gatewayBaseUrlDraft,
                    apiToken = current.gatewayApiTokenDraft,
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        remoteTools = emptyList(),
                        status = "已保存",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "保存网关设置失败。") }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun saveLocalSearchSettings() {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, status = null) }
            runCatching {
                searchSettingsRepository.saveSettings(
                    enabled = current.localSearchEnabled,
                    provider = current.localSearchProvider,
                    baseUrl = current.localSearchBaseUrlDraft,
                    apiKey = current.localSearchApiKeyDraft,
                    maxResults = current.localSearchMaxResultsOrDefault(),
                    searchDepth = current.localSearchDepthDraft,
                    topic = current.localSearchTopicDraft,
                )
            }.onSuccess {
                _state.update { it.copy(status = "搜索设置已保存") }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "保存搜索设置失败。") }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun checkHealth() {
        val baseUrl = _state.value.gatewayBaseUrlDraft
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, status = null) }
            runCatching {
                gatewayClient.health(baseUrl)
            }.onSuccess { health ->
                _state.update { it.copy(status = "Gateway ${health.status} / ${health.version}") }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "网关健康检查失败。") }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun fetchManifest() {
        val baseUrl = _state.value.gatewayBaseUrlDraft
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, status = null) }
            runCatching {
                gatewayClient.toolManifest(baseUrl)
            }.onSuccess { manifest ->
                _state.update {
                    it.copy(
                        remoteTools = manifest.tools,
                        status = "已加载 ${manifest.tools.size} 个网关工具",
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        remoteTools = emptyList(),
                        status = error.message ?: "加载工具清单失败。",
                    )
                }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun requestPermission(tool: ToolDescriptor) {
        if (!tool.isEnabled()) {
            _state.update { it.copy(status = "${tool.displayName} 已禁用。") }
            return
        }
        if (!tool.requiresConfirmation()) {
            _state.update { it.copy(status = "${tool.displayName} 可直接运行") }
            return
        }
        _state.update { it.copy(pendingConfirmation = tool) }
    }

    fun requestSearch() {
        val current = _state.value
        val query = current.searchQuery.trim()
        if (query.isBlank()) {
            _state.update {
                it.copy(
                    status = "搜索关键词不能为空。",
                    searchError = ToolError(
                        code = "invalid_query",
                        message = "搜索关键词不能为空。",
                    ),
                )
            }
            return
        }
        if (current.localSearchEnabled) {
            if (!current.localSearchBaseUrlDraft.isValidGatewayBaseUrl()) {
                _state.update { it.copy(status = "搜索 Provider URL 无效。") }
                return
            }
            if (current.localSearchApiKeyDraft.isBlank()) {
                _state.update {
                    it.copy(
                        status = "搜索 API Key 未配置。",
                        searchError = localSearchKeyRequiredError(),
                    )
                }
                return
            }
            if (current.localSearchMaxResultsOrNull() == null) {
                _state.update { it.copy(status = "搜索结果数量必须在 1 到 20 之间。") }
                return
            }
            val searchTool = current.tools.firstOrNull { it.name == "web_search_local" }
            if (searchTool == null) {
                _state.update { it.copy(status = "本地搜索工具不可用。") }
                return
            }
            if (!searchTool.isEnabled()) {
                _state.update { it.copy(status = "本地搜索工具已禁用。") }
                return
            }
            if (searchTool.requiresConfirmation()) {
                _state.update {
                    it.copy(
                        pendingConfirmation = searchTool,
                        pendingSearchQuery = query,
                    )
                }
            } else {
                executeLocalSearch(query)
            }
            return
        }
        if (!current.gatewayEnabled) {
            _state.update { it.copy(status = "搜索前请启用本地搜索或 Gateway。") }
            return
        }
        requestGatewaySearch(query)
    }

    private fun requestGatewaySearch(query: String) {
        val current = _state.value
        if (!current.gatewayBaseUrlDraft.isValidGatewayBaseUrl()) {
            _state.update { it.copy(status = "工具网关地址无效。") }
            return
        }
        if (current.gatewayApiTokenDraft.isBlank()) {
            _state.update {
                it.copy(
                    status = "Gateway API token 未配置。",
                    searchError = gatewayTokenRequiredError(),
                )
            }
            return
        }
        val searchTool = current.remoteTools.firstOrNull { it.name == "web_search" }
        if (searchTool == null) {
            _state.update { it.copy(status = "搜索前请先加载工具清单。") }
            return
        }
        if (!searchTool.isEnabled()) {
            _state.update { it.copy(status = "Gateway 搜索工具已禁用。") }
            return
        }
        if (searchTool.requiresConfirmation()) {
            _state.update {
                it.copy(
                    pendingConfirmation = searchTool,
                    pendingSearchQuery = query,
                )
            }
        } else {
            executeGatewaySearch(query)
        }
    }

    fun requestSandboxRun() {
        val current = _state.value
        val code = current.sandboxCode.trim()
        if (code.isBlank()) {
            _state.update {
                it.copy(
                    status = "沙箱代码不能为空。",
                    sandboxError = ToolError(
                        code = "invalid_code",
                        message = "沙箱代码不能为空。",
                    ),
                )
            }
            return
        }
        if (!current.gatewayEnabled) {
            _state.update { it.copy(status = "运行代码前请启用网关。") }
            return
        }
        if (!current.gatewayBaseUrlDraft.isValidGatewayBaseUrl()) {
            _state.update { it.copy(status = "工具网关地址无效。") }
            return
        }
        if (current.gatewayApiTokenDraft.isBlank()) {
            _state.update {
                it.copy(
                    status = "Gateway API token 未配置。",
                    sandboxError = gatewayTokenRequiredError(),
                )
            }
            return
        }
        val sandboxTool = current.remoteTools.firstOrNull { it.name == "code_sandbox" }
        if (sandboxTool == null) {
            _state.update { it.copy(status = "运行代码前请先加载工具清单。") }
            return
        }
        if (!sandboxTool.isEnabled()) {
            _state.update { it.copy(status = "代码沙箱工具已禁用。") }
            return
        }
        _state.update {
            it.copy(
                pendingConfirmation = sandboxTool,
                pendingSandboxCode = code,
            )
        }
    }

    fun confirmPermission() {
        val tool = _state.value.pendingConfirmation ?: return
        val query = _state.value.pendingSearchQuery
        val code = _state.value.pendingSandboxCode
        _state.update {
            it.copy(
                pendingConfirmation = null,
                pendingSearchQuery = null,
                pendingSandboxCode = null,
                status = "已确认${tool.permissionLevel.label()}工具：${tool.displayName}",
            )
        }
        if (tool.name == "web_search_local" && query != null) {
            executeLocalSearch(query)
        }
        if (tool.name == "web_search" && query != null) {
            executeGatewaySearch(query)
        }
        if (tool.name == "code_sandbox" && code != null) {
            executeSandbox(code)
        }
    }

    fun dismissPermission() {
        _state.update {
            it.copy(
                pendingConfirmation = null,
                pendingSearchQuery = null,
                pendingSandboxCode = null,
            )
        }
    }

    private fun rerunLocalSearch(result: ToolResult) {
        val query = result.extractSearchQuery()
        if (query == null) {
            _state.update { it.copy(status = "无法从历史记录解析搜索参数。") }
            return
        }
        _state.update { it.copy(searchQuery = query) }
        if (!_state.value.localSearchEnabled) {
            _state.update { it.copy(status = "本地搜索未启用，已回填参数。") }
            return
        }
        requestSearch()
    }

    private fun rerunGatewaySearch(result: ToolResult) {
        val query = result.extractSearchQuery()
        if (query == null) {
            _state.update { it.copy(status = "无法从历史记录解析搜索参数。") }
            return
        }
        _state.update { it.copy(searchQuery = query) }
        if (!_state.value.gatewayEnabled) {
            _state.update { it.copy(status = "Gateway 未启用，已回填参数。") }
            return
        }
        requestGatewaySearch(query)
    }

    private fun rerunSandbox(result: ToolResult) {
        val code = result.extractSandboxCode()
        if (code == null) {
            _state.update { it.copy(status = "无法从历史记录解析沙箱代码。") }
            return
        }
        _state.update { it.copy(sandboxCode = code) }
        requestSandboxRun()
    }

    private fun executeLocalSearch(query: String) {
        viewModelScope.launch {
            val startedAt = clock.instant()
            val toolCallId = ToolCallId(UUID.randomUUID().toString())
            _state.update {
                it.copy(
                    isLoading = true,
                    status = null,
                    searchResults = emptyList(),
                    searchError = null,
                )
            }
            runCatching {
                localSearchClient.search(query, _state.value.toLocalSearchConfig())
            }.onSuccess { response ->
                saveSearchSuccess(
                    toolCallId = toolCallId,
                    toolName = "web_search_local",
                    query = query,
                    startedAt = startedAt,
                    response = response,
                    status = "本地搜索返回 ${response.results.size} 个来源",
                )
            }.onFailure { error ->
                saveSearchFailure(
                    toolCallId = toolCallId,
                    toolName = "web_search_local",
                    query = query,
                    startedAt = startedAt,
                    error = error.toSearchToolError(),
                    status = "本地搜索失败。",
                )
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun executeGatewaySearch(query: String) {
        val baseUrl = _state.value.gatewayBaseUrlDraft
        viewModelScope.launch {
            val startedAt = clock.instant()
            val toolCallId = ToolCallId(UUID.randomUUID().toString())
            _state.update {
                it.copy(
                    isLoading = true,
                    status = null,
                    searchResults = emptyList(),
                    searchError = null,
                )
            }
            runCatching {
                gatewayClient.search(baseUrl, query, _state.value.gatewayApiTokenDraft)
            }.onSuccess { response ->
                saveSearchSuccess(
                    toolCallId = toolCallId,
                    toolName = "web_search",
                    query = query,
                    startedAt = startedAt,
                    response = response,
                    status = "Gateway 搜索返回 ${response.results.size} 个来源",
                )
            }.onFailure { error ->
                saveSearchFailure(
                    toolCallId = toolCallId,
                    toolName = "web_search",
                    query = query,
                    startedAt = startedAt,
                    error = error.toSearchToolError(),
                    status = "Gateway 搜索失败。",
                )
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun saveSearchSuccess(
        toolCallId: ToolCallId,
        toolName: String,
        query: String,
        startedAt: Instant,
        response: SearchResponse,
        status: String,
    ) {
        val outputJson = response.toJson()
        val finishedAt = clock.instant()
        val toolResult = ToolResult(
            id = toolCallId,
            toolName = toolName,
            permissionLevel = ToolPermissionLevel.Network,
            inputSummary = query.toInputSummary("query"),
            output = ToolOutput.Json(outputJson),
            status = ToolStatus.Completed,
            startedAt = startedAt,
            finishedAt = finishedAt,
            error = null,
            rawInputJson = toolsJson.encodeToString(SearchInputJson(query = query)),
            rawOutputJson = outputJson,
            durationMs = startedAt.durationUntilMs(finishedAt),
        )
        toolInvocationRepository.saveToolResult(conversationId = null, toolResult = toolResult)
        _state.update {
            it.copy(
                searchResults = response.results,
                searchFetchedAt = response.fetchedAt.toString(),
                searchError = null,
                status = status,
            )
        }
    }

    private suspend fun saveSearchFailure(
        toolCallId: ToolCallId,
        toolName: String,
        query: String,
        startedAt: Instant,
        error: ToolError,
        status: String,
    ) {
        val outputJson = emptySearchOutputJson(query, startedAt)
        val finishedAt = clock.instant()
        val toolResult = ToolResult(
            id = toolCallId,
            toolName = toolName,
            permissionLevel = ToolPermissionLevel.Network,
            inputSummary = query.toInputSummary("query"),
            output = ToolOutput.Json(outputJson),
            status = ToolStatus.Failed,
            startedAt = startedAt,
            finishedAt = finishedAt,
            error = error,
            rawInputJson = toolsJson.encodeToString(SearchInputJson(query = query)),
            rawOutputJson = outputJson,
            durationMs = startedAt.durationUntilMs(finishedAt),
        )
        toolInvocationRepository.saveToolResult(conversationId = null, toolResult = toolResult)
        _state.update {
            it.copy(
                status = status,
                searchError = error,
            )
        }
    }

    private fun executeSandbox(code: String) {
        val baseUrl = _state.value.gatewayBaseUrlDraft
        viewModelScope.launch {
            val startedAt = clock.instant()
            val toolCallId = ToolCallId(UUID.randomUUID().toString())
            _state.update {
                it.copy(
                    isLoading = true,
                    status = null,
                    sandboxResult = null,
                    sandboxError = null,
                )
            }
            runCatching {
                gatewayClient.runSandbox(
                    baseUrl = baseUrl,
                    language = "python",
                    code = code,
                    timeoutSeconds = 3,
                    apiToken = _state.value.gatewayApiTokenDraft,
                )
            }.onSuccess { response ->
                val outputJson = response.toJson()
                val finishedAt = clock.instant()
                val toolResult = ToolResult(
                    id = toolCallId,
                    toolName = "code_sandbox",
                    permissionLevel = ToolPermissionLevel.Execute,
                    inputSummary = code.toInputSummary("python"),
                    output = ToolOutput.Json(outputJson),
                    status = ToolStatus.Completed,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    error = null,
                    rawInputJson = toolsJson.encodeToString(
                        SandboxInputJson(language = "python", code = code, timeoutSeconds = 3),
                    ),
                    rawOutputJson = outputJson,
                    durationMs = startedAt.durationUntilMs(finishedAt),
                )
                toolInvocationRepository.saveToolResult(conversationId = null, toolResult = toolResult)
                _state.update {
                    it.copy(
                        sandboxResult = response,
                        sandboxError = null,
                        status = "代码沙箱退出码 ${response.exitCode}",
                    )
                }
            }.onFailure { error ->
                val toolError = error.toToolError(
                    fallbackCode = "sandbox_failed",
                    fallbackMessage = "代码沙箱运行失败。",
                )
                val outputJson = emptySandboxOutputJson()
                val finishedAt = clock.instant()
                val toolResult = ToolResult(
                    id = toolCallId,
                    toolName = "code_sandbox",
                    permissionLevel = ToolPermissionLevel.Execute,
                    inputSummary = code.toInputSummary("python"),
                    output = ToolOutput.Json(outputJson),
                    status = ToolStatus.Failed,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    error = toolError,
                    rawInputJson = toolsJson.encodeToString(
                        SandboxInputJson(language = "python", code = code, timeoutSeconds = 3),
                    ),
                    rawOutputJson = outputJson,
                    durationMs = startedAt.durationUntilMs(finishedAt),
                )
                toolInvocationRepository.saveToolResult(conversationId = null, toolResult = toolResult)
                _state.update {
                    it.copy(
                        status = "代码沙箱失败。",
                        sandboxError = toolError,
                    )
                }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun Throwable.toSearchToolError(): ToolError =
        toToolError(
            fallbackCode = "search_failed",
            fallbackMessage = "网络搜索失败。",
        )

    private fun gatewayTokenRequiredError(): ToolError =
        ToolError(
            code = "gateway_token_required",
            message = "Gateway API token 未配置。",
        )

    private fun localSearchKeyRequiredError(): ToolError =
        ToolError(
            code = "local_search_key_required",
            message = "搜索 API Key 未配置。",
        )

    private fun Throwable.toToolError(
        fallbackCode: String,
        fallbackMessage: String,
    ): ToolError =
        when (this) {
            is GatewayHttpException -> ToolError(
                code = gatewayCode,
                message = message ?: fallbackMessage,
                statusCode = statusCode,
                retryable = statusCode == 429 || statusCode in 500..599,
            )
            is LocalSearchHttpException -> ToolError(
                code = code,
                message = message ?: fallbackMessage,
                statusCode = statusCode,
                retryable = statusCode == 429 || statusCode in 500..599,
            )
            else -> ToolError(
                code = fallbackCode,
                message = message ?: fallbackMessage,
            )
        }

    private fun String.toInputSummary(label: String): String {
        val normalized = replace(Regex("\\s+"), " ").trim()
        val preview = normalized.take(120)
        return if (normalized.length > preview.length) {
            "$label: $preview..."
        } else {
            "$label: $preview"
        }
    }

    private fun Instant.durationUntilMs(finishedAt: Instant): Long =
        (finishedAt.toEpochMilli() - toEpochMilli()).coerceAtLeast(0)

    private fun emptySearchOutputJson(query: String, fetchedAt: Instant): String =
        toolsJson.encodeToString(
            SearchOutputJson(
                query = query,
                fetchedAt = fetchedAt.toString(),
                results = emptyList(),
            ),
        )

    private fun emptySandboxOutputJson(): String =
        toolsJson.encodeToString(
            SandboxOutputJson(
                language = "python",
                stdout = "",
                stderr = "",
                exitCode = -1,
                durationMs = 0,
                timedOut = false,
                truncated = false,
            ),
        )

    private fun SandboxRunResponse.toJson(): String =
        toolsJson.encodeToString(
            toSandboxOutputJson(),
        )

    private fun SearchResponse.toJson(): String =
        toolsJson.encodeToString(
            SearchOutputJson(
                query = query,
                fetchedAt = fetchedAt.toString(),
                results = results.map { result -> result.toSearchResultOutputJson() },
            ),
        )

    private fun SearchResult.toSearchResultOutputJson(): SearchResultOutputJson =
        SearchResultOutputJson(
            title = title,
            summary = summary,
            url = url,
            source = source,
            publishedAt = publishedAt?.toString(),
        )

    private fun SandboxRunResponse.toSandboxOutputJson(): SandboxOutputJson =
        SandboxOutputJson(
            language = language,
            stdout = stdout,
            stderr = stderr,
            exitCode = exitCode,
            durationMs = durationMs,
            timedOut = timedOut,
            truncated = truncated,
        )

    private fun ToolPermissionLevel.label(): String =
        when (this) {
            ToolPermissionLevel.ReadOnly -> "只读"
            ToolPermissionLevel.Network -> "联网"
            ToolPermissionLevel.Execute -> "执行"
            ToolPermissionLevel.HighRisk -> "高风险"
        }

    private fun ToolsUiState.toLocalSearchConfig(): SearchConfig =
        SearchConfig(
            enabled = localSearchEnabled,
            provider = localSearchProvider,
            baseUrl = localSearchBaseUrlDraft,
            apiKey = localSearchApiKeyDraft,
            maxResults = localSearchMaxResultsOrDefault(),
            searchDepth = localSearchDepthDraft,
            topic = localSearchTopicDraft,
        )

    private fun ToolsUiState.localSearchMaxResultsOrDefault(): Int =
        localSearchMaxResultsOrNull() ?: 5

    private fun ToolsUiState.localSearchMaxResultsOrNull(): Int? =
        localSearchMaxResultsDraft.toIntOrNull()?.takeIf { it in 1..20 }

    private fun ToolDescriptor.isEnabled(): Boolean =
        _state.value.toolSettings.runtimeSettingFor(this).enabled

    private fun ToolDescriptor.requiresConfirmation(): Boolean {
        val setting = _state.value.toolSettings.runtimeSettingFor(this)
        return requiresConfirmation(setting.permissionPolicy)
    }

    private fun ToolDescriptor.fixedPermissionPolicyStatus(policy: ToolPermissionPolicy): String =
        when (policy) {
            ToolPermissionPolicy.AskEveryTime -> "$displayName 必须每次确认。"
            ToolPermissionPolicy.AllowWithoutPrompt -> "$displayName 固定为免确认。"
        }

    private fun ToolResult.extractSearchQuery(): String? =
        rawInputJson?.let { rawInput ->
            runCatching { toolsJson.decodeFromString<SearchInputJson>(rawInput).query }.getOrNull()
        } ?: inputSummary.substringAfter("query:", "").trim().takeIf(String::isNotBlank)

    private fun ToolResult.extractSandboxCode(): String? =
        rawInputJson?.let { rawInput ->
            runCatching { toolsJson.decodeFromString<SandboxInputJson>(rawInput).code }.getOrNull()
        } ?: inputSummary.substringAfter("python:", "").trim().takeIf(String::isNotBlank)
}

private val toolsJson = Json {
    explicitNulls = false
    encodeDefaults = true
}

private val RERUNNABLE_STATUSES = setOf(ToolStatus.Completed, ToolStatus.Failed)
private val REFILLABLE_STATUSES = setOf(ToolStatus.Completed, ToolStatus.Failed)
private val CHAT_REPLAY_STATUSES = setOf(ToolStatus.Completed, ToolStatus.Failed)
private val RERUNNABLE_TOOL_NAMES = setOf(
        "web_search_local",
        "web_search",
        "code_sandbox",
        "text_transform",
        "code_diff_preview",
        "provider_connection_test",
        "local_js",
        "file_read",
        "image_generation",
)
private val CHAT_REPLAY_TOOL_NAMES = setOf(
    "text_transform",
    "code_diff_preview",
        "provider_connection_test",
        "local_js",
        "file_read",
        "image_generation",
        "image_upload_to_model",
)
private val REFILLABLE_TOOL_NAMES = setOf(
    "time",
    "text_transform",
    "code_diff_preview",
    "provider_connection_test",
    "local_js",
    "file_read",
    "image_generation",
)

private fun sampleInputForToolName(toolName: String): String =
    when (toolName.canonicalToolName()) {
        "time" -> """{"timezone":"Asia/Shanghai"}"""
        "text_transform" -> """{"operation":"json_format","text":"{\"name\":\"demo\"}"}"""
        "code_diff_preview" ->
            """{"fileName":"snippet.kt","original":"fun answer() = \"old\"","modified":"fun answer() = \"new\""}"""
        "web_search_local", "web_search" -> """{"query":"AI 行业最新消息"}"""
        "image_generation" -> """{"prompt":"一张原生移动端 AI 工作台界面概念图","count":1}"""
        "image_upload_to_model" ->
            """{"imageUri":"<聊天输入栏中用户已选择的图片URI>","purpose":"分析这张图片"}"""
        "provider_connection_test" -> """{"providerId":"default"}"""
        "local_js" ->
            """{"language":"javascript","code":"return JSON.stringify({ ok: true })","timeoutMillis":1000,"outputLimitBytes":8192}"""
        "file_read" -> """{"uri":"content://<系统文件选择器返回的授权URI>","maxBytes":65536}"""
        "code_sandbox" -> """{"language":"python","code":"print(1 + 1)","timeoutSeconds":5}"""
        else -> "{}"
    }

@Serializable
private data class SearchInputJson(
    val query: String,
)

@Serializable
private data class SearchOutputJson(
    val query: String,
    val fetchedAt: String,
    val results: List<SearchResultOutputJson>,
)

@Serializable
private data class SearchResultOutputJson(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: String? = null,
)

@Serializable
private data class SandboxInputJson(
    val language: String,
    val code: String,
    val timeoutSeconds: Int,
)

@Serializable
private data class SandboxOutputJson(
    val language: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean,
    val truncated: Boolean,
)
