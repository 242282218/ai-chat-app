package com.aichat.workbench.provider.openai

import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
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

class OpenAiResponsesProtocolTest {

    @Test
    fun buildRequest_serializesResponsesBodyAndHeaders() {
        val request = OpenAiResponsesProtocol.buildRequest(
            request(
                tools = listOf(
                    tool("web_search", ToolSource.Official),
                    tool("local_js", ToolSource.BuiltIn),
                ),
            ),
            stream = false,
        )
        val body = request.body!!.readUtf8()

        assertEquals("/v1/responses", request.url.encodedPath)
        assertEquals("Bearer test-key", request.header("Authorization"))
        assertEquals("application/json", request.header("Accept"))
        assertTrue(body.contains(""""store":false"""))
        assertTrue(body.contains(""""instructions":"Be concise.""""))
        assertTrue(body.contains(""""type":"web_search_preview""""))
        assertTrue(body.contains(""""name":"local_js""""))
        assertTrue(!body.contains("system-only"))
    }

    @Test
    fun mapSse_mapsTextImageAndFailedEvents() {
        assertEquals(
            ProviderStreamEvent.TextDelta("Hi"),
            OpenAiResponsesProtocol.mapSse("""{"type":"response.output_text.delta","delta":"Hi"}"""),
        )
        assertEquals(
            ProviderStreamEvent.ImageDelta(
                com.aichat.workbench.domain.model.MessagePart.Image("data:image/png;base64,img", "image/png"),
            ),
            OpenAiResponsesProtocol.mapSse(
                """{"type":"response.output_item.done","item":{"type":"image_generation_call","result":"img"}}""",
            ),
        )
        assertEquals(
            ProviderStreamEvent.Failed(
                com.aichat.workbench.provider.api.ProviderError(
                    code = "provider_error",
                    message = "bad request",
                    statusCode = null,
                    retryable = false,
                ),
            ),
            OpenAiResponsesProtocol.mapSse("""{"type":"response.failed","message":"bad request"}"""),
        )
    }

    private fun request(tools: List<ToolDescriptor> = emptyList()): ChatProviderRequest =
        ChatProviderRequest(
            provider = ProviderConfig(
                id = ProviderId("provider-1"),
                name = "OpenAI",
                type = ProviderType.OpenAI,
                baseUrl = "https://zzshu.cc",
                apiKeyRef = null,
                headers = mapOf("X-Test" to "1"),
                models = emptyList(),
                defaultModel = null,
                enabled = true,
            ),
            apiKey = "test-key",
            model = "gpt-test",
            systemPrompt = "Be concise.",
            messages = listOf(
                ProviderChatMessage(MessageRole.System, "system-only"),
                ProviderChatMessage(MessageRole.User, "Hello"),
            ),
            parameters = ModelParameters(maxTokens = 64),
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
