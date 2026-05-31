package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import kotlinx.coroutines.test.runTest
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
        val tester = ProviderConnectionTester()

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
                .setBody("""{"error":"unauthorized"}"""),
        )
        val tester = ProviderConnectionTester()

        val result = tester.test(provider(), "bad-key")

        assertFalse(result.ok)
        assertEquals(401, result.statusCode)
        assertEquals("Provider returned HTTP 401", result.message)
    }

    private fun provider(): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = "OpenAI compatible",
            type = ProviderType.OpenAICompatible,
            baseUrl = server.url("/v1").toString().trimEnd('/'),
            apiKeyRef = null,
            headers = emptyMap(),
            models = emptyList(),
            defaultModel = null,
            enabled = true,
        )
}
