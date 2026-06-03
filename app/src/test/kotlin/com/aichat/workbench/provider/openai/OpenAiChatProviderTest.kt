package com.aichat.workbench.provider.openai

import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.compatible.OpenAiCompatibleChatProvider
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiChatProviderTest {
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
    fun complete_usesResponsesApiAndDisablesStore() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"output_text":"Hello"}"""),
        )
        val provider = OpenAiChatProvider()

        val response = provider.complete(openAiRequest())
        val recorded = server.takeRequest()

        assertEquals("Hello", response.content)
        assertEquals("/v1/responses", recorded.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains(""""store":false"""))
    }

    @Test
    fun stream_parsesResponsesTextDeltas() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    event: response.output_text.delta
                    data: {"type":"response.output_text.delta","delta":"Hel"}

                    event: response.output_text.delta
                    data: {"type":"response.output_text.delta","delta":"lo"}

                    event: response.completed
                    data: {"type":"response.completed"}

                    """.trimIndent(),
                ),
        )
        val provider = OpenAiChatProvider()

        val events = provider.stream(openAiRequest()).toList()

        assertEquals(
            listOf(
                ProviderStreamEvent.TextDelta("Hel"),
                ProviderStreamEvent.TextDelta("lo"),
                ProviderStreamEvent.Completed,
            ),
            events,
        )
    }

    @Test
    fun compatibleProvider_usesChatCompletionsStream() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"Hi"},"finish_reason":null}]}

                    data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                    """.trimIndent(),
                ),
        )
        val provider = OpenAiCompatibleChatProvider()

        val events = provider.stream(openAiRequest(type = ProviderType.OpenAICompatible)).toList()
        val recorded = server.takeRequest()

        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals(listOf(ProviderStreamEvent.TextDelta("Hi"), ProviderStreamEvent.Completed), events)
    }

    @Test
    fun compatibleProvider_marksStreamCompletedWhenConnectionClosesWithoutDoneFrame() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"Hi"},"finish_reason":null}]}

                    """.trimIndent(),
                ),
        )
        val provider = OpenAiCompatibleChatProvider()

        val events = provider.stream(openAiRequest(type = ProviderType.OpenAICompatible)).toList()

        assertEquals(listOf(ProviderStreamEvent.TextDelta("Hi"), ProviderStreamEvent.Completed), events)
    }

    @Test
    fun compatibleProvider_appendsV1ForRootBaseUrl() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"Hello"}}]}"""),
        )
        val provider = OpenAiCompatibleChatProvider()

        val response = provider.complete(
            openAiRequest(
                type = ProviderType.Custom,
                baseUrl = server.url("/").toString().trimEnd('/'),
            ),
        )
        val recorded = server.takeRequest()

        assertEquals("Hello", response.content)
        assertEquals("/v1/chat/completions", recorded.path)
    }

    @Test
    fun compatibleProvider_sendsHostedWebSearchOptionsForOfficialSearchTool() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"News"},"finish_reason":null}]}

                    data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                    """.trimIndent(),
                ),
        )
        val provider = OpenAiCompatibleChatProvider()

        val events = provider.stream(
            openAiRequest(
                type = ProviderType.NewApi,
                tools = listOf(hostedTool("web_search", ToolPermissionLevel.ReadOnly)),
            ),
        ).toList()
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()

        assertEquals("/v1/chat/completions", recorded.path)
        assertTrue(body.contains(""""web_search_options""""))
        assertTrue(body.contains(""""search_context_size":"medium""""))
        assertTrue(!body.contains(""""web_search_preview""""))
        assertEquals(listOf(ProviderStreamEvent.TextDelta("News"), ProviderStreamEvent.Completed), events)
    }

    @Test
    fun stream_withOfficialHostedToolsUsesResponsesApi() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    event: response.output_text.delta
                    data: {"type":"response.output_text.delta","delta":"Found news"}

                    event: response.completed
                    data: {"type":"response.completed"}

                    """.trimIndent(),
                ),
        )
        val provider = OpenAiChatProvider()
        val tools = listOf(
            hostedTool("web_search", ToolPermissionLevel.ReadOnly),
            hostedTool("code_interpreter", ToolPermissionLevel.ReadOnly),
            hostedTool("image_generation", ToolPermissionLevel.ReadOnly),
        )

        val events = provider.stream(openAiRequest(tools = tools)).toList()
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()

        assertEquals("/v1/responses", recorded.path)
        assertTrue(body.contains(""""type":"web_search_preview""""))
        assertTrue(body.contains(""""type":"code_interpreter""""))
        assertTrue(body.contains(""""container""""))
        assertTrue(body.contains(""""type":"auto""""))
        assertTrue(body.contains(""""type":"image_generation""""))
        assertEquals(listOf(ProviderStreamEvent.TextDelta("Found news"), ProviderStreamEvent.Completed), events)
    }

    @Test
    fun stream_mapsResponsesImageGenerationEventsToImageDeltas() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    event: response.image_generation_call.partial_image
                    data: {"type":"response.image_generation_call.partial_image","partial_image_index":0,"partial_image_b64":"partial-image"}

                    event: response.output_item.done
                    data: {"type":"response.output_item.done","item":{"type":"image_generation_call","result":"final-image"}}

                    event: response.completed
                    data: {"type":"response.completed"}

                    """.trimIndent(),
                ),
        )
        val provider = OpenAiChatProvider()

        val events = provider.stream(
            openAiRequest(
                tools = listOf(hostedTool("image_generation", ToolPermissionLevel.ReadOnly)),
            ),
        ).toList()

        assertEquals(
            listOf(
                ProviderStreamEvent.ImageDelta(MessagePart.Image("data:image/png;base64,final-image", "image/png")),
                ProviderStreamEvent.Completed,
            ),
            events,
        )
    }

    @Test
    fun stream_withGatewayFunctionToolsUsesChatCompletionsAndAggregatesToolDeltas() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"AI"}}]},"finish_reason":null}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":" news\"}"}}]},"finish_reason":null}]}

                    data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                    """.trimIndent(),
                ),
        )
        val provider = OpenAiChatProvider()
        val tool = gatewayTool(
            name = "web_search",
            permissionLevel = ToolPermissionLevel.Network,
            inputSchemaJson = """{"type":"object","properties":{"query":{"type":"string"}}}""",
        )

        val events = provider.stream(openAiRequest(tools = listOf(tool))).toList()
        val recorded = server.takeRequest()

        assertEquals("/v1/chat/completions", recorded.path)
        assertTrue(recorded.body.readUtf8().contains(""""tools""""))
        assertEquals(
            listOf(
                ProviderStreamEvent.ToolCallDelta(
                    ToolCall(
                        id = ToolCallId("call_1"),
                        name = "web_search",
                        arguments = """{"query":"AI news"}""",
                    ),
                ),
                ProviderStreamEvent.Completed,
            ),
            events,
        )
    }

    @Test
    fun stream_withImageInputUsesChatCompletionsContentArray() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"Seen"},"finish_reason":null}]}

                    data: {"choices":[{"delta":{},"finish_reason":"stop"}]}

                    """.trimIndent(),
                ),
        )
        val provider = OpenAiChatProvider()
        val image = MessagePart.Image("data:image/jpeg;base64,abc", "image/jpeg")

        val events = provider.stream(
            openAiRequest(
                messages = listOf(
                    ProviderChatMessage(
                        role = MessageRole.User,
                        content = "Describe",
                        contentParts = listOf(MessagePart.Text("Describe"), image),
                    ),
                ),
            ),
        ).toList()
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()

        assertEquals("/v1/chat/completions", recorded.path)
        assertTrue(body.contains(""""type":"image_url""""))
        assertTrue(body.contains(""""url":"data:image/jpeg;base64,abc""""))
        assertEquals(listOf(ProviderStreamEvent.TextDelta("Seen"), ProviderStreamEvent.Completed), events)
    }

    @Test
    fun complete_mapsProviderErrors() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"bad key"}}"""),
        )
        val provider = OpenAiChatProvider()

        val error = runCatching {
            provider.complete(openAiRequest())
        }.exceptionOrNull()

        require(error is ProviderHttpException)
        assertEquals("authentication_failed", error.error.code)
        assertEquals("bad key", error.error.message)
        assertEquals(401, error.error.statusCode)
    }

    private fun hostedTool(name: String, permissionLevel: ToolPermissionLevel): ToolDescriptor =
        ToolDescriptor(
            name = name,
            displayName = name,
            description = name,
            permissionLevel = permissionLevel,
            inputSchemaJson = "{}",
            outputSchemaJson = null,
            timeoutSeconds = null,
            source = ToolSource.Official,
        )

    private fun gatewayTool(
        name: String,
        permissionLevel: ToolPermissionLevel,
        inputSchemaJson: String = "{}",
    ): ToolDescriptor =
        ToolDescriptor(
            name = name,
            displayName = name,
            description = name,
            permissionLevel = permissionLevel,
            inputSchemaJson = inputSchemaJson,
            outputSchemaJson = null,
            timeoutSeconds = null,
            source = ToolSource.Gateway,
        )

    private fun openAiRequest(
        type: ProviderType = ProviderType.OpenAI,
        baseUrl: String = server.url("/v1").toString().trimEnd('/'),
        tools: List<ToolDescriptor> = emptyList(),
        messages: List<ProviderChatMessage> = listOf(ProviderChatMessage(MessageRole.User, "Hello")),
    ): ChatProviderRequest =
        ChatProviderRequest(
            provider = ProviderConfig(
                id = ProviderId("provider-1"),
                name = "OpenAI",
                type = type,
                baseUrl = baseUrl,
                apiKeyRef = null,
                headers = emptyMap(),
                models = emptyList(),
                defaultModel = "gpt-test",
                enabled = true,
            ),
            apiKey = "test-key",
            model = "gpt-test",
            systemPrompt = "Be concise.",
            messages = messages,
            parameters = ModelParameters(temperature = 0.2, topP = 0.9, maxTokens = 64),
            tools = tools,
        )
}
