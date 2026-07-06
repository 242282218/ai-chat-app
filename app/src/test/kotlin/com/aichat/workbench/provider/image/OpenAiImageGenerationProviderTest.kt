package com.aichat.workbench.provider.image

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.api.ProviderHttpException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiImageGenerationProviderTest {
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
    fun generate_postsImageRequestAndParsesBase64Images() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"b64_json":"aW1hZ2U=","revised_prompt":"A cat"}]}"""),
        )
        val provider = provider()

        val response = provider.generate(request())
        val recorded = server.takeRequest()
        val body = recorded.body.readUtf8()

        assertEquals("/v1/images/generations", recorded.path)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertEquals("trace-1", recorded.getHeader("X-Trace"))
        assertEquals(null, recorded.getHeader("x-api-key"))
        assertTrue(body.contains(""""model":"gpt-image-1""""))
        assertTrue(body.contains(""""prompt":"A cat""""))
        assertTrue(body.contains(""""n":1"""))
        assertEquals("aW1hZ2U=", response.images.single().base64)
        assertEquals("A cat", response.images.single().revisedPrompt)
    }

    @Test
    fun generate_appendsV1ForCustomRootBaseUrl() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"b64_json":"aW1hZ2U="}]}"""),
        )
        val provider = provider()

        provider.generate(
            request(
                type = ProviderType.Custom,
                baseUrl = server.url("/").toString().trimEnd('/'),
            ),
        )
        val recorded = server.takeRequest()

        assertEquals("/v1/images/generations", recorded.path)
    }

    @Test
    fun generate_rejectsHttpUrlImagesBeforeDownload() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"url":"${server.url("/generated.png")}"}]}"""),
        )
        val provider = provider()

        val error = runCatching {
            provider.generate(request())
        }.exceptionOrNull()
        val postRequest = server.takeRequest()

        require(error is IllegalArgumentException)
        assertTrue(error.message.orEmpty().contains("只支持 HTTPS 图片 URL"))
        assertEquals("/v1/images/generations", postRequest.path)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun generate_rejectsPrivateHttpsUrlImagesBeforeDownload() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"url":"https://127.0.0.1/generated.png"}]}"""),
        )
        val provider = provider()

        val error = runCatching {
            provider.generate(request())
        }.exceptionOrNull()
        val postRequest = server.takeRequest()

        require(error is IllegalArgumentException)
        assertTrue(error.message.orEmpty().contains("不支持下载本地或内网图片 URL"))
        assertEquals("/v1/images/generations", postRequest.path)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun generate_rejectsUrlImagesWithUserInfoBeforeDownload() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":[{"url":"https://user:pass@example.com/generated.png"}]}"""),
        )
        val provider = provider()

        val error = runCatching {
            provider.generate(request())
        }.exceptionOrNull()
        val postRequest = server.takeRequest()

        require(error is IllegalArgumentException)
        assertTrue(error.message.orEmpty().contains("图片 URL 不支持用户信息"))
        assertEquals("/v1/images/generations", postRequest.path)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun generate_mapsProviderErrors() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":{"message":"slow down"}}"""),
        )
        val provider = provider()

        val error = runCatching {
            provider.generate(request())
        }.exceptionOrNull()

        require(error is ProviderHttpException)
        assertEquals("rate_limited", error.error.code)
        assertEquals("slow down", error.error.message)
        assertEquals(429, error.error.statusCode)
    }

    @Test
    fun generate_mapsProviderServerErrorsAsRetryable() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":{"message":"temporary outage"}}"""),
        )
        val provider = provider()

        val error = runCatching {
            provider.generate(request())
        }.exceptionOrNull()

        require(error is ProviderHttpException)
        assertEquals("provider_unavailable", error.error.code)
        assertEquals("temporary outage", error.error.message)
        assertEquals(500, error.error.statusCode)
        assertEquals(true, error.error.retryable)
    }

    private fun request(
        type: ProviderType = ProviderType.OpenAI,
        baseUrl: String = server.url("/v1").toString().trimEnd('/'),
    ): ImageGenerationProviderRequest =
        ImageGenerationProviderRequest(
            provider = ProviderConfig(
                id = ProviderId("provider-1"),
                name = "OpenAI",
                type = type,
                baseUrl = baseUrl,
                apiKeyRef = null,
                headers = mapOf(
                    "X-Trace" to "trace-1",
                    "x-api-key" to "stale-secret",
                ),
                models = emptyList(),
                defaultModel = null,
                enabled = true,
            ),
            apiKey = "test-key",
            model = "gpt-image-1",
            prompt = "A cat",
            size = "1024x1024",
            quality = "auto",
            count = 1,
        )

    private fun provider(): OpenAiImageGenerationProvider =
        OpenAiImageGenerationProvider(client = OkHttpClient())
}
