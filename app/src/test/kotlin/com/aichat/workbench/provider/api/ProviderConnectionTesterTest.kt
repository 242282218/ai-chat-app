package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.ProviderRegistry
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderConnectionTesterTest {
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
    fun test_returnsOkForModelsEndpoint() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[]}"""),
        )
        val tester = ProviderConnectionTester(providerRegistry = registeredRegistry())

        val result = tester.test(provider(), "test-key")
        val recorded = server.takeRequest()

        assertTrue(result.ok)
        assertEquals(200, result.statusCode)
        assertEquals("/v1/models", recorded.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
    }

    @Test
    fun test_returnsFailureForHttpErrors() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key"}}"""),
        )
        val tester = ProviderConnectionTester(providerRegistry = registeredRegistry())

        val result = tester.test(provider(), "bad-key")

        assertFalse(result.ok)
        assertEquals(401, result.statusCode)
        assertEquals("Provider HTTP 401：Invalid API key", result.message)
    }

    @Test
    fun test_returnsReadableMessageForConnectionFailures() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor { throw IOException("socket closed") }
            .build()
        val tester = ProviderConnectionTester(
            client = client,
            providerRegistry = registeredRegistry(),
        )

        val result = tester.test(provider(), "test-key")

        assertFalse(result.ok)
        assertEquals(null, result.statusCode)
        assertEquals("Provider 连接失败：socket closed", result.message)
    }

    @Test
    fun test_returnsMissingApiKeyWhenDescriptorRequiresOne() = runTest {
        val tester = ProviderConnectionTester(providerRegistry = registeredRegistry())

        val result = tester.test(provider(), null)

        assertFalse(result.ok)
        assertEquals(null, result.statusCode)
        assertEquals("API Key 缺失。", result.message)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun test_usesOllamaTagsWithoutApiKey() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"models":[]}"""),
        )
        val tester = ProviderConnectionTester(providerRegistry = registeredRegistry())

        val result = tester.test(provider(type = ProviderType.Ollama), null)
        val recorded = server.takeRequest()

        assertTrue(result.ok)
        assertEquals("/api/tags", recorded.path)
        assertEquals(null, recorded.getHeader("Authorization"))
    }

    @Test
    fun test_returnsUnsupportedWhenProviderHasNoChatImplementation() = runTest {
        val tester = ProviderConnectionTester(providerRegistry = registeredRegistry())

        val result = tester.test(provider(type = ProviderType.Anthropic), "test-key")

        assertFalse(result.ok)
        assertEquals(null, result.statusCode)
        assertEquals("当前 Provider 暂未接入聊天发送：Anthropic。", result.message)
        assertEquals(0, server.requestCount)
    }

    private fun provider(type: ProviderType = ProviderType.OpenAICompatible): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = "OpenAI compatible",
            type = type,
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            apiKeyRef = null,
            headers = emptyMap(),
            models = emptyList(),
            defaultModel = null,
            enabled = true,
        )

    private fun registeredRegistry(): ProviderRegistry =
        ProviderRegistry().apply {
            val provider = ProviderConnectionTesterChatProvider()
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.OpenAICompatible)), provider)
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.Ollama)), provider)
        }
}

private class ProviderConnectionTesterChatProvider : ChatProvider {
    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse =
        ProviderTextResponse("")

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> =
        emptyFlow()
}
