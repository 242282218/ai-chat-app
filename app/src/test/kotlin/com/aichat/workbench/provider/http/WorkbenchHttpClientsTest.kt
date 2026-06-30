package com.aichat.workbench.provider.http

import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class WorkbenchHttpClientsTest {
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
    fun jsonClient_addsDefaultRequestMetadata() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = WorkbenchHttpClients.json()

        client.newCall(Request.Builder().url(server.url("/health")).get().build()).execute().close()
        val recorded = server.takeRequest()

        assertEquals("AIChatWorkbench Android", recorded.getHeader("User-Agent"))
        assertNotNull(recorded.getHeader("X-Request-Id"))
    }

    @Test
    fun jsonClient_preservesExistingRequestId() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = WorkbenchHttpClients.json()

        client.newCall(
            Request.Builder()
                .url(server.url("/health"))
                .get()
                .header("X-Request-Id", "request-1")
                .build(),
        ).execute().close()
        val recorded = server.takeRequest()

        assertEquals("request-1", recorded.getHeader("X-Request-Id"))
    }

    @Test
    fun clients_useWorkloadSpecificTimeouts() {
        val json = WorkbenchHttpClients.json()
        val streaming = WorkbenchHttpClients.streaming()
        val longRunning = WorkbenchHttpClients.longRunning()

        assertEquals(45_000, json.callTimeoutMillis)
        assertEquals(600_000, streaming.callTimeoutMillis)
        assertEquals(300_000, streaming.readTimeoutMillis)
        assertEquals(300_000, longRunning.callTimeoutMillis)
        assertEquals(300_000, longRunning.readTimeoutMillis)
    }
}
