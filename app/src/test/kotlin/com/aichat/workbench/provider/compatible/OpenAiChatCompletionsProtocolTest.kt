package com.aichat.workbench.provider.compatible

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderStreamEvent
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChatCompletionsProtocolTest {

    @Test
    fun buildRequest_serializesChatCompletionsBodyAndImagePartsWithoutTools() {
        val request = OpenAiChatCompletionsProtocol.buildRequest(request(), stream = true)
        val body = request.body!!.readUtf8()

        assertEquals("/v1/chat/completions", request.url.encodedPath)
        assertEquals("text/event-stream", request.header("Accept"))
        assertEquals("Bearer test-key", request.header("Authorization"))
        assertTrue(body.contains(""""store":false"""))
        assertTrue(body.contains(""""type":"image_url""""))
        assertTrue(body.contains(""""url":"data:image/png;base64,img""""))
        assertFalse(body.contains(""""tools""""))
        assertFalse(body.contains(""""tool_choice""""))
        assertFalse(body.contains(""""web_search_options""""))
        assertFalse(body.contains(""""parallel_tool_calls""""))
    }

    @Test
    fun mapSse_ignoresFunctionCallFramesAndCompletesOnFinishReason() {
        val first = OpenAiChatCompletionsProtocol.mapSse(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"legacy_call","arguments":"{\"query\":\"AI"}}]},"finish_reason":null}]}""",
        )
        val second = OpenAiChatCompletionsProtocol.mapSse(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":" check\"}"}}]},"finish_reason":null}]}""",
        )
        val completed = OpenAiChatCompletionsProtocol.mapSse(
            """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
        )

        assertEquals(emptyList<ProviderStreamEvent>(), first)
        assertEquals(emptyList<ProviderStreamEvent>(), second)
        assertEquals(listOf(ProviderStreamEvent.Completed), completed)
    }

    private fun request(): ChatProviderRequest =
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
        )

    private fun okhttp3.RequestBody.readUtf8(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }
}
