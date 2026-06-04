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
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.runtimeSettingFor
import com.aichat.workbench.tool.search.LocalSearchClient
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
        viewModel.updateLocalSearchApiKey("test-search-key")
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
        viewModel.updateLocalSearchApiKey("test-search-key")
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
        viewModel.updateLocalSearchApiKey("test-search-key")
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
    fun observesAndFiltersToolHistory() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = RecordingToolInvocationRepository()
        val viewModel = viewModel(toolInvocationRepository = repository)
        advanceUntilIdle()

        val completedSearch = toolResult(
            id = "search-1",
            toolName = "web_search_local",
            status = ToolStatus.Completed,
        )
        val failedSearch = toolResult(
            id = "search-2",
            toolName = "web_search_local",
            status = ToolStatus.Failed,
            error = ToolError(code = "local_search_http_429", message = "Rate limit exceeded"),
        )
        val completedTime = toolResult(
            id = "time-1",
            toolName = "time",
            status = ToolStatus.Completed,
        )
        repository.savedResults.value = listOf(completedSearch, failedSearch, completedTime)
        advanceUntilIdle()

        assertEquals(listOf(completedSearch, failedSearch, completedTime), viewModel.state.value.toolHistory)
        assertEquals(listOf(completedSearch, failedSearch, completedTime), viewModel.state.value.filteredToolHistory)

        viewModel.updateToolHistoryToolFilter("web_search_local")

        assertEquals(listOf(completedSearch, failedSearch), viewModel.state.value.filteredToolHistory)

        viewModel.updateToolHistoryStatusFilter(ToolStatus.Failed)

        assertEquals(listOf(failedSearch), viewModel.state.value.filteredToolHistory)

        viewModel.updateToolHistoryToolFilter(null)

        assertEquals(listOf(failedSearch), viewModel.state.value.filteredToolHistory)
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

private fun toolResult(
    id: String,
    toolName: String,
    status: ToolStatus,
    error: ToolError? = null,
): ToolResult =
    ToolResult(
        id = ToolCallId(id),
        toolName = toolName,
        permissionLevel = ToolPermissionLevel.Network,
        inputSummary = "input for $toolName",
        output = ToolOutput.Json("""{"ok":true}"""),
        status = status,
        startedAt = Instant.parse("2026-06-01T00:00:00Z"),
        finishedAt = Instant.parse("2026-06-01T00:00:01Z"),
        error = error,
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
) : LocalSearchClient {
    val requests = mutableListOf<LocalSearchRequest>()

    override suspend fun search(query: String, config: SearchConfig): SearchResponse {
        requests += LocalSearchRequest(query, config)
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
