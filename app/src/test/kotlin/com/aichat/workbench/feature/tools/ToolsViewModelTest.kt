package com.aichat.workbench.feature.tools

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.settings.GatewaySettingsRepository
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.tool.gateway.GatewayClient
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
    private lateinit var preferences: android.content.SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
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

    private fun viewModel(): ToolsViewModel =
        ToolsViewModel(
            settingsRepository = GatewaySettingsRepository(context, FakeSecretStore()),
            gatewayClient = GatewayClient(),
            toolInvocationRepository = RecordingToolInvocationRepository(),
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

private class FakeSecretStore : SecretStore {
    override suspend fun putSecret(ref: String, value: String) = Unit

    override suspend fun getSecret(ref: String): String? = null

    override suspend fun deleteSecret(ref: String) = Unit
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
