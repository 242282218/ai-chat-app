package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.ModelCapabilitySource
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.ProviderRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProviderModelDiscoveryClientTest {
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
    fun discover_returnsOpenAiModelConfigs() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"id":"model-a"},{"id":" model-b "},{"id":"model-a"}]}"""),
        )
        val client = ProviderModelDiscoveryClient(providerRegistry = registeredRegistry())

        val result = client.discover(provider(), "test-key")

        assertTrue(result.ok)
        assertEquals("连接正常，发现 2 个模型", result.message)
        assertEquals(listOf("model-a", "model-b"), result.models.map { it.id })
        assertEquals(listOf("model-a", "model-b"), result.models.map { it.displayName })
        assertEquals(
            listOf(ModelCapabilitySource.ProviderDiscovery, ModelCapabilitySource.ProviderDiscovery),
            result.models.map { it.capability?.source },
        )
    }

    @Test
    fun discover_returnsOllamaTagsWithoutApiKey() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"models":[{"name":"llama3"},{"name":"qwen2"}]}"""),
        )
        val client = ProviderModelDiscoveryClient(providerRegistry = registeredRegistry())

        val result = client.discover(provider(type = ProviderType.Ollama), null)
        val recorded = server.takeRequest()

        assertTrue(result.ok)
        assertEquals(listOf("llama3", "qwen2"), result.models.map { it.id })
        assertEquals("/api/tags", recorded.path)
        assertEquals(null, recorded.getHeader("Authorization"))
    }

    @Test
    fun discover_doesNotMarkPlainChatModelsAsImageGenerationCapable() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"id":"codex-auto-review"},{"id":"gpt-image-1"}]}"""),
        )
        val client = ProviderModelDiscoveryClient(providerRegistry = registeredRegistry())

        val result = client.discover(provider(type = ProviderType.NewApi), "test-key")

        assertTrue(result.ok)
        val chatModel = result.models.single { it.id == "codex-auto-review" }
        val imageModel = result.models.single { it.id == "gpt-image-1" }
        assertEquals(false, chatModel.capability?.imageGeneration)
        assertEquals(true, chatModel.capability?.text)
        assertEquals(true, imageModel.capability?.imageGeneration)
        assertEquals(false, imageModel.capability?.text)
        assertEquals(ModelCapabilitySource.ProviderDiscovery, chatModel.capability?.source)
    }

    @Test
    fun discover_filtersSensitiveStoredHeaders() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"id":"model-a"}]}"""),
        )
        val client = ProviderModelDiscoveryClient(providerRegistry = registeredRegistry())

        val result = client.discover(
            provider(
                headers = mapOf(
                    "Authorization" to "Bearer stale-key",
                    "x-api-key" to "stale-secret",
                    "X-Trace" to "trace-1",
                ),
            ),
            "fresh-key",
        )
        val recorded = server.takeRequest()

        assertTrue(result.ok)
        assertEquals("Bearer fresh-key", recorded.getHeader("Authorization"))
        assertEquals(null, recorded.getHeader("x-api-key"))
        assertEquals("trace-1", recorded.getHeader("X-Trace"))
    }

    @Test
    fun discover_rejectsDecoratedBaseUrlBeforeRequest() = runTest {
        val client = ProviderModelDiscoveryClient(providerRegistry = registeredRegistry())

        val result = client.discover(
            provider(baseUrl = "${server.url("/v1").toString().trimEnd('/')}?q=1"),
            "test-key",
        )

        assertEquals(false, result.ok)
        assertEquals("Provider URL 无效。", result.message)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun discover_keepsCodeAndLongContextHintsAsChatModels() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"id":"qwen2.5-coder-32b"},{"id":"gpt-4.1-128k"}]}"""),
        )
        val client = ProviderModelDiscoveryClient(providerRegistry = registeredRegistry())

        val result = client.discover(provider(type = ProviderType.NewApi), "test-key")

        assertTrue(result.ok)
        val codeModel = result.models.single { it.id == "qwen2.5-coder-32b" }
        val contextNamedModel = result.models.single { it.id == "gpt-4.1-128k" }
        assertEquals(true, codeModel.capability?.text)
        assertEquals(false, codeModel.capability?.imageGeneration)
        assertEquals(true, contextNamedModel.capability?.text)
        assertEquals(false, contextNamedModel.capability?.imageGeneration)
    }

    private fun provider(
        type: ProviderType = ProviderType.OpenAICompatible,
        baseUrl: String = server.url("/v1").toString().trimEnd('/'),
        headers: Map<String, String> = emptyMap(),
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = "OpenAI compatible",
            type = type,
            baseUrl = baseUrl,
            apiKeyRef = null,
            headers = headers,
            models = emptyList(),
            defaultModel = null,
            enabled = true,
        )

    private fun registeredRegistry(): ProviderRegistry =
        ProviderRegistry().apply {
            val provider = ProviderModelDiscoveryClientChatProvider()
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.OpenAICompatible)), provider)
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.NewApi)), provider)
            register(requireNotNull(ProviderRegistry.builtInDescriptor(ProviderType.Ollama)), provider)
        }
}

private class ProviderModelDiscoveryClientChatProvider : ChatProvider {
    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse =
        ProviderTextResponse("")

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> =
        emptyFlow()
}
