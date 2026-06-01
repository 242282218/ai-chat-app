package com.aichat.workbench.feature.chat

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.tool.gateway.GatewayClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ToolExecutorTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun localToolsDoNotCreateGatewayClientWhenGatewayDisabled() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = ToolExecutor(
            gatewaySettingsProvider = { GatewaySettings(enabled = false, baseUrl = "", apiToken = "") },
            gatewayClientProvider = { error("GatewayClient should be lazy") },
            toolInvocationRepository = repository,
            clock = clock,
        )

        val tools = executor.availableTools()
        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_1"), "time", "{}"),
        )

        assertEquals(listOf("time"), tools.map { it.name })
        assertEquals("""{"currentTime":"2026-06-01T00:00:00Z"}""", execution.messageContent)
        assertEquals(1, repository.savedResults.value.size)
    }

    @Test
    fun remoteToolsCreateGatewayClientOnlyWhenGatewayEnabled() = runTest {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "version": 1,
                  "generatedAt": "2026-06-01T00:00:00Z",
                  "tools": [
                    {
                      "name": "web_search",
                      "description": "Search",
                      "permissionLevel": "Network",
                      "inputSchema": {}
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        server.start()
        try {
            var created = false
            val executor = ToolExecutor(
                gatewaySettingsProvider = { GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = "token") },
                gatewayClientProvider = {
                    created = true
                    GatewayClient()
                },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val tools = executor.availableTools()

            assertEquals(true, created)
            assertEquals(listOf("time", "web_search"), tools.map { it.name })
            assertEquals("/v1/tools/manifest", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }
}

private class RecordingToolInvocationRepository : ToolInvocationRepository {
    val savedResults = MutableStateFlow<List<ToolResult>>(emptyList())

    override fun observeToolInvocations(): Flow<List<ToolResult>> = savedResults

    override suspend fun saveToolResult(conversationId: ConversationId?, toolResult: ToolResult) {
        savedResults.value = savedResults.value + toolResult
    }
}
