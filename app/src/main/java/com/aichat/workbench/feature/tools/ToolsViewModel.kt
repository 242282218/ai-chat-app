package com.aichat.workbench.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.data.settings.GatewaySettingsRepository
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
import com.aichat.workbench.tool.gateway.SearchResponse
import com.aichat.workbench.tool.gateway.SearchResult
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.requiresConfirmation
import com.aichat.workbench.tool.registry.BuiltInToolRegistry
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
) {
    val tools: List<ToolDescriptor>
        get() = BuiltInToolRegistry.tools + remoteTools
}

class ToolsViewModel(
    private val settingsRepository: GatewaySettingsRepository,
    private val gatewayClient: GatewayClient,
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

    fun updateSearchQuery(value: String) {
        _state.update { it.copy(searchQuery = value, searchError = null) }
    }

    fun updateSandboxCode(value: String) {
        _state.update { it.copy(sandboxCode = value, sandboxError = null) }
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
                _state.update { it.copy(status = error.message ?: "加载工具清单失败。") }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun requestPermission(tool: ToolDescriptor) {
        if (!tool.permissionLevel.requiresConfirmation()) {
            _state.update { it.copy(status = "${tool.displayName} 是只读工具") }
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
        if (!current.gatewayEnabled) {
            _state.update { it.copy(status = "搜索前请启用网关。") }
            return
        }
        if (!current.gatewayBaseUrlDraft.isValidGatewayBaseUrl()) {
            _state.update { it.copy(status = "工具网关地址无效。") }
            return
        }
        val searchTool = current.remoteTools.firstOrNull { it.name == "web_search" }
        if (searchTool == null) {
            _state.update { it.copy(status = "搜索前请先加载工具清单。") }
            return
        }
        _state.update {
            it.copy(
                pendingConfirmation = searchTool,
                pendingSearchQuery = query,
            )
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
        val sandboxTool = current.remoteTools.firstOrNull { it.name == "code_sandbox" }
        if (sandboxTool == null) {
            _state.update { it.copy(status = "运行代码前请先加载工具清单。") }
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
        if (tool.name == "web_search" && query != null) {
            executeSearch(query)
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

    private fun executeSearch(query: String) {
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
                val toolResult = ToolResult(
                    id = toolCallId,
                    toolName = "web_search",
                    permissionLevel = ToolPermissionLevel.Network,
                    inputSummary = query.toInputSummary("query"),
                    output = ToolOutput.Json(response.toJson()),
                    status = ToolStatus.Completed,
                    startedAt = startedAt,
                    finishedAt = clock.instant(),
                    error = null,
                )
                toolInvocationRepository.saveToolResult(conversationId = null, toolResult = toolResult)
                _state.update {
                    it.copy(
                        searchResults = response.results,
                        searchFetchedAt = response.fetchedAt.toString(),
                        searchError = null,
                        status = "网络搜索返回 ${response.results.size} 个来源",
                    )
                }
            }.onFailure { error ->
                val toolError = error.toSearchToolError()
                val toolResult = ToolResult(
                    id = toolCallId,
                    toolName = "web_search",
                    permissionLevel = ToolPermissionLevel.Network,
                    inputSummary = query.toInputSummary("query"),
                    output = ToolOutput.Json(emptySearchOutputJson(query, startedAt)),
                    status = ToolStatus.Failed,
                    startedAt = startedAt,
                    finishedAt = clock.instant(),
                    error = toolError,
                )
                toolInvocationRepository.saveToolResult(conversationId = null, toolResult = toolResult)
                _state.update {
                    it.copy(
                        status = "网络搜索失败。",
                        searchError = toolError,
                    )
                }
            }
            _state.update { it.copy(isLoading = false) }
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
                val toolResult = ToolResult(
                    id = toolCallId,
                    toolName = "code_sandbox",
                    permissionLevel = ToolPermissionLevel.Execute,
                    inputSummary = code.toInputSummary("python"),
                    output = ToolOutput.Json(response.toJson()),
                    status = ToolStatus.Completed,
                    startedAt = startedAt,
                    finishedAt = clock.instant(),
                    error = null,
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
                val toolResult = ToolResult(
                    id = toolCallId,
                    toolName = "code_sandbox",
                    permissionLevel = ToolPermissionLevel.Execute,
                    inputSummary = code.toInputSummary("python"),
                    output = ToolOutput.Json(emptySandboxOutputJson()),
                    status = ToolStatus.Failed,
                    startedAt = startedAt,
                    finishedAt = clock.instant(),
                    error = toolError,
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

    private fun Throwable.toToolError(
        fallbackCode: String,
        fallbackMessage: String,
    ): ToolError =
        when (this) {
            is GatewayHttpException -> ToolError(
                code = gatewayCode,
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
}

private val toolsJson = Json {
    explicitNulls = false
    encodeDefaults = true
}

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
private data class SandboxOutputJson(
    val language: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean,
    val truncated: Boolean,
)
