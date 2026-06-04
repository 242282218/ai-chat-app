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
    val toolHistoryToolFilter: String? = null,
    val toolHistoryStatusFilter: ToolStatus? = null,
    val toolSettings: Map<String, ToolRuntimeSetting> = emptyMap(),
) {
    val tools: List<ToolDescriptor>
        get() = BuiltInToolRegistry.tools + remoteTools

    val enabledTools: List<ToolDescriptor>
        get() = tools.filter { tool -> toolSettings.runtimeSettingFor(tool).enabled }

    val filteredToolHistory: List<ToolResult>
        get() = toolHistory
            .filter { result -> toolHistoryToolFilter == null || result.toolName == toolHistoryToolFilter }
            .filter { result -> toolHistoryStatusFilter == null || result.status == toolHistoryStatusFilter }
}

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

    fun updateToolHistoryToolFilter(value: String?) {
        _state.update { it.copy(toolHistoryToolFilter = value?.takeIf(String::isNotBlank)) }
    }

    fun updateToolHistoryStatusFilter(value: ToolStatus?) {
        _state.update { it.copy(toolHistoryStatusFilter = value) }
    }

    fun updateToolEnabled(toolName: String, enabled: Boolean) {
        toolSettingsRepository.setToolEnabled(toolName, enabled)
    }

    fun updateToolPermissionPolicy(toolName: String, policy: ToolPermissionPolicy) {
        toolSettingsRepository.setPermissionPolicy(toolName, policy)
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
            )
            is LocalSearchHttpException -> ToolError(
                code = code,
                message = message ?: fallbackMessage,
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
            SandboxOutputJson(
                language = language,
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode,
                durationMs = durationMs,
                timedOut = timedOut,
                truncated = truncated,
            ),
        )

    private fun SearchResponse.toJson(): String =
        toolsJson.encodeToString(
            SearchOutputJson(
                query = query,
                fetchedAt = fetchedAt.toString(),
                results = results.map { result ->
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
}

private val toolsJson = Json {
    explicitNulls = false
    encodeDefaults = true
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
