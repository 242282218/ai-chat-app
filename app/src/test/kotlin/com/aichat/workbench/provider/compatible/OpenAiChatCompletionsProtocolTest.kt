package com.aichat.workbench.provider.compatible

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChatCompletionsProtocolTest {

    @Test
    fun buildRequest_serializesChatCompletionsBodyAndImageParts() {
        val request = OpenAiChatCompletionsProtocol.buildRequest(
            request(
                tools = listOf(
                    tool("web_search", ToolSource.Official),
                    tool("code_sandbox", ToolSource.Gateway),
                ),
            ),
            stream = true,
        )
        val body = request.body!!.readUtf8()

        assertEquals("/v1/chat/completions", request.url.encodedPath)
        assertEquals("text/event-stream", request.header("Accept"))
        assertEquals("Bearer test-key", request.header("Authorization"))
        assertTrue(body.contains(""""store":false"""))
        assertTrue(body.contains(""""type":"image_url""""))
        assertTrue(body.contains(""""url":"data:image/png;base64,img""""))
        assertTrue(body.contains(""""name":"code_sandbox""""))
        assertTrue(body.contains(""""web_search_options""""))
        assertTrue(body.contains(""""parallel_tool_calls":false"""))
    }

    @Test
    fun mapSse_accumulatesToolCallDeltas() {
        val accumulator = ChatToolCallAccumulator()

        val first = OpenAiChatCompletionsProtocol.mapSse(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"web_search","arguments":"{\"query\":\"AI"}}]},"finish_reason":null}]}""",
            accumulator,
        )
        val second = OpenAiChatCompletionsProtocol.mapSse(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":" news\"}"}}]},"finish_reason":null}]}""",
            accumulator,
        )
        val completed = OpenAiChatCompletionsProtocol.mapSse(
            """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            accumulator,
        )

        assertEquals(emptyList<ProviderStreamEvent>(), first)
        assertEquals(emptyList<ProviderStreamEvent>(), second)
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
            completed,
        )
    }

    private fun request(tools: List<ToolDescriptor> = emptyList()): ChatProviderRequest =
        ChatProviderRequest(
            provider = ProviderConfig(
                id = ProviderId("provider-1"),
                name = "New API",
                type = ProviderType.NewApi,
                baseUrl = "https://zzshu.cc",
                apiKeyRef = null,
                headers = emptyMap(),
                models = emptyList(),
                defaultModel = null,
                enabled = true,
            ),
            apiKey = "test-key",
            model = "gpt-test",
            systemPrompt = "Be concise.",
            messages = listOf(
                ProviderChatMessage(
                    role = MessageRole.User,
                    content = "Describe",
                    contentParts = listOf(
                        MessagePart.Text("Describe"),
                        MessagePart.Image("data:image/png;base64,img", "image/png"),
                    ),
                ),
            ),
            parameters = ModelParameters(temperature = 0.2),
            tools = tools,
        )

    private fun tool(name: String, source: ToolSource): ToolDescriptor =
        ToolDescriptor(
            name = name,
            displayName = name,
            description = name,
            permissionLevel = ToolPermissionLevel.ReadOnly,
            inputSchemaJson = """{"type":"object"}""",
            outputSchemaJson = null,
            timeoutSeconds = null,
            source = source,
        )

    private fun okhttp3.RequestBody.readUtf8(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }
}
