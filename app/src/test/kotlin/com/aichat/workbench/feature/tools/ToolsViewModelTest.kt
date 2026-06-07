package com.aichat.workbench.feature.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.settings.GatewaySettingsRepository
import com.aichat.workbench.data.settings.SearchSettingsRepository
import com.aichat.workbench.data.settings.ToolSettingsRepository
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.runtimeSettingFor
import com.aichat.workbench.tool.search.LocalSearchClient
import com.aichat.workbench.tool.search.LocalSearchHttpException
import com.aichat.workbench.tool.search.SearchConfig
import com.aichat.workbench.tool.search.SearchProvider
import com.aichat.workbench.tool.search.SearchResponse
import com.aichat.workbench.tool.search.SearchResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ToolsViewModelTest {
    @get:Rule
    val mainDispatcherRule = ToolsMainDispatcherRule()

    private lateinit var context: Context
    private lateinit var gatewayPreferences: android.content.SharedPreferences
    private lateinit var searchPreferences: android.content.SharedPreferences
    private lateinit var toolPreferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        gatewayPreferences = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)
        searchPreferences = context.getSharedPreferences("search_settings", Context.MODE_PRIVATE)
        toolPreferences = context.getSharedPreferences("tool_settings", Context.MODE_PRIVATE)
        gatewayPreferences.edit().clear().commit()
        searchPreferences.edit().clear().commit()
        toolPreferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        gatewayPreferences.edit().clear().commit()
        searchPreferences.edit().clear().commit()
        toolPreferences.edit().clear().commit()
    }

    @Test
    fun requestSearchRequiresGatewayApiToken() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.updateGatewayEnabled(true)
        viewModel.updateGatewayBaseUrl("https://gateway.example.com")
        viewModel.updateSearchQuery("release notes")
        viewModel.requestSearch()

        assertEquals("Gateway API token 未配置。", viewModel.state.value.status)
        assertEquals("gateway_token_required", viewModel.state.value.searchError?.code)
        assertNull(viewModel.state.value.pendingConfirmation)
    }

    @Test
    fun requestSandboxRunRequiresGatewayApiToken() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.updateGatewayEnabled(true)
        viewModel.updateGatewayBaseUrl("https://gateway.example.com")
        viewModel.updateSandboxCode("print(1)")
        viewModel.requestSandboxRun()

        assertEquals("Gateway API token 未配置。", viewModel.state.value.status)
        assertEquals("gateway_token_required", viewModel.state.value.sandboxError?.code)
        assertNull(viewModel.state.value.pendingConfirmation)
    }

    @Test
    fun failedManifestFetchClearsStaleRemoteTools() = runTest(mainDispatcherRule.testDispatcher) {
        val server = MockWebServer()
        server.enqueue(manifestResponse("web_search"))
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("""{"code":"gateway_unavailable","message":"Gateway unavailable"}"""),
        )
        server.start()
        try {
            val viewModel = viewModel()
            advanceUntilIdle()
            viewModel.updateGatewayEnabled(true)
            viewModel.updateGatewayBaseUrl(server.url("/").toString())

            viewModel.fetchManifest()
            advanceUntilIdle()
            viewModel.awaitNotLoading()

            assertEquals(listOf("web_search"), viewModel.state.value.remoteTools.map { it.name })
            assertEquals("已加载 1 个网关工具", viewModel.state.value.status)

            viewModel.fetchManifest()
            advanceUntilIdle()
            viewModel.awaitNotLoading()

            assertEquals(emptyList<String>(), viewModel.state.value.remoteTools.map { it.name })
            assertEquals("Gateway unavailable", viewModel.state.value.status)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun requestLocalSearchRequiresApiKey() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.updateLocalSearchEnabled(true)
        viewModel.updateLocalSearchBaseUrl("https://api.tavily.com")
        viewModel.updateSearchQuery("release notes")
        viewModel.requestSearch()

        assertEquals("搜索 API Key 未配置。", viewModel.state.value.status)
        assertEquals("local_search_key_required", viewModel.state.value.searchError?.code)
        assertNull(viewModel.state.value.pendingConfirmation)
    }

    @Test
    fun savedLocalSearchApiKeyIsClearedFromUiStateAndUsedForExecution() =
        runTest(mainDispatcherRule.testDispatcher) {
            val localSearchClient = RecordingLocalSearchClient()
            val viewModel = viewModel(localSearchClient = localSearchClient)
            advanceUntilIdle()

            viewModel.updateToolPermissionPolicy("local-search", ToolPermissionPolicy.AllowWithoutPrompt)
            advanceUntilIdle()
            viewModel.updateLocalSearchEnabled(true)
            viewModel.updateLocalSearchBaseUrl("https://api.tavily.com")
            viewModel.saveLocalSearchSettings("stored-search-key")
            advanceUntilIdle()
            viewModel.awaitNotLoading()

            assertEquals(true, viewModel.state.value.localSearchHasApiKey)

            viewModel.updateSearchQuery("release notes")
            viewModel.requestSearch()
            advanceUntilIdle()
            viewModel.awaitNotLoading()

            assertEquals("stored-search-key", localSearchClient.requests.single().config.apiKey)
        }

    @Test
    fun clearLocalSearchApiKeyRemovesSavedKey() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.updateLocalSearchEnabled(true)
        viewModel.updateLocalSearchBaseUrl("https://api.tavily.com")
        viewModel.saveLocalSearchSettings("stored-search-key")
        advanceUntilIdle()
        viewModel.awaitNotLoading()

        viewModel.saveLocalSearchSettings("")
        advanceUntilIdle()
        viewModel.awaitNotLoading()
        viewModel.updateSearchQuery("release notes")
        viewModel.requestSearch()

        assertEquals(false, viewModel.state.value.localSearchHasApiKey)
        assertEquals("搜索 API Key 未配置。", viewModel.state.value.status)
        assertEquals("local_search_key_required", viewModel.state.value.searchError?.code)
    }

    @Test
    fun savedGatewayTokenIsClearedFromUiStateAndUsedForSearchRequests() =
        runTest(mainDispatcherRule.testDispatcher) {
            val server = MockWebServer()
            server.enqueue(manifestResponse("web_search"))
            server.enqueue(searchResponse())
            server.start()
            try {
                val viewModel = viewModel()
                advanceUntilIdle()
                viewModel.updateGatewayEnabled(true)
                viewModel.updateGatewayBaseUrl(server.url("/").toString())
                viewModel.saveGatewaySettings("stored-gateway-token")
                advanceUntilIdle()
                viewModel.awaitNotLoading()

                assertEquals(true, viewModel.state.value.gatewayHasApiToken)

                viewModel.fetchManifest()
                advanceUntilIdle()
                viewModel.awaitNotLoading()
                viewModel.updateSearchQuery("release notes")
                viewModel.requestSearch()

                assertEquals("web_search", viewModel.state.value.pendingConfirmation?.name)

                viewModel.confirmPermission()
                advanceUntilIdle()
                viewModel.awaitNotLoading()

                assertEquals(null, server.takeRequest().getHeader("Authorization"))
                assertEquals("Bearer stored-gateway-token", server.takeRequest().getHeader("Authorization"))
            } finally {
                server.shutdown()
            }
        }

    @Test
    fun clearGatewayTokenRemovesSavedToken() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.updateGatewayEnabled(true)
        viewModel.updateGatewayBaseUrl("https://gateway.example.com")
        viewModel.saveGatewaySettings("stored-gateway-token")
        advanceUntilIdle()
        viewModel.awaitNotLoading()

        viewModel.saveGatewaySettings("")
        advanceUntilIdle()
        viewModel.awaitNotLoading()
        viewModel.updateSearchQuery("release notes")
        viewModel.requestSearch()

        assertEquals(false, viewModel.state.value.gatewayHasApiToken)
        assertEquals("Gateway API token 未配置。", viewModel.state.value.status)
        assertEquals("gateway_token_required", viewModel.state.value.searchError?.code)
    }

    @Test
    fun localSearchExecutesAndSavesToolResult() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val localSearchClient = RecordingLocalSearchClient()
        val viewModel = viewModel(
            toolInvocationRepository = repository,
            localSearchClient = localSearchClient,
        )
        advanceUntilIdle()

        viewModel.updateLocalSearchEnabled(true)
        viewModel.updateLocalSearchBaseUrl("https://api.tavily.com")
        viewModel.saveLocalSearchSettings("test-search-key")
        advanceUntilIdle()
        viewModel.awaitNotLoading()
        viewModel.updateLocalSearchMaxResults("3")
        viewModel.updateLocalSearchDepth("advanced")
        viewModel.updateLocalSearchTopic("news")
        viewModel.updateSearchQuery("AI release notes")
        viewModel.requestSearch()

        assertEquals("web_search_local", viewModel.state.value.pendingConfirmation?.name)

        viewModel.confirmPermission()
        advanceUntilIdle()
        viewModel.awaitNotLoading()

        val request = localSearchClient.requests.single()
        assertEquals("AI release notes", request.query)
        assertEquals(SearchProvider.Tavily, request.config.provider)
        assertEquals("https://api.tavily.com", request.config.baseUrl)
        assertEquals("test-search-key", request.config.apiKey)
        assertEquals(3, request.config.maxResults)
        assertEquals("advanced", request.config.searchDepth)
        assertEquals("news", request.config.topic)
        assertEquals("本地搜索返回 1 个来源", viewModel.state.value.status)
        assertEquals("Result title", viewModel.state.value.searchResults.single().title)

        val saved = repository.savedResults.value.single()
        assertEquals("web_search_local", saved.toolName)
        assertEquals(ToolPermissionLevel.Network, saved.permissionLevel)
        assertEquals(ToolStatus.Completed, saved.status)
        assertNull(saved.error)
        assertEquals("""{"query":"AI release notes"}""", saved.rawInputJson)
        assertTrue(saved.rawOutputJson.orEmpty().contains("Result title"))
        assertEquals(0L, saved.durationMs)
    }

    @Test
    fun localSearchFailureSavesHttpDiagnosticMetadata() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val localSearchClient = RecordingLocalSearchClient(
            error = LocalSearchHttpException(
                statusCode = 429,
                code = "local_search_http_429",
                message = "Rate limit exceeded",
            ),
        )
        val viewModel = viewModel(
            toolInvocationRepository = repository,
            localSearchClient = localSearchClient,
        )
        advanceUntilIdle()

        viewModel.updateToolPermissionPolicy("local-search", ToolPermissionPolicy.AllowWithoutPrompt)
        advanceUntilIdle()
        viewModel.updateLocalSearchEnabled(true)
        viewModel.updateLocalSearchBaseUrl("https://api.tavily.com")
        viewModel.saveLocalSearchSettings("test-search-key")
        advanceUntilIdle()
        viewModel.awaitNotLoading()
        viewModel.updateSearchQuery("AI release notes")
        viewModel.requestSearch()
        advanceUntilIdle()
        viewModel.awaitNotLoading()

        val error = viewModel.state.value.searchError
        val saved = repository.savedResults.value.single()

        assertEquals("本地搜索失败。", viewModel.state.value.status)
        assertEquals("local_search_http_429", error?.code)
        assertEquals(429, error?.statusCode)
        assertEquals(true, error?.retryable)
        assertEquals(ToolStatus.Failed, saved.status)
        assertEquals("local_search_http_429", saved.error?.code)
        assertEquals(429, saved.error?.statusCode)
        assertEquals(true, saved.error?.retryable)
    }

    @Test
    fun localSearchSkipsConfirmationWhenPolicyAllows() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val localSearchClient = RecordingLocalSearchClient()
        val viewModel = viewModel(
            toolInvocationRepository = repository,
            localSearchClient = localSearchClient,
        )
        advanceUntilIdle()

        viewModel.updateToolPermissionPolicy("local-search", ToolPermissionPolicy.AllowWithoutPrompt)
        advanceUntilIdle()
        viewModel.updateLocalSearchEnabled(true)
        viewModel.updateLocalSearchBaseUrl("https://api.tavily.com")
        viewModel.saveLocalSearchSettings("test-search-key")
        advanceUntilIdle()
        viewModel.awaitNotLoading()
        viewModel.updateSearchQuery("AI release notes")
        viewModel.requestSearch()
        advanceUntilIdle()
        viewModel.awaitNotLoading()

        assertNull(viewModel.state.value.pendingConfirmation)
        assertEquals("AI release notes", localSearchClient.requests.single().query)
        assertEquals("本地搜索返回 1 个来源", viewModel.state.value.status)
        assertEquals("web_search_local", repository.savedResults.value.single().toolName)
    }

    @Test
    fun requestLocalSearchStopsWhenToolIsDisabled() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val localSearchClient = RecordingLocalSearchClient()
        val viewModel = viewModel(
            toolInvocationRepository = repository,
            localSearchClient = localSearchClient,
        )
        advanceUntilIdle()

        viewModel.updateToolEnabled("local-search", false)
        advanceUntilIdle()
        viewModel.updateLocalSearchEnabled(true)
        viewModel.updateLocalSearchBaseUrl("https://api.tavily.com")
        viewModel.saveLocalSearchSettings("test-search-key")
        advanceUntilIdle()
        viewModel.awaitNotLoading()
        viewModel.updateSearchQuery("AI release notes")
        viewModel.requestSearch()
        advanceUntilIdle()

        assertEquals("本地搜索工具已禁用。", viewModel.state.value.status)
        assertNull(viewModel.state.value.pendingConfirmation)
        assertEquals(emptyList<LocalSearchRequest>(), localSearchClient.requests)
        assertEquals(emptyList<ToolResult>(), repository.savedResults.value)
    }

    @Test
    fun observesAndUpdatesToolSettings() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.updateToolEnabled("local-search", false)
        viewModel.updateToolPermissionPolicy("local-search", ToolPermissionPolicy.AllowWithoutPrompt)
        advanceUntilIdle()

        val setting = viewModel.state.value.toolSettings.runtimeSettingFor("web_search_local")
        assertEquals(false, setting.enabled)
        assertEquals(ToolPermissionPolicy.AllowWithoutPrompt, setting.permissionPolicy)
        assertEquals(false, viewModel.state.value.enabledTools.any { it.name == "web_search_local" })

        val nextViewModel = viewModel()
        advanceUntilIdle()

        val persisted = nextViewModel.state.value.toolSettings.runtimeSettingFor("web-search-local")
        assertEquals(false, persisted.enabled)
        assertEquals(ToolPermissionPolicy.AllowWithoutPrompt, persisted.permissionPolicy)
    }

    @Test
    fun highRiskToolsCannotPersistAllowWithoutPromptPolicy() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        listOf("local_js", "file_read", "image_upload_to_model").forEach { toolName ->
            viewModel.updateToolPermissionPolicy(toolName, ToolPermissionPolicy.AllowWithoutPrompt)
            advanceUntilIdle()

            val setting = viewModel.state.value.toolSettings.runtimeSettingFor(toolName)
            assertEquals(ToolPermissionPolicy.AskEveryTime, setting.permissionPolicy)
        }
        assertEquals("图片发送给模型 必须每次确认。", viewModel.state.value.status)

        val nextViewModel = viewModel()
        advanceUntilIdle()

        listOf("local_js", "file_read", "image_upload_to_model").forEach { toolName ->
            val persisted = nextViewModel.state.value.toolSettings.runtimeSettingFor(toolName)
            assertEquals(ToolPermissionPolicy.AskEveryTime, persisted.permissionPolicy)
        }
    }

    @Test
    fun readOnlyToolsKeepFixedAllowWithoutPromptPolicy() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.updateToolPermissionPolicy("time", ToolPermissionPolicy.AskEveryTime)
        advanceUntilIdle()

        val setting = viewModel.state.value.toolSettings.runtimeSettingFor("time")
        assertEquals(ToolPermissionPolicy.AllowWithoutPrompt, setting.permissionPolicy)
        assertEquals("本机时间 固定为免确认。", viewModel.state.value.status)
    }

    @Test
    fun observesAndFiltersToolHistory() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val viewModel = viewModel(toolInvocationRepository = repository)
        advanceUntilIdle()

        val completedSearch = toolResult(
            id = "search-1",
            toolName = "web_search_local",
            status = ToolStatus.Completed,
            conversationId = "conversation-a",
        )
        val failedSearch = toolResult(
            id = "search-2",
            toolName = "web_search_local",
            status = ToolStatus.Failed,
            error = ToolError(code = "local_search_http_429", message = "Rate limit exceeded"),
            conversationId = "conversation-b",
        )
        val completedTime = toolResult(
            id = "time-1",
            toolName = "time",
            status = ToolStatus.Completed,
            conversationId = "conversation-a",
        )
        repository.savedResults.value = listOf(completedSearch, failedSearch, completedTime)
        advanceUntilIdle()

        assertEquals(listOf(completedSearch, failedSearch, completedTime), viewModel.state.value.toolHistory)
        assertEquals(listOf(completedSearch, failedSearch, completedTime), viewModel.state.value.filteredToolHistory)

        viewModel.updateToolHistoryConversationFilter("conversation-a")

        assertEquals(listOf(completedSearch, completedTime), viewModel.state.value.filteredToolHistory)

        viewModel.updateToolHistoryToolFilter("web_search_local")

        assertEquals(listOf(completedSearch), viewModel.state.value.filteredToolHistory)

        viewModel.updateToolHistoryStatusFilter(ToolStatus.Failed)

        assertEquals(emptyList<ToolResult>(), viewModel.state.value.filteredToolHistory)

        viewModel.updateToolHistoryConversationFilter("conversation-b")

        assertEquals(listOf(failedSearch), viewModel.state.value.filteredToolHistory)

        viewModel.updateToolHistoryToolFilter(null)

        assertEquals(listOf(failedSearch), viewModel.state.value.filteredToolHistory)
    }

    @Test
    fun toolHistoryToolFilterUsesCanonicalToolNames() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val viewModel = viewModel(toolInvocationRepository = repository)
        advanceUntilIdle()

        val canonicalSearch = toolResult(
            id = "search-1",
            toolName = "web_search_local",
            status = ToolStatus.Completed,
        )
        val aliasSearch = toolResult(
            id = "search-2",
            toolName = "local-web-search",
            status = ToolStatus.Completed,
        )
        val sandbox = toolResult(
            id = "sandbox-1",
            toolName = "sandbox",
            status = ToolStatus.Completed,
        )
        repository.savedResults.value = listOf(canonicalSearch, aliasSearch, sandbox)
        advanceUntilIdle()

        viewModel.updateToolHistoryToolFilter("local-web-search")

        assertEquals("web_search_local", viewModel.state.value.toolHistoryToolFilter)
        assertEquals(listOf(canonicalSearch, aliasSearch), viewModel.state.value.filteredToolHistory)
    }

    @Test
    fun latestToolResultForUsesCanonicalToolNameAndNewestStartedAt() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val viewModel = viewModel(toolInvocationRepository = repository)
        advanceUntilIdle()

        val olderSearch = toolResult(
            id = "search-older",
            toolName = "local-web-search",
            status = ToolStatus.Completed,
            startedAt = Instant.parse("2026-06-01T00:00:00Z"),
        )
        val newestSearch = toolResult(
            id = "search-newest",
            toolName = "web_search_local",
            status = ToolStatus.Failed,
            startedAt = Instant.parse("2026-06-01T00:02:00Z"),
            durationMs = 456,
        )
        val sandbox = toolResult(
            id = "sandbox-1",
            toolName = "code_sandbox",
            status = ToolStatus.Completed,
            startedAt = Instant.parse("2026-06-01T00:03:00Z"),
        )
        repository.savedResults.value = listOf(olderSearch, newestSearch, sandbox)
        advanceUntilIdle()

        val latest = viewModel.state.value.latestToolResultFor(toolDescriptor(name = "local-web-search"))

        assertEquals(newestSearch, latest)
    }

    @Test
    fun latestToolResultForReturnsNullWhenToolHasNoHistory() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val viewModel = viewModel(toolInvocationRepository = repository)
        advanceUntilIdle()

        repository.savedResults.value = listOf(
            toolResult(id = "search-1", toolName = "web_search_local", status = ToolStatus.Completed),
        )
        advanceUntilIdle()

        assertNull(viewModel.state.value.latestToolResultFor(toolDescriptor(name = "time")))
    }

    @Test
    fun recentToolResultsForUsesCanonicalNameNewestFirstAndLimit() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val viewModel = viewModel(toolInvocationRepository = repository)
        advanceUntilIdle()

        val searchResults = (0..11).map { index ->
            toolResult(
                id = "search-$index",
                toolName = if (index % 2 == 0) "local-web-search" else "web_search_local",
                status = ToolStatus.Completed,
                startedAt = Instant.parse("2026-06-01T00:${index.toString().padStart(2, '0')}:00Z"),
            )
        }
        val timeResult = toolResult(
            id = "time-1",
            toolName = "time",
            status = ToolStatus.Completed,
            startedAt = Instant.parse("2026-06-01T00:30:00Z"),
        )
        repository.savedResults.value = searchResults + timeResult
        advanceUntilIdle()

        val recent = viewModel.state.value.recentToolResultsFor(toolDescriptor(name = "web_search_local"))

        assertEquals(10, recent.size)
        assertEquals("search-11", recent.first().id.value)
        assertEquals("search-2", recent.last().id.value)
        assertTrue(recent.none { it.toolName == "time" })
    }

    @Test
    fun recentToolResultsForHandlesZeroLimit() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val viewModel = viewModel(toolInvocationRepository = repository)
        advanceUntilIdle()

        repository.savedResults.value = listOf(
            toolResult(id = "search-1", toolName = "web_search_local", status = ToolStatus.Completed),
        )
        advanceUntilIdle()

        assertEquals(
            emptyList<ToolResult>(),
            viewModel.state.value.recentToolResultsFor(toolDescriptor(name = "local-web-search"), limit = 0),
        )
    }

    @Test
    fun latestToolDebugActionsRespectRerunAndRefillPolicies() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val viewModel = viewModel(toolInvocationRepository = repository)
        advanceUntilIdle()

        val sandbox = toolResult(
            id = "sandbox-1",
            toolName = "sandbox",
            status = ToolStatus.Completed,
            rawInputJson = """{"language":"python","code":"print(1)","timeoutSeconds":5}""",
        )
        val fileRead = toolResult(
            id = "file-1",
            toolName = "read_file",
            status = ToolStatus.Completed,
            rawInputJson = """{"uri":"content://docs/a.md","maxBytes":65536}""",
        )
        val customTool = toolResult(
            id = "custom-1",
            toolName = "custom_tool",
            status = ToolStatus.Completed,
            rawInputJson = """{"value":1}""",
        )
        repository.savedResults.value = listOf(sandbox, fileRead, customTool)
        advanceUntilIdle()

        val state = viewModel.state.value

        assertEquals(sandbox, state.latestRerunnableToolResultFor(toolDescriptor(name = "code_sandbox")))
        assertNull(state.latestRefillableToolResultFor(toolDescriptor(name = "code_sandbox")))
        assertEquals(fileRead, state.latestRerunnableToolResultFor(toolDescriptor(name = "file_read")))
        assertEquals(fileRead, state.latestRefillableToolResultFor(toolDescriptor(name = "file_read")))
        assertNull(state.latestRerunnableToolResultFor(toolDescriptor(name = "custom_tool")))
        assertNull(state.latestRefillableToolResultFor(toolDescriptor(name = "custom_tool")))
    }

    @Test
    fun latestDebugActionsSkipNewestNonActionableHistory() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val viewModel = viewModel(toolInvocationRepository = repository)
        advanceUntilIdle()

        val olderRunnable = toolResult(
            id = "js-runnable",
            toolName = "local_js",
            status = ToolStatus.Completed,
            rawInputJson = """{"language":"javascript","code":"return 1"}""",
            startedAt = Instant.parse("2026-06-01T00:00:00Z"),
        )
        val newerMissingInput = toolResult(
            id = "js-missing-input",
            toolName = "local_js",
            status = ToolStatus.Completed,
            rawInputJson = null,
            startedAt = Instant.parse("2026-06-01T00:01:00Z"),
        )
        val newestCancelled = toolResult(
            id = "js-cancelled",
            toolName = "local_js",
            status = ToolStatus.Cancelled,
            rawInputJson = """{"language":"javascript","code":"return 2"}""",
            startedAt = Instant.parse("2026-06-01T00:02:00Z"),
        )
        repository.savedResults.value = listOf(olderRunnable, newerMissingInput, newestCancelled)
        advanceUntilIdle()

        val state = viewModel.state.value

        assertEquals(newestCancelled, state.latestToolResultFor(toolDescriptor(name = "local_js")))
        assertEquals(olderRunnable, state.latestRerunnableToolResultFor(toolDescriptor(name = "local_js")))
        assertEquals(olderRunnable, state.latestRefillableToolResultFor(toolDescriptor(name = "local_js")))
    }

    @Test
    fun refillDeniedToolHistoryDoesNotPrepareChatDraft() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val denied = toolResult(
            id = "js-denied",
            toolName = "local_js",
            status = ToolStatus.Denied,
            rawInputJson = """{"language":"javascript","code":"return 1"}""",
        )

        assertEquals(false, viewModel.state.value.canRefillToolResult(denied))

        viewModel.refillToolResult(denied)
        advanceUntilIdle()

        assertEquals("local_js 当前状态不支持回填参数。", viewModel.state.value.status)
        assertNull(viewModel.state.value.refilledToolName)
        assertNull(viewModel.state.value.chatInstructionForRefilledTool())
    }

    @Test
    fun rerunLocalSearchRestoresRawInputAndExecutes() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val localSearchClient = RecordingLocalSearchClient()
        val viewModel = viewModel(
            toolInvocationRepository = repository,
            localSearchClient = localSearchClient,
        )
        advanceUntilIdle()

        viewModel.updateToolPermissionPolicy("local-search", ToolPermissionPolicy.AllowWithoutPrompt)
        advanceUntilIdle()
        viewModel.updateLocalSearchEnabled(true)
        viewModel.updateLocalSearchBaseUrl("https://api.tavily.com")
        viewModel.saveLocalSearchSettings("test-search-key")
        advanceUntilIdle()
        viewModel.awaitNotLoading()

        val historyResult = toolResult(
            id = "search-1",
            toolName = "web_search_local",
            status = ToolStatus.Completed,
            rawInputJson = """{"query":"rerun query"}""",
        )

        viewModel.rerunToolResult(historyResult)
        advanceUntilIdle()
        viewModel.awaitNotLoading()

        assertEquals("rerun query", viewModel.state.value.searchQuery)
        assertEquals("rerun query", localSearchClient.requests.single().query)
        assertEquals("web_search_local", repository.savedResults.value.single().toolName)
    }

    @Test
    fun rerunLocalSearchAcceptsCanonicalAliasFromHistory() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val localSearchClient = RecordingLocalSearchClient()
        val viewModel = viewModel(
            toolInvocationRepository = repository,
            localSearchClient = localSearchClient,
        )
        advanceUntilIdle()

        viewModel.updateToolPermissionPolicy("local-search", ToolPermissionPolicy.AllowWithoutPrompt)
        advanceUntilIdle()
        viewModel.updateLocalSearchEnabled(true)
        viewModel.updateLocalSearchBaseUrl("https://api.tavily.com")
        viewModel.saveLocalSearchSettings("test-search-key")
        advanceUntilIdle()
        viewModel.awaitNotLoading()

        val historyResult = toolResult(
            id = "search-alias-1",
            toolName = "local-web-search",
            status = ToolStatus.Completed,
            rawInputJson = """{"query":"alias query"}""",
        )

        viewModel.rerunToolResult(historyResult)
        advanceUntilIdle()
        viewModel.awaitNotLoading()

        assertEquals("alias query", viewModel.state.value.searchQuery)
        assertEquals("alias query", localSearchClient.requests.single().query)
        assertEquals("web_search_local", repository.savedResults.value.single().toolName)
    }

    @Test
    fun rerunDeniedToolHistoryDoesNotExecute() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val localSearchClient = RecordingLocalSearchClient()
        val viewModel = viewModel(
            toolInvocationRepository = repository,
            localSearchClient = localSearchClient,
        )
        advanceUntilIdle()

        viewModel.updateLocalSearchEnabled(true)
        viewModel.updateLocalSearchBaseUrl("https://api.tavily.com")
        viewModel.saveLocalSearchSettings("test-search-key")
        advanceUntilIdle()
        viewModel.awaitNotLoading()

        viewModel.rerunToolResult(
            toolResult(
                id = "search-denied",
                toolName = "web_search_local",
                status = ToolStatus.Denied,
                rawInputJson = """{"query":"should not run"}""",
            ),
        )
        advanceUntilIdle()

        assertEquals("web_search_local 当前状态不支持重跑。", viewModel.state.value.status)
        assertEquals(emptyList<LocalSearchRequest>(), localSearchClient.requests)
        assertEquals(emptyList<ToolResult>(), repository.savedResults.value)
    }

    @Test
    fun toolHistoryRerunAvailabilityMatchesSupportedTools() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            true,
            viewModel.state.value.canRerunToolResult(
                toolResult(id = "local-search", toolName = "web_search_local", status = ToolStatus.Completed),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRerunToolResult(
                toolResult(id = "local-search-failed", toolName = "web_search_local", status = ToolStatus.Failed),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRerunToolResult(
                toolResult(id = "local-search-alias", toolName = "local-web-search", status = ToolStatus.Completed),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRerunToolResult(
                toolResult(id = "gateway-search", toolName = "web_search", status = ToolStatus.Completed),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRerunToolResult(
                toolResult(id = "sandbox-alias", toolName = "sandbox", status = ToolStatus.Completed),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRerunToolResult(
                toolResult(id = "sandbox", toolName = "code_sandbox", status = ToolStatus.Completed),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRerunToolResult(
                toolResult(
                    id = "file-rerun",
                    toolName = "file_read",
                    status = ToolStatus.Completed,
                    rawInputJson = """{"uri":"content://docs/a.md","maxBytes":65536}""",
                ),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRerunToolResult(
                toolResult(
                    id = "local-js",
                    toolName = "local_js",
                    status = ToolStatus.Completed,
                    rawInputJson = """{"language":"javascript","code":"return 1"}""",
                ),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRerunToolResult(
                toolResult(
                    id = "image",
                    toolName = "image_generation",
                    status = ToolStatus.Failed,
                    rawInputJson = """{"prompt":"mobile AI workbench"}""",
                ),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRerunToolResult(
                toolResult(
                    id = "provider-test",
                    toolName = "provider_connection_test",
                    status = ToolStatus.Completed,
                    rawInputJson = """{"providerId":"default"}""",
                ),
            ),
        )
        assertEquals(
            false,
            viewModel.state.value.canRerunToolResult(
                toolResult(id = "local-js-missing-input", toolName = "local_js", status = ToolStatus.Completed),
            ),
        )
        assertEquals(
            false,
            viewModel.state.value.canRerunToolResult(
                toolResult(id = "local-search-denied", toolName = "web_search_local", status = ToolStatus.Denied),
            ),
        )
        assertEquals(
            false,
            viewModel.state.value.canRerunToolResult(
                toolResult(id = "local-search-cancelled", toolName = "web_search_local", status = ToolStatus.Cancelled),
            ),
        )
        assertEquals(
            false,
            viewModel.state.value.canRerunToolResult(
                toolResult(id = "local-search-cancelled", toolName = "web_search_local", status = ToolStatus.Cancelled),
            ),
        )
        assertEquals(
            false,
            viewModel.state.value.canRerunToolResult(
                toolResult(
                    id = "image-upload",
                    toolName = "image_upload_to_model",
                    status = ToolStatus.Completed,
                    rawInputJson = """{"imageUri":"content://image/1"}""",
                ),
            ),
        )
    }

    @Test
    fun rerunChatBoundLocalToolRefillsArgumentsWithoutExecuting() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val localSearchClient = RecordingLocalSearchClient()
        val viewModel = viewModel(
            toolInvocationRepository = repository,
            localSearchClient = localSearchClient,
        )
        advanceUntilIdle()

        val historyResult = toolResult(
            id = "local-js-1",
            toolName = "local-javascript",
            status = ToolStatus.Completed,
            rawInputJson = """{"language":"javascript","code":"return JSON.stringify({ ok: true })"}""",
        )

        viewModel.rerunToolResult(historyResult)
        advanceUntilIdle()

        assertEquals("local_js", viewModel.state.value.refilledToolName)
        assertEquals(historyResult.rawInputJson, viewModel.state.value.refilledToolInputJson)
        assertEquals("已回填 local_js 参数。", viewModel.state.value.status)
        assertEquals(emptyList<LocalSearchRequest>(), localSearchClient.requests)
        assertEquals(emptyList<ToolResult>(), repository.savedResults.value)
    }

    @Test
    fun toolHistoryRefillAvailabilityRequiresSupportedToolAndRawInput() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(
            true,
            viewModel.state.value.canRefillToolResult(
                toolResult(
                    id = "text",
                    toolName = "text_transform",
                    status = ToolStatus.Completed,
                    rawInputJson = """{"operation":"json_format","text":"{}"}""",
                ),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRefillToolResult(
                toolResult(
                    id = "read-file",
                    toolName = "read_file",
                    status = ToolStatus.Completed,
                    rawInputJson = """{"uri":"content://docs/a.md","maxBytes":65536}""",
                ),
            ),
        )
        assertEquals(
            true,
            viewModel.state.value.canRefillToolResult(
                toolResult(
                    id = "image-upload",
                    toolName = "image_upload_to_model",
                    status = ToolStatus.Completed,
                    rawInputJson = """{"imageUri":"content://image/1","purpose":"describe it"}""",
                ),
            ),
        )
        assertEquals(
            false,
            viewModel.state.value.canRefillToolResult(
                toolResult(id = "missing-input", toolName = "text_transform", status = ToolStatus.Completed),
            ),
        )
        assertEquals(
            false,
            viewModel.state.value.canRefillToolResult(
                toolResult(
                    id = "sandbox",
                    toolName = "code_sandbox",
                    status = ToolStatus.Completed,
                    rawInputJson = """{"language":"python","code":"print(1)","timeoutSeconds":5}""",
                ),
            ),
        )
    }

    @Test
    fun refillToolResultStoresCanonicalToolNameAndRawInputWithoutExecuting() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val viewModel = viewModel(toolInvocationRepository = repository)
        advanceUntilIdle()

        val rawInput = """{"fileName":"snippet","original":"old","modified":"new"}"""
        viewModel.refillToolResult(
            toolResult(
                id = "diff",
                toolName = "code_diff_preview",
                status = ToolStatus.Completed,
                rawInputJson = rawInput,
            ),
        )

        assertEquals("code_diff_preview", viewModel.state.value.refilledToolName)
        assertEquals(rawInput, viewModel.state.value.refilledToolInputJson)
        assertEquals("已回填 code_diff_preview 参数。", viewModel.state.value.status)
        assertEquals(emptyList<ToolResult>(), repository.savedResults.value)
    }

    @Test
    fun chatInstructionForRefilledToolUsesStoredCanonicalNameAndRawInput() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val rawInput = """{"uri":"content://docs/a.md","maxBytes":65536}"""
        viewModel.refillToolResult(
            toolResult(
                id = "file-1",
                toolName = "read_file",
                status = ToolStatus.Completed,
                rawInputJson = rawInput,
            ),
        )

        val instruction = viewModel.state.value.chatInstructionForRefilledTool()

        assertTrue(instruction.orEmpty().contains("工具：file_read"))
        assertTrue(instruction.orEmpty().contains("参数：$rawInput"))
        assertTrue(instruction.orEmpty().contains("系统文件选择器授权的 content:// URI"))
        assertTrue(instruction.orEmpty().contains("不要自动上传图片或文件内容"))
    }

    @Test
    fun chatInstructionForRefilledLocalJsKeepsSandboxBoundary() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val rawInput = """{"code":"return input.value + 1;","inputJson":"{\"value\":1}"}"""
        viewModel.refillToolResult(
            toolResult(
                id = "js-1",
                toolName = "local-js",
                status = ToolStatus.Completed,
                rawInputJson = rawInput,
            ),
        )

        val instruction = viewModel.state.value.chatInstructionForRefilledTool().orEmpty()

        assertTrue(instruction.contains("工具：local_js"))
        assertTrue(instruction.contains("参数：$rawInput"))
        assertTrue(instruction.contains("不要请求网络、文件系统、系统命令或 Android Context"))
        assertTrue(instruction.contains("超时和输出截断设置"))
    }

    @Test
    fun chatInstructionForRefilledImageGenerationRequiresConfirmation() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val rawInput = """{"providerId":"image-provider","prompt":"mobile AI workbench","count":1}"""
        viewModel.refillToolResult(
            toolResult(
                id = "image-1",
                toolName = "image_generation",
                status = ToolStatus.Completed,
                rawInputJson = rawInput,
            ),
        )

        val instruction = viewModel.state.value.chatInstructionForRefilledTool().orEmpty()

        assertTrue(instruction.contains("工具：image_generation"))
        assertTrue(instruction.contains("参数：$rawInput"))
        assertTrue(instruction.contains("需要用户确认的付费/联网调用"))
        assertTrue(instruction.contains("如果参数里包含 providerId，请先核对是否仍要使用该 Provider"))
        assertTrue(instruction.contains("不要自动上传本地图片"))
    }

    @Test
    fun chatInstructionForRefilledTextTransformKeepsLocalOnlyBoundary() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            val rawInput = """{"operation":"json_format","text":"{\"ok\":true}"}"""
            viewModel.refillToolResult(
                toolResult(
                    id = "text-1",
                    toolName = "text_transform",
                    status = ToolStatus.Completed,
                    rawInputJson = rawInput,
                ),
            )

            val instruction = viewModel.state.value.chatInstructionForRefilledTool().orEmpty()

            assertTrue(instruction.contains("工具：text_transform"))
            assertTrue(instruction.contains("参数：$rawInput"))
            assertTrue(instruction.contains("本地文本转换处理"))
            assertTrue(instruction.contains("不调用 Provider，不上传文本"))
        }

    @Test
    fun chatInstructionForRefilledCodeDiffPreviewForbidsWritingFiles() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            val rawInput = """{"fileName":"Main.kt","original":"old","modified":"new"}"""
            viewModel.refillToolResult(
                toolResult(
                    id = "diff-1",
                    toolName = "code-diff-preview",
                    status = ToolStatus.Completed,
                    rawInputJson = rawInput,
                ),
            )

            val instruction = viewModel.state.value.chatInstructionForRefilledTool().orEmpty()

            assertTrue(instruction.contains("工具：code_diff_preview"))
            assertTrue(instruction.contains("参数：$rawInput"))
            assertTrue(instruction.contains("只用 code_diff_preview 生成 Diff 预览"))
            assertTrue(instruction.contains("不写入文件、不修改本机项目"))
        }

    @Test
    fun chatInstructionForProviderConnectionTestDoesNotRequestApiKeyPlaintext() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            val rawInput = """{"providerId":"default"}"""
            viewModel.refillToolResult(
                toolResult(
                    id = "provider-1",
                    toolName = "provider_connection_test",
                    status = ToolStatus.Completed,
                    rawInputJson = rawInput,
                ),
            )

            val instruction = viewModel.state.value.chatInstructionForRefilledTool().orEmpty()

            assertTrue(instruction.contains("工具：provider_connection_test"))
            assertTrue(instruction.contains("参数：$rawInput"))
            assertTrue(instruction.contains("已保存的 Provider 配置"))
            assertTrue(instruction.contains("不要输出或索要 API Key 明文"))
        }

    @Test
    fun chatInstructionForRefilledToolReturnsNullBeforeRefill() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertNull(viewModel.state.value.chatInstructionForRefilledTool())
    }

    @Test
    fun chatInstructionForToolResultUsesCanonicalNameAndRawInput() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val instruction = viewModel.state.value.chatInstructionForToolResult(
            toolResult(
                id = "file-1",
                toolName = "read_file",
                status = ToolStatus.Completed,
                rawInputJson = """{"uri":"content://docs/a.md","maxBytes":65536}""",
            ),
        )

        assertTrue(instruction.contains("工具：file_read"))
        assertTrue(instruction.contains("""参数：{"uri":"content://docs/a.md","maxBytes":65536}"""))
    }

    @Test
    fun deniedToolHistoryChatInstructionIsDiagnosticOnly() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val result = toolResult(
            id = "js-denied-chat",
            toolName = "local_js",
            status = ToolStatus.Denied,
            rawInputJson = """{"language":"javascript","code":"return 1"}""",
        )

        assertEquals(false, viewModel.state.value.canSendToolResultToChat(result))

        val instruction = viewModel.state.value.chatInstructionForToolResult(result)

        assertTrue(instruction.contains("诊断记录参考"))
        assertTrue(instruction.contains("不要据此重新执行工具"))
        assertTrue(instruction.contains("工具：local_js"))
        assertTrue(instruction.contains("状态：Denied"))
        assertTrue(!instruction.contains("参数："))
    }

    @Test
    fun cancelledToolHistoryChatInstructionIsDiagnosticOnly() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val result = toolResult(
            id = "js-cancelled-chat",
            toolName = "local_js",
            status = ToolStatus.Cancelled,
            rawInputJson = """{"language":"javascript","code":"return 1"}""",
        )

        assertEquals(false, viewModel.state.value.canSendToolResultToChat(result))

        val instruction = viewModel.state.value.chatInstructionForToolResult(result)

        assertTrue(instruction.contains("诊断记录参考"))
        assertTrue(instruction.contains("不要据此重新执行工具"))
        assertTrue(instruction.contains("工具：local_js"))
        assertTrue(instruction.contains("状态：Cancelled"))
        assertTrue(!instruction.contains("参数："))
    }

    @Test
    fun chatInstructionForToolResultFallsBackToInputSummary() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val instruction = viewModel.state.value.chatInstructionForToolResult(
            toolResult(
                id = "custom-1",
                toolName = "custom_tool",
                status = ToolStatus.Completed,
            ),
        )

        assertTrue(instruction.contains("工具：custom_tool"))
        assertTrue(instruction.contains("输入摘要：input for custom_tool"))
    }

    @Test
    fun chatInstructionForFailedToolResultIncludesFailureContext() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val instruction = viewModel.state.value.chatInstructionForToolResult(
            toolResult(
                id = "search-failed-1",
                toolName = "local-web-search",
                status = ToolStatus.Failed,
                rawInputJson = """{"query":"AI"}""",
                error = ToolError(
                    code = "local_search_http_429",
                    message = "Rate limit exceeded",
                    statusCode = 429,
                    retryable = true,
                ),
            ),
        )

        assertTrue(instruction.contains("工具：web_search_local"))
        assertTrue(instruction.contains("""参数：{"query":"AI"}"""))
        assertTrue(instruction.contains("上次执行失败：local_search_http_429: Rate limit exceeded"))
        assertTrue(instruction.contains("HTTP 状态：429"))
        assertTrue(instruction.contains("是否可重试：是"))
        assertTrue(instruction.contains("恢复建议：搜索请求被限流，稍后重试，或切换搜索 Provider。"))
    }

    @Test
    fun toolHistoryRecoveryHintUsesProviderBackedGuidance() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val imageInstruction = viewModel.state.value.chatInstructionForToolResult(
            toolResult(
                id = "image-failed-1",
                toolName = "image_generation",
                status = ToolStatus.Failed,
                rawInputJson = """{"prompt":"mobile workbench"}""",
                error = ToolError(
                    code = "provider_http_429",
                    message = "Rate limit exceeded",
                    statusCode = 429,
                    retryable = true,
                ),
            ),
        )
        val providerTest = toolResult(
            id = "provider-failed-1",
            toolName = "provider_connection_test",
            status = ToolStatus.Failed,
            rawInputJson = """{"providerId":"default"}""",
            error = ToolError(
                code = "provider_http_401",
                message = "Unauthorized",
                statusCode = 401,
                retryable = false,
            ),
        )

        assertTrue(imageInstruction.contains("切换图片模型/Provider"))
        assertEquals(
            "检查 Provider API Key、Base URL 和模型配置后重试。",
            providerTest.recoveryHintForHistory(),
        )
        assertEquals(
            "请打开工具中心检查工具是否启用、名称是否正确，或改用当前 App 支持的本地工具。",
            toolResult(
                id = "disabled-tool",
                toolName = "time",
                status = ToolStatus.Failed,
                rawInputJson = "{}",
                error = ToolError(
                    code = "tool_disabled",
                    message = "工具已禁用。",
                    retryable = false,
                ),
            ).recoveryHintForHistory(),
        )
    }

    @Test
    fun sampleInputAndChatInstructionForToolUseCanonicalToolName() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val aliasTool = toolDescriptor(name = "local-web-search")

        assertEquals("""{"query":"AI 行业最新消息"}""", viewModel.state.value.sampleInputForTool(aliasTool))

        val instruction = viewModel.state.value.chatInstructionForTool(aliasTool)
        assertTrue(instruction.contains("工具：web_search_local"))
        assertTrue(instruction.contains("关键结论必须标注对应来源 URL"))
        assertTrue(instruction.contains("没有可引用来源"))
        assertTrue(instruction.contains("""参数：{"query":"AI 行业最新消息"}"""))
    }

    @Test
    fun fileReadChatInstructionKeepsSystemPickerBoundary() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val fileReadTool = toolDescriptor(name = "read_file")
        val sampleInput = viewModel.state.value.sampleInputForTool(fileReadTool)
        val instruction = viewModel.state.value.chatInstructionForTool(fileReadTool)

        assertTrue(sampleInput.contains("系统文件选择器返回的授权URI"))
        assertTrue(instruction.contains("附件按钮"))
        assertTrue(instruction.contains("系统文件选择器返回的授权 URI"))
        assertTrue(instruction.contains("不要手写本地路径"))
        assertTrue(instruction.contains("不要自动上传图片或文件内容"))
        assertTrue(instruction.contains("工具：file_read"))
    }

    @Test
    fun toolChatInstructionForLocalJsKeepsSandboxBoundary() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val instruction = viewModel.state.value.chatInstructionForTool(toolDescriptor(name = "local_js"))

        assertTrue(instruction.contains("工具：local_js"))
        assertTrue(instruction.contains("不要请求网络、文件系统、系统命令或 Android Context"))
        assertTrue(instruction.contains("超时和输出截断设置"))
        assertTrue(instruction.contains(""""timeoutMillis":1000"""))
        assertTrue(instruction.contains(""""outputLimitBytes":8192"""))
    }

    @Test
    fun toolChatInstructionForImageGenerationRequiresConfirmation() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val instruction = viewModel.state.value.chatInstructionForTool(toolDescriptor(name = "image_generation"))

        assertTrue(instruction.contains("工具：image_generation"))
        assertTrue(instruction.contains("需要用户确认的付费/联网调用"))
        assertTrue(instruction.contains("Provider、模型、数量和尺寸"))
        assertTrue(instruction.contains("不要自动上传本地图片"))
    }

    @Test
    fun toolChatInstructionForImageUploadRequiresChatInputConfirmation() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            val instruction = viewModel.state.value.chatInstructionForTool(
                toolDescriptor(name = "image-upload"),
            )

            assertTrue(instruction.contains("工具：image_upload_to_model"))
            assertTrue(instruction.contains("聊天输入栏选择图片"))
            assertTrue(instruction.contains("多模态内容发送给当前模型"))
            assertTrue(instruction.contains("不要手写本地路径"))
            assertTrue(instruction.contains("必须等待用户二次确认"))

            val failedHistory = toolResult(
                id = "image-upload-failed",
                toolName = "image_upload_to_model",
                status = ToolStatus.Failed,
                rawInputJson = """{"imageUri":"content://image/1"}""",
                error = ToolError(
                    code = "image_upload_requires_chat_confirmation",
                    message = "Needs chat confirmation",
                    retryable = false,
                ),
            )
            assertEquals(
                "请通过聊天输入栏选择图片，并在发送前确认图片会作为多模态内容发送给当前模型。",
                failedHistory.recoveryHintForHistory(),
            )
        }

    @Test
    fun toolChatInstructionForProviderConnectionTestUsesSavedConfigOnly() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()

            val instruction = viewModel.state.value.chatInstructionForTool(
                toolDescriptor(name = "provider_connection_test"),
            )

            assertTrue(instruction.contains("工具：provider_connection_test"))
            assertTrue(instruction.contains("已保存的 Provider 配置"))
            assertTrue(instruction.contains("不要输出或索要 API Key 明文"))
        }

    @Test
    fun sampleInputForLocalLowRiskToolsMatchesExpectedSchemas() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        val textTransform = viewModel.state.value.sampleInputForTool(toolDescriptor(name = "text_transform"))
        val diffPreview = viewModel.state.value.sampleInputForTool(toolDescriptor(name = "code_diff_preview"))

        assertTrue(textTransform.contains(""""operation":"json_format""""))
        assertTrue(textTransform.contains(""""text":"{\"name\":\"demo\"}""""))
        assertTrue(diffPreview.contains(""""fileName":"snippet.kt""""))
        assertTrue(diffPreview.contains(""""modified":"fun answer() = \"new\"""""))
    }

    @Test
    fun workbenchCopyPayloadsExposeSearchInputAndSourceOutputJson() {
        val state = ToolsUiState(
            searchQuery = "AI release notes",
            searchFetchedAt = "2026-06-01T00:00:01Z",
            searchResults = listOf(
                SearchResult(
                    title = "Result title",
                    summary = "Result summary",
                    url = "https://example.com/result",
                    source = "example.com",
                    publishedAt = Instant.parse("2026-05-31T00:00:00Z"),
                ),
            ),
        )

        assertEquals("""{"query":"AI release notes"}""", state.searchWorkbenchInputJson())
        val outputJson = state.searchWorkbenchOutputJson().orEmpty()

        assertTrue(outputJson.contains(""""query":"AI release notes""""))
        assertTrue(outputJson.contains(""""url":"https://example.com/result""""))
        assertTrue(outputJson.contains(""""publishedAt":"2026-05-31T00:00:00Z""""))
        val chatDraft = state.searchWorkbenchChatDraft().orEmpty()

        assertTrue(chatDraft.contains("本地搜索结果继续处理"))
        assertTrue(chatDraft.contains("关键结论必须标注对应来源 URL"))
        assertTrue(chatDraft.contains("https://example.com/result"))
    }

    @Test
    fun workbenchCopyPayloadsExposeSandboxInputAndExecutionFlags() {
        val state = ToolsUiState(
            sandboxCode = "print(1 + 1)",
            sandboxResult = SandboxRunResponse(
                language = "python",
                stdout = "2\n",
                stderr = "",
                exitCode = 0,
                durationMs = 12,
                timedOut = true,
                truncated = true,
            ),
        )

        assertEquals(
            """{"language":"python","code":"print(1 + 1)","timeoutSeconds":3}""",
            state.sandboxWorkbenchInputJson(),
        )
        val outputJson = state.sandboxWorkbenchOutputJson().orEmpty()

        assertTrue(outputJson.contains(""""stdout":"2\n""""))
        assertTrue(outputJson.contains(""""timedOut":true"""))
        assertTrue(outputJson.contains(""""truncated":true"""))
        val chatDraft = state.sandboxWorkbenchChatDraft().orEmpty()

        assertTrue(chatDraft.contains("代码沙箱执行结果继续处理"))
        assertTrue(chatDraft.contains("stdout、stderr、退出码、超时和截断状态"))
        assertTrue(chatDraft.contains(""""timedOut":true"""))
    }

    private fun viewModel(
        secretStore: FakeSecretStore = FakeSecretStore(),
        toolInvocationRepository: ToolInvocationRepository = RecordingToolInvocationRepository(),
        localSearchClient: LocalSearchClient = RecordingLocalSearchClient(),
    ): ToolsViewModel =
        ToolsViewModel(
            settingsRepository = GatewaySettingsRepository(context, secretStore),
            searchSettingsRepository = SearchSettingsRepository(context, secretStore),
            toolSettingsRepository = ToolSettingsRepository(context),
            gatewayClient = GatewayClient(),
            localSearchClient = localSearchClient,
            toolInvocationRepository = toolInvocationRepository,
            clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC),
        )
}

private suspend fun ToolsViewModel.awaitNotLoading() {
    withTimeout(5_000) {
        while (state.value.isLoading) {
            yield()
        }
    }
}

private fun manifestResponse(toolName: String): MockResponse =
    MockResponse().setResponseCode(200).setBody(
        """
        {
          "version": 1,
          "generatedAt": "2026-06-01T00:00:00Z",
          "tools": [
            {
              "name": "$toolName",
              "description": "Remote tool",
              "permissionLevel": "Network",
              "inputSchema": {}
            }
          ]
        }
        """.trimIndent(),
    )

private fun searchResponse(): MockResponse =
    MockResponse().setResponseCode(200).setBody(
        """
        {
          "query": "release notes",
          "fetchedAt": "2026-06-01T00:00:01Z",
          "results": [
            {
              "title": "Result title",
              "summary": "Result summary",
              "url": "https://example.com/result",
              "source": "example.com",
              "publishedAt": "2026-05-31T00:00:00Z"
            }
          ]
        }
        """.trimIndent(),
    )

private fun toolDescriptor(name: String): com.aichat.workbench.tool.model.ToolDescriptor =
    com.aichat.workbench.tool.model.ToolDescriptor(
        name = name,
        displayName = name,
        description = "Tool $name",
        permissionLevel = ToolPermissionLevel.ReadOnly,
        inputSchemaJson = "{}",
        outputSchemaJson = "{}",
        timeoutSeconds = null,
        source = com.aichat.workbench.tool.model.ToolSource.BuiltIn,
    )

private fun toolResult(
    id: String,
    toolName: String,
    status: ToolStatus,
    error: ToolError? = null,
    conversationId: String? = null,
    rawInputJson: String? = null,
    startedAt: Instant = Instant.parse("2026-06-01T00:00:00Z"),
    durationMs: Long? = null,
): ToolResult =
    ToolResult(
        id = ToolCallId(id),
        toolName = toolName,
        permissionLevel = ToolPermissionLevel.Network,
        inputSummary = "input for $toolName",
        output = ToolOutput.Json("""{"ok":true}"""),
        status = status,
        startedAt = startedAt,
        finishedAt = Instant.parse("2026-06-01T00:00:01Z"),
        error = error,
        conversationId = conversationId?.let(::ConversationId),
        rawInputJson = rawInputJson,
        durationMs = durationMs,
    )

private class FakeSecretStore : SecretStore {
    private val secrets = mutableMapOf<String, String>()

    override suspend fun putSecret(ref: String, value: String) {
        secrets[ref] = value
    }

    override suspend fun getSecret(ref: String): String? = secrets[ref]

    override suspend fun deleteSecret(ref: String) {
        secrets.remove(ref)
    }
}

private data class LocalSearchRequest(
    val query: String,
    val config: SearchConfig,
)

private class RecordingLocalSearchClient(
    private val response: SearchResponse = SearchResponse(
        query = "AI release notes",
        fetchedAt = Instant.parse("2026-06-01T00:00:01Z"),
        results = listOf(
            SearchResult(
                title = "Result title",
                summary = "Result summary",
                url = "https://example.com/result",
                source = "example.com",
                publishedAt = Instant.parse("2026-05-31T00:00:00Z"),
            ),
        ),
    ),
    private val error: Throwable? = null,
) : LocalSearchClient {
    val requests = mutableListOf<LocalSearchRequest>()

    override suspend fun search(query: String, config: SearchConfig): SearchResponse {
        requests += LocalSearchRequest(query, config)
        error?.let { throw it }
        return response.copy(query = query)
    }
}

private class RecordingToolInvocationRepository : ToolInvocationRepository {
    val savedResults = MutableStateFlow<List<ToolResult>>(emptyList())

    override fun observeToolInvocations(): Flow<List<ToolResult>> = savedResults

    override suspend fun saveToolResult(conversationId: ConversationId?, toolResult: ToolResult) {
        savedResults.value = savedResults.value + toolResult
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ToolsMainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
