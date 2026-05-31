package com.aichat.workbench.provider.openai

import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.compatible.OpenAiCompatibleChatProvider
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

    private fun openAiRequest(type: ProviderType = ProviderType.OpenAI): ChatProviderRequest =
        ChatProviderRequest(
            provider = ProviderConfig(
                id = ProviderId("provider-1"),
                name = "OpenAI",
                type = type,
                baseUrl = server.url("/v1").toString().trimEnd('/'),
                apiKeyRef = null,
                headers = emptyMap(),
                models = emptyList(),
                defaultModel = "gpt-test",
                enabled = true,
            ),
            apiKey = "test-key",
            model = "gpt-test",
            systemPrompt = "Be concise.",
            messages = listOf(ProviderChatMessage(MessageRole.User, "Hello")),
            parameters = ModelParameters(temperature = 0.2, topP = 0.9, maxTokens = 64),
        )
}
