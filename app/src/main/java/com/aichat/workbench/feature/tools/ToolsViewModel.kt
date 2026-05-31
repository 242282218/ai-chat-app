package com.aichat.workbench.feature.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.app.AppGraph
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
import com.aichat.workbench.tool.gateway.SearchResult
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.requiresConfirmation
import com.aichat.workbench.tool.registry.BuiltInToolRegistry
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ToolsUiState(
    val gatewayEnabled: Boolean = false,
    val gatewayBaseUrlDraft: String = "",
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
            settingsRepository.observeSettings().collect { settings ->
                _state.update {
                    it.copy(
                        gatewayEnabled = settings.enabled,
                        gatewayBaseUrlDraft = settings.baseUrl,
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

    fun updateSearchQuery(value: String) {
        _state.update { it.copy(searchQuery = value, searchError = null) }
    }

    fun updateSandboxCode(value: String) {
        _state.update { it.copy(sandboxCode = value, sandboxError = null) }
    }

    fun saveGatewaySettings() {
        val current = _state.value
        settingsRepository.saveSettings(
            enabled = current.gatewayEnabled,
            baseUrl = current.gatewayBaseUrlDraft,
        )
        _state.update { it.copy(status = "Saved") }
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
                _state.update { it.copy(status = error.message ?: "Gateway health check failed.") }
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
                        status = "Loaded ${manifest.tools.size} gateway tools",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "Load manifest failed.") }
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    fun requestPermission(tool: ToolDescriptor) {
        if (!tool.permissionLevel.requiresConfirmation()) {
            _state.update { it.copy(status = "${tool.displayName} is read-only") }
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
                    status = "Search query must not be blank.",
                    searchError = ToolError(
                        code = "invalid_query",
                        message = "Search query must not be blank.",
                    ),
                )
            }
            return
        }
        if (!current.gatewayEnabled) {
            _state.update { it.copy(status = "Enable Gateway before searching.") }
            return
        }
        val searchTool = current.remoteTools.firstOrNull { it.name == "web_search" }
        if (searchTool == null) {
            _state.update { it.copy(status = "Load gateway manifest before searching.") }
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
                    status = "Sandbox code must not be blank.",
                    sandboxError = ToolError(
                        code = "invalid_code",
                        message = "Sandbox code must not be blank.",
                    ),
                )
            }
            return
        }
        if (!current.gatewayEnabled) {
            _state.update { it.copy(status = "Enable Gateway before running code.") }
            return
        }
        val sandboxTool = current.remoteTools.firstOrNull { it.name == "code_sandbox" }
        if (sandboxTool == null) {
            _state.update { it.copy(status = "Load gateway manifest before running code.") }
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
                status = "Confirmed ${tool.permissionLevel.label()} tool: ${tool.displayName}",
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
                gatewayClient.search(baseUrl, query)
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
                        status = "Search returned ${response.results.size} sources",
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
                        status = "Search failed.",
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
                        status = "Sandbox finished with exit code ${response.exitCode}",
                    )
                }
            }.onFailure { error ->
                val toolError = error.toToolError(
                    fallbackCode = "sandbox_failed",
                    fallbackMessage = "Sandbox run failed.",
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
                        status = "Sandbox failed.",
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
            fallbackMessage = "Search failed.",
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

    private fun emptySearchOutputJson(query: String, fetchedAt: java.time.Instant): String =
        JSONObject()
            .put("query", query)
            .put("fetchedAt", fetchedAt.toString())
            .put("results", JSONArray())
            .toString()

    private fun emptySandboxOutputJson(): String =
        JSONObject()
            .put("language", "python")
            .put("stdout", "")
            .put("stderr", "")
            .put("exitCode", -1)
            .put("durationMs", 0)
            .put("timedOut", false)
            .put("truncated", false)
            .toString()

    private fun SandboxRunResponse.toJson(): String =
        JSONObject()
            .put("language", language)
            .put("stdout", stdout)
            .put("stderr", stderr)
            .put("exitCode", exitCode)
            .put("durationMs", durationMs)
            .put("timedOut", timedOut)
            .put("truncated", truncated)
            .toString()

    private fun com.aichat.workbench.tool.gateway.SearchResponse.toJson(): String {
        val resultsJson = JSONArray()
        results.forEach { result ->
            resultsJson.put(
                JSONObject()
                    .put("title", result.title)
                    .put("summary", result.summary)
                    .put("url", result.url)
                    .put("source", result.source)
                    .put("publishedAt", result.publishedAt?.toString()),
            )
        }
        return JSONObject()
            .put("query", query)
            .put("fetchedAt", fetchedAt.toString())
            .put("results", resultsJson)
            .toString()
    }

    private fun ToolPermissionLevel.label(): String =
        when (this) {
            ToolPermissionLevel.ReadOnly -> "read-only"
            ToolPermissionLevel.Network -> "network"
            ToolPermissionLevel.Execute -> "execute"
            ToolPermissionLevel.HighRisk -> "high-risk"
        }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ToolsViewModel(
                    settingsRepository = AppGraph.gatewaySettingsRepository,
                    gatewayClient = AppGraph.gatewayClient,
                    toolInvocationRepository = AppGraph.toolInvocationRepository,
                    clock = AppGraph.clock,
                ) as T
        }
    }
}
