package com.aichat.workbench.tool.search

import java.net.SocketTimeoutException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TavilyLocalSearchClientTest {
    private lateinit var server: MockWebServer
    private val clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

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
    fun search_postsTavilyRequestAndParsesResults() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "results": [
                        {
                          "title": "AI search update",
                          "url": "https://www.example.com/ai-search",
                          "content": "Search summary.",
                          "published_date": "2026-05-31"
                        },
                        {
                          "title": "",
                          "url": "https://wire.example.com/brief",
                          "content": null,
                          "published_date": "2026-05-30T12:00:00Z"
                        },
                        {
                          "title": "Missing URL",
                          "url": "",
                          "content": "ignored"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
        )
        val client = TavilyLocalSearchClient(clock = clock)

        val response = client.search(
            query = " AI news ",
            config = searchConfig(
                baseUrl = server.url("/").toString(),
                maxResults = 99,
                searchDepth = "ADVANCED",
                topic = "NEWS",
            ),
        )
        val recorded = server.takeRequest()
        val requestJson = JSONObject(recorded.body.readUtf8())

        assertEquals("/search", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer test-search-key", recorded.getHeader("Authorization"))
        assertEquals("application/json", recorded.getHeader("Accept"))
        assertEquals("AI news", requestJson.getString("query"))
        assertEquals(20, requestJson.getInt("max_results"))
        assertEquals("advanced", requestJson.getString("search_depth"))
        assertEquals("news", requestJson.getString("topic"))
        assertEquals(false, requestJson.getBoolean("include_answer"))
        assertEquals(false, requestJson.getBoolean("include_raw_content"))

        assertEquals("AI news", response.query)
        assertEquals(clock.instant(), response.fetchedAt)
        assertEquals(2, response.results.size)
        assertEquals("AI search update", response.results[0].title)
        assertEquals("Search summary.", response.results[0].summary)
        assertEquals("https://www.example.com/ai-search", response.results[0].url)
        assertEquals("example.com", response.results[0].source)
        assertEquals("2026-05-31T00:00:00Z", response.results[0].publishedAt.toString())
        assertEquals("https://wire.example.com/brief", response.results[1].title)
        assertEquals("", response.results[1].summary)
        assertEquals("wire.example.com", response.results[1].source)
        assertEquals("2026-05-30T12:00:00Z", response.results[1].publishedAt.toString())
    }

    @Test
    fun search_returnsEmptyResults() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"results":[]}"""),
        )
        val client = TavilyLocalSearchClient(clock = clock)

        val response = client.search("nothing new", searchConfig(baseUrl = server.url("/").toString()))

        assertEquals("nothing new", response.query)
        assertEquals(clock.instant(), response.fetchedAt)
        assertEquals(emptyList<SearchResult>(), response.results)
    }

    @Test
    fun search_mapsUnauthorizedError() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"code":"invalid_api_key","message":"Invalid API key"}"""),
        )
        val client = TavilyLocalSearchClient(clock = clock)

        try {
            client.search("AI news", searchConfig(baseUrl = server.url("/").toString()))
            fail("Expected LocalSearchHttpException")
        } catch (error: LocalSearchHttpException) {
            assertEquals(401, error.statusCode)
            assertEquals("invalid_api_key", error.code)
            assertEquals("Invalid API key", error.message)
        }
    }

    @Test
    fun search_mapsRateLimitErrorDetail() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"detail":{"message":"Rate limit exceeded"}}"""),
        )
        val client = TavilyLocalSearchClient(clock = clock)

        try {
            client.search("AI news", searchConfig(baseUrl = server.url("/").toString()))
            fail("Expected LocalSearchHttpException")
        } catch (error: LocalSearchHttpException) {
            assertEquals(429, error.statusCode)
            assertEquals("local_search_http_429", error.code)
            assertEquals("Rate limit exceeded", error.message)
        }
    }

    @Test
    fun search_mapsServerErrorFallbackMessage() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"server_error","message":"Search backend unavailable"}"""),
        )
        val client = TavilyLocalSearchClient(clock = clock)

        try {
            client.search("AI news", searchConfig(baseUrl = server.url("/").toString()))
            fail("Expected LocalSearchHttpException")
        } catch (error: LocalSearchHttpException) {
            assertEquals(500, error.statusCode)
            assertEquals("server_error", error.code)
            assertEquals("Search backend unavailable", error.message)
        }
    }

    @Test
    fun search_surfacesTimeout() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"results":[]}""")
                .setBodyDelay(300, TimeUnit.MILLISECONDS),
        )
        val httpClient = OkHttpClient.Builder()
            .readTimeout(Duration.ofMillis(50))
            .build()
        val client = TavilyLocalSearchClient(client = httpClient, clock = clock)

        try {
            client.search("AI news", searchConfig(baseUrl = server.url("/").toString()))
            fail("Expected SocketTimeoutException")
        } catch (error: SocketTimeoutException) {
            assertTrue(error.message.orEmpty().isNotBlank())
        }
    }

    private fun searchConfig(
        baseUrl: String,
        maxResults: Int = 5,
        searchDepth: String = "basic",
        topic: String = "general",
    ): SearchConfig =
        SearchConfig(
            enabled = true,
            provider = SearchProvider.Tavily,
            baseUrl = baseUrl,
            apiKey = "test-search-key",
            maxResults = maxResults,
            searchDepth = searchDepth,
            topic = topic,
        )
}
