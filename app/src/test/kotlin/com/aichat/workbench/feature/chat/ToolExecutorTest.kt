package com.aichat.workbench.feature.chat

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.tool.gateway.GatewayClient
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
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

    @Test
    fun remoteToolsAcceptCaseInsensitiveGatewayScheme() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.start()
        try {
            val uppercaseBaseUrl = server.url("/").toString().replaceFirst("http", "HTTP")
            val executor = ToolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = " $uppercaseBaseUrl ", apiToken = "token")
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val tools = executor.availableTools()

            assertEquals(listOf("time", "web_search"), tools.map { it.name })
            assertEquals("/v1/tools/manifest", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsReuseCachedManifest() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.start()
        try {
            val executor = ToolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = "token")
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val first = executor.availableTools()
            val second = executor.availableTools()

            assertEquals(listOf("time", "web_search"), first.map { it.name })
            assertEquals(first, second)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsInvalidateCacheWhenGatewaySettingsChange() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.enqueue(manifestResponse(toolName = "code_sandbox", permissionLevel = "Execute"))
        server.start()
        try {
            var token = "token-1"
            val executor = ToolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = token)
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val first = executor.availableTools()
            token = "token-2"
            val second = executor.availableTools()

            assertEquals(listOf("time", "web_search"), first.map { it.name })
            assertEquals(listOf("time", "code_sandbox"), second.map { it.name })
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsRefreshExpiredCache() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.enqueue(manifestResponse(toolName = "code_sandbox", permissionLevel = "Execute"))
        server.start()
        try {
            val mutableClock = MutableClock(Instant.parse("2026-06-01T00:00:00Z"))
            val executor = ToolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = "token")
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = mutableClock,
            )

            val first = executor.availableTools()
            mutableClock.advanceBy(Duration.ofMinutes(6))
            val second = executor.availableTools()

            assertEquals(listOf("time", "web_search"), first.map { it.name })
            assertEquals(listOf("time", "code_sandbox"), second.map { it.name })
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsCoalesceConcurrentManifestRequests() = runTest {
        val server = MockWebServer()
        server.enqueue(manifestResponse(toolName = "web_search", permissionLevel = "Network"))
        server.start()
        try {
            val executor = ToolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = "token")
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val results = (1..5)
                .map { async { executor.availableTools().map { it.name } } }
                .awaitAll()

            assertEquals(List(5) { listOf("time", "web_search") }, results)
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun remoteToolsExposeOnlyClientExecutableGatewayTools() = runTest {
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
                    },
                    {
                      "name": "future_gateway_tool",
                      "description": "Not implemented on Android yet",
                      "permissionLevel": "HighRisk",
                      "inputSchema": {}
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        server.start()
        try {
            val executor = ToolExecutor(
                gatewaySettingsProvider = {
                    GatewaySettings(enabled = true, baseUrl = server.url("/").toString(), apiToken = "token")
                },
                gatewayClientProvider = { GatewayClient() },
                toolInvocationRepository = RecordingToolInvocationRepository(),
                clock = clock,
            )

            val tools = executor.availableTools()

            assertEquals(listOf("time", "web_search"), tools.map { it.name })
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun executeWithKnownDescriptorReportsDisabledGatewayWithoutReloadingManifest() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = ToolExecutor(
            gatewaySettingsProvider = { GatewaySettings(enabled = false, baseUrl = "", apiToken = "") },
            gatewayClientProvider = { error("GatewayClient should not be created for known descriptor failure") },
            toolInvocationRepository = repository,
            clock = clock,
        )
        val descriptor = ToolDescriptor(
            name = "web_search",
            displayName = "Web Search",
            description = "Remote search",
            permissionLevel = ToolPermissionLevel.Network,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = null,
            source = ToolSource.Gateway,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_2"), "web_search", """{"query":"AI"}"""),
            descriptor = descriptor,
        )

        assertEquals(ToolPermissionLevel.Network, execution.result.permissionLevel)
        assertEquals("gateway_disabled", execution.result.error?.code)
        assertEquals("工具网关未启用。", execution.result.error?.message)
        assertEquals(1, repository.savedResults.value.size)
    }

    @Test
    fun executeSearchReportsInvalidGatewayUrlBeforeCreatingGatewayClient() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = ToolExecutor(
            gatewaySettingsProvider = {
                GatewaySettings(enabled = true, baseUrl = "gateway.local", apiToken = "token")
            },
            gatewayClientProvider = { error("GatewayClient should not be created for invalid gateway URL") },
            toolInvocationRepository = repository,
            clock = clock,
        )
        val descriptor = ToolDescriptor(
            name = "web_search",
            displayName = "Web Search",
            description = "Remote search",
            permissionLevel = ToolPermissionLevel.Network,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = 20,
            source = ToolSource.Gateway,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(ToolCallId("call_5"), "web_search", """{"query":"AI"}"""),
            descriptor = descriptor,
        )

        assertEquals("invalid_gateway_url", execution.result.error?.code)
        assertEquals("工具网关地址无效。", execution.result.error?.message)
        assertEquals(1, repository.savedResults.value.size)
    }

    @Test
    fun executeSandboxRejectsInvalidTimeoutBeforeCreatingGatewayClient() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = ToolExecutor(
            gatewaySettingsProvider = {
                GatewaySettings(enabled = true, baseUrl = "http://127.0.0.1:8080", apiToken = "token")
            },
            gatewayClientProvider = { error("GatewayClient should not be created for invalid sandbox timeout") },
            toolInvocationRepository = repository,
            clock = clock,
        )
        val descriptor = ToolDescriptor(
            name = "code_sandbox",
            displayName = "Code Sandbox",
            description = "Remote sandbox",
            permissionLevel = ToolPermissionLevel.Execute,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = 10,
            source = ToolSource.Gateway,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                id = ToolCallId("call_3"),
                name = "code_sandbox",
                arguments = """{"language":"python","code":"print(1)","timeoutSeconds":11}""",
            ),
            descriptor = descriptor,
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals("Sandbox timeoutSeconds 必须在 1 到 10 秒之间。", execution.result.error?.message)
        assertEquals(1, repository.savedResults.value.size)
    }

    @Test
    fun executeSearchReportsInvalidToolArgumentsForMalformedJson() = runTest {
        val repository = RecordingToolInvocationRepository()
        val executor = ToolExecutor(
            gatewaySettingsProvider = {
                GatewaySettings(enabled = true, baseUrl = "http://127.0.0.1:8080", apiToken = "token")
            },
            gatewayClientProvider = { error("GatewayClient should not be created for malformed tool arguments") },
            toolInvocationRepository = repository,
            clock = clock,
        )
        val descriptor = ToolDescriptor(
            name = "web_search",
            displayName = "Web Search",
            description = "Remote search",
            permissionLevel = ToolPermissionLevel.Network,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = 20,
            source = ToolSource.Gateway,
        )

        val execution = executor.execute(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                id = ToolCallId("call_4"),
                name = "web_search",
                arguments = "{",
            ),
            descriptor = descriptor,
        )

        assertEquals("invalid_tool_arguments", execution.result.error?.code)
        assertEquals(1, repository.savedResults.value.size)
    }
}

private fun manifestResponse(toolName: String, permissionLevel: String): MockResponse =
    MockResponse().setResponseCode(200).setBody(
        """
        {
          "version": 1,
          "generatedAt": "2026-06-01T00:00:00Z",
          "tools": [
            {
              "name": "$toolName",
              "description": "Remote tool",
              "permissionLevel": "$permissionLevel",
              "inputSchema": {}
            }
          ]
        }
        """.trimIndent(),
    )

private class RecordingToolInvocationRepository : ToolInvocationRepository {
    val savedResults = MutableStateFlow<List<ToolResult>>(emptyList())

    override fun observeToolInvocations(): Flow<List<ToolResult>> = savedResults

    override suspend fun saveToolResult(conversationId: ConversationId?, toolResult: ToolResult) {
        savedResults.value = savedResults.value + toolResult
    }
}

private class MutableClock(
    private var currentInstant: Instant,
) : Clock() {
    override fun instant(): Instant = currentInstant

    override fun getZone(): ZoneOffset = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId): Clock = this

    fun advanceBy(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }
}
