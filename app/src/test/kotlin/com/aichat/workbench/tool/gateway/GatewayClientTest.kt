package com.aichat.workbench.tool.gateway

import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.model.requiresConfirmation
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GatewayClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun toolManifest_fetchesAndParsesContractFixture() = runTest {
        val fixturePath = listOf(
            Path.of("contracts", "gateway", "fixtures", "tool-manifest.json"),
            Path.of("..", "contracts", "gateway", "fixtures", "tool-manifest.json"),
        ).first(Files::exists)
        val fixture = String(Files.readAllBytes(fixturePath))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(fixture),
        )
        val client = GatewayClient()

        val manifest = client.toolManifest(server.url("/").toString())
        val recorded = server.takeRequest()

        assertEquals("/v1/tools/manifest", recorded.path)
        assertEquals(1, manifest.version)
        assertEquals(2, manifest.tools.size)
        val webSearch = manifest.tools[0]
        val sandbox = manifest.tools[1]
        assertEquals("Web Search", webSearch.displayName)
        assertEquals(ToolPermissionLevel.Network, webSearch.permissionLevel)
        assertEquals(ToolRiskLevel.Medium, webSearch.riskLevel)
        assertTrue(webSearch.requiresNetwork)
        assertFalse(webSearch.requiresFileAccess)
        assertEquals(ToolPermissionPolicy.AskEveryTime, webSearch.defaultPermissionPolicy)
        assertEquals(ToolSource.Gateway, webSearch.source)
        assertEquals(ToolPermissionLevel.Execute, sandbox.permissionLevel)
        assertEquals(ToolRiskLevel.High, sandbox.riskLevel)
        assertTrue(sandbox.requiresNetwork)
        assertFalse(sandbox.requiresFileAccess)
        assertEquals(ToolPermissionPolicy.AskEveryTime, sandbox.defaultPermissionPolicy)
    }

    @Test
    fun toolManifest_mapsUnknownPermissionLevelToHighRisk() {
        val client = GatewayClient()

        val manifest = client.parseToolManifest(
            """
            {
              "version": 1,
              "generatedAt": "2026-06-01T00:00:00Z",
              "tools": [
                {
                  "name": "future_tool",
                  "description": "Future Gateway tool",
                  "permissionLevel": "FuturePermission",
                  "inputSchema": {}
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(ToolPermissionLevel.HighRisk, manifest.tools.single().permissionLevel)
    }

    @Test
    fun toolManifest_parsesLegacyManifestWithoutMetadataDefaults() {
        val client = GatewayClient()

        val manifest = client.parseToolManifest(
            """
            {
              "version": 1,
              "generatedAt": "2026-06-01T00:00:00Z",
              "tools": [
                {
                  "name": "code_sandbox",
                  "description": "Legacy sandbox",
                  "permissionLevel": "Execute",
                  "inputSchema": {}
                },
                {
                  "name": "legacy_read",
                  "description": "Legacy read-only tool",
                  "permissionLevel": "ReadOnly",
                  "inputSchema": {}
                }
              ]
            }
            """.trimIndent(),
        )

        val sandbox = manifest.tools.first { it.name == "code_sandbox" }
        val readOnly = manifest.tools.first { it.name == "legacy_read" }
        assertEquals(ToolRiskLevel.High, sandbox.riskLevel)
        assertTrue(sandbox.requiresNetwork)
        assertFalse(sandbox.requiresFileAccess)
        assertEquals(ToolPermissionPolicy.AskEveryTime, sandbox.defaultPermissionPolicy)
        assertEquals(ToolRiskLevel.Low, readOnly.riskLevel)
        assertFalse(readOnly.requiresNetwork)
        assertFalse(readOnly.requiresFileAccess)
        assertEquals(ToolPermissionPolicy.AllowWithoutPrompt, readOnly.defaultPermissionPolicy)
    }

    @Test
    fun health_trimsBaseUrlBeforeRequest() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "status": "ok",
                      "service": "ai-chat-gateway",
                      "version": "test",
                      "time": "2026-06-01T00:00:00Z"
                    }
                    """.trimIndent(),
                ),
        )
        val client = GatewayClient()

        val health = client.health(" ${server.url("/")} ")
        val recorded = server.takeRequest()

        assertEquals("/health", recorded.path)
        assertEquals("ok", health.status)
        assertEquals("test", health.version)
    }

    @Test
    fun search_postsQueryAndParsesResults() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "query": "AI news",
                      "fetchedAt": "2026-05-31T00:00:00Z",
                      "results": [
                        {
                          "title": "AI funding rises",
                          "summary": "A concise summary.",
                          "url": "https://example.com/ai-funding",
                          "source": "Example News",
                          "publishedAt": "2026-05-30T12:00:00Z"
                        },
                        {
                          "title": "AI policy update",
                          "summary": "",
                          "url": "https://example.com/ai-policy",
                          "source": "Example Wire",
                          "publishedAt": null
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val client = GatewayClient()

        val response = client.search(" ${server.url("/")} ", "AI news", apiToken = "token-1")
        val recorded = server.takeRequest()

        assertEquals("/v1/search", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer token-1", recorded.getHeader("Authorization"))
        assertNotNull(recorded.getHeader("X-Request-Id"))
        assertTrue(recorded.body.readUtf8().contains("AI news"))
        assertEquals("AI news", response.query)
        assertEquals("2026-05-31T00:00:00Z", response.fetchedAt.toString())
        assertEquals(2, response.results.size)
        assertEquals("AI funding rises", response.results[0].title)
        assertEquals("Example News", response.results[0].source)
        assertEquals("2026-05-30T12:00:00Z", response.results[0].publishedAt.toString())
        assertEquals(null, response.results[1].publishedAt)
    }

    @Test
    fun search_mapsStructuredGatewayError() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(
                    """
                    {
                      "code": "invalid_query",
                      "message": "Search query 不能为空。",
                      "requestId": "request-1",
                      "details": null
                    }
                    """.trimIndent(),
                ),
        )
        val client = GatewayClient()

        try {
            client.search(server.url("/").toString(), " ")
            fail("Expected GatewayHttpException")
        } catch (error: GatewayHttpException) {
            assertEquals(400, error.statusCode)
            assertEquals("invalid_query", error.gatewayCode)
            assertEquals("request-1", error.requestId)
            assertEquals("Search query 不能为空。", error.message)
        }
    }

    @Test
    fun search_mapsBlankHttpErrorToReadableFallback() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setBody(" \n\t "),
        )
        val client = GatewayClient()

        try {
            client.search(server.url("/").toString(), "AI news")
            fail("Expected GatewayHttpException")
        } catch (error: GatewayHttpException) {
            assertEquals(502, error.statusCode)
            assertEquals("http_502", error.gatewayCode)
            assertEquals(null, error.requestId)
            assertEquals("Gateway HTTP 502 请求失败。", error.message)
        }
    }

    @Test
    fun search_truncatesNonJsonHttpErrorBody() = runTest {
        val longBody = "<html>\n<body>" + "Gateway unavailable ".repeat(40) + "</body>\n</html>"
        server.enqueue(
            MockResponse()
                .setResponseCode(502)
                .setBody(longBody),
        )
        val client = GatewayClient()

        try {
            client.search(server.url("/").toString(), "AI news")
            fail("Expected GatewayHttpException")
        } catch (error: GatewayHttpException) {
            val messagePrefix = "Gateway HTTP 502："
            val message = error.message.orEmpty()
            assertEquals(502, error.statusCode)
            assertEquals("http_502", error.gatewayCode)
            assertEquals(null, error.requestId)
            assertTrue(message.startsWith(messagePrefix))
            assertTrue(message.contains("Gateway unavailable"))
            assertTrue(message.endsWith("..."))
            assertTrue(message.length <= messagePrefix.length + 240 + "...".length)
            assertFalse(message.contains("\n"))
            assertFalse(message.contains("</html>"))
        }
    }

    @Test
    fun sandboxRun_postsCodeAndParsesResult() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "language": "python",
                      "stdout": "2\n",
                      "stderr": "",
                      "exitCode": 0,
                      "durationMs": 12,
                      "timedOut": false,
                      "truncated": false
                    }
                    """.trimIndent(),
                ),
        )
        val client = GatewayClient()

        val response = client.runSandbox(
            baseUrl = server.url("/").toString(),
            language = "python",
            code = "print(1 + 1)",
            timeoutSeconds = 3,
            apiToken = "token-2",
        )
        val recorded = server.takeRequest()

        assertEquals("/v1/sandbox/run", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer token-2", recorded.getHeader("Authorization"))
        assertNotNull(recorded.getHeader("X-Request-Id"))
        assertTrue(recorded.body.readUtf8().contains("print(1 + 1)"))
        assertEquals("python", response.language)
        assertEquals("2\n", response.stdout)
        assertEquals("", response.stderr)
        assertEquals(0, response.exitCode)
        assertEquals(12L, response.durationMs)
        assertFalse(response.timedOut)
        assertFalse(response.truncated)
    }

    @Test
    fun sandboxRun_rejectsInvalidTimeoutWithoutRequest() = runTest {
        val client = GatewayClient()

        try {
            client.runSandbox(
                baseUrl = server.url("/").toString(),
                language = "python",
                code = "print(1)",
                timeoutSeconds = 11,
            )
            fail("Expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertEquals("Sandbox timeoutSeconds 必须在 1 到 10 秒之间。", error.message)
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun sandboxRun_mapsStructuredGatewayError() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody(
                    """
                    {
                      "code": "sandbox_unavailable",
                      "message": "Sandbox runner 不可用。",
                      "requestId": "request-2",
                      "details": null
                    }
                    """.trimIndent(),
                ),
        )
        val client = GatewayClient()

        try {
            client.runSandbox(
                baseUrl = server.url("/").toString(),
                language = "python",
                code = "print(1)",
                timeoutSeconds = 3,
            )
            fail("Expected GatewayHttpException")
        } catch (error: GatewayHttpException) {
            assertEquals(503, error.statusCode)
            assertEquals("sandbox_unavailable", error.gatewayCode)
            assertEquals("request-2", error.requestId)
            assertEquals("Sandbox runner 不可用。", error.message)
        }
    }

    @Test
    fun toolPermissionLevels_requireConfirmationExceptReadOnly() {
        assertFalse(ToolPermissionLevel.ReadOnly.requiresConfirmation())
        assertTrue(ToolPermissionLevel.Network.requiresConfirmation())
        assertTrue(ToolPermissionLevel.Execute.requiresConfirmation())
        assertTrue(ToolPermissionLevel.HighRisk.requiresConfirmation())
    }
}
