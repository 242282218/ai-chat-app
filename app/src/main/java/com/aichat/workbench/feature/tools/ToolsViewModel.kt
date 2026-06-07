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
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.canUsePermissionPolicy
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.tool.model.requiresConfirmation
import com.aichat.workbench.tool.model.runtimeSettingFor
import com.aichat.workbench.tool.search.LocalSearchClient
import com.aichat.workbench.tool.search.SearchResponse
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

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
                        gatewayHasApiToken = settings.hasApiToken,
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
                        localSearchHasApiKey = settings.hasApiKey,
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

    fun updateLocalSearchEnabled(value: Boolean) {
        _state.update { it.copy(localSearchEnabled = value) }
    }

    fun updateLocalSearchBaseUrl(value: String) {
        _state.update { it.copy(localSearchBaseUrlDraft = value) }
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

    fun saveGatewaySettings(newApiToken: String? = null) {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, status = null) }
            runCatching {
                val apiToken = newApiToken ?: settingsRepository.currentApiToken()
                settingsRepository.saveSettings(
                    enabled = current.gatewayEnabled,
                    baseUrl = current.gatewayBaseUrlDraft,
                    apiToken = apiToken,
                )
            }.onSuccess {
                val saved = settingsRepository.currentSettings()
                _state.update {
                    it.copy(
                        remoteTools = emptyList(),
                        gatewayHasApiToken = saved.hasApiToken,
                        status = "已保存",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "保存网关设置失败。") }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun saveLocalSearchSettings(newApiKey: String? = null) {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, status = null) }
            runCatching {
                val apiKey = newApiKey ?: searchSettingsRepository.currentApiKey()
                searchSettingsRepository.saveSettings(
                    enabled = current.localSearchEnabled,
                    provider = current.localSearchProvider,
                    baseUrl = current.localSearchBaseUrlDraft,
                    apiKey = apiKey,
                    maxResults = current.localSearchMaxResultsOrDefault(),
                    searchDepth = current.localSearchDepthDraft,
                    topic = current.localSearchTopicDraft,
                )
            }.onSuccess {
                val saved = searchSettingsRepository.currentSettings()
                _state.update {
                    it.copy(
                        localSearchHasApiKey = saved.hasApiKey,
                        status = "搜索设置已保存",
                    )
                }
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
                _state.update { it.copy(status = "Gateway ${health.status} · ${health.version}") }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "健康检查失败。") }
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
            if (!current.localSearchApiKeyAvailable) {
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
        if (!current.gatewayApiTokenAvailable) {
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
        if (!current.gatewayApiTokenAvailable) {
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
        if (sandboxTool.requiresConfirmation()) {
            _state.update {
                it.copy(
                    pendingConfirmation = sandboxTool,
                    pendingSandboxCode = code,
                )
            }
        } else {
            executeSandbox(code)
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
                localSearchClient.search(query, _state.value.toLocalSearchConfig(currentLocalSearchApiKey()))
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
                gatewayClient.search(baseUrl, query, currentGatewayApiToken())
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
                    apiToken = currentGatewayApiToken(),
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

    private suspend fun currentGatewayApiToken(): String =
        settingsRepository.currentApiToken()

    private suspend fun currentLocalSearchApiKey(): String =
        searchSettingsRepository.currentApiKey()

    private fun ToolDescriptor.isEnabled(): Boolean =
        _state.value.toolSettings.runtimeSettingFor(this).enabled

    private fun ToolDescriptor.requiresConfirmation(): Boolean {
        val setting = _state.value.toolSettings.runtimeSettingFor(this)
        return requiresConfirmation(setting.permissionPolicy)
    }
}
