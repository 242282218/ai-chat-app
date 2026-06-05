package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.tool.search.LocalSearchClient
import com.aichat.workbench.tool.search.SearchConfig
import com.aichat.workbench.tool.search.SearchProvider
import com.aichat.workbench.tool.search.SearchResponse
import com.aichat.workbench.tool.search.SearchResult
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalWebSearchToolTest {
    @Test
    fun executeNormalizesCaseAndReturnsCitableSources() = runTest {
        val searchClient = RecordingSearchClient(
            SearchResponse(
                query = "AI news",
                fetchedAt = Instant.parse("2026-06-01T00:00:00Z"),
                results = listOf(
                    SearchResult(
                        title = "AI update",
                        summary = "Search summary.",
                        url = "https://example.com/ai",
                        source = "example.com",
                        publishedAt = Instant.parse("2026-05-31T00:00:00Z"),
                    ),
                ),
            ),
        )
        val tool = LocalWebSearchTool(
            searchConfigProvider = { searchConfig() },
            searchClient = searchClient,
        )

        val output = tool.execute(
            request(
                """
                {"query":" AI news ","maxResults":3,"searchDepth":"ADVANCED","topic":"NEWS"}
                """.trimIndent(),
            ),
        ).jsonOutput()

        val captured = searchClient.requests.single()
        assertEquals("AI news", captured.query)
        assertEquals(3, captured.config.maxResults)
        assertEquals("advanced", captured.config.searchDepth)
        assertEquals("news", captured.config.topic)
        assertTrue(output.contains(""""url":"https://example.com/ai""""))
        assertTrue(output.contains(""""source":"example.com""""))
        assertTrue(output.contains(""""publishedAt":"2026-05-31T00:00:00Z""""))
    }

    private fun request(arguments: String): LocalToolRequest =
        LocalToolRequest(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                id = ToolCallId("call"),
                name = "web_search_local",
                arguments = arguments,
            ),
        )

    private fun LocalToolExecution.jsonOutput(): String =
        (output as ToolOutput.Json).value
}

private data class SearchRequest(
    val query: String,
    val config: SearchConfig,
)

private class RecordingSearchClient(
    private val response: SearchResponse,
) : LocalSearchClient {
    val requests = mutableListOf<SearchRequest>()

    override suspend fun search(query: String, config: SearchConfig): SearchResponse {
        requests += SearchRequest(query, config)
        return response.copy(query = query)
    }
}

private fun searchConfig(): SearchConfig =
    SearchConfig(
        enabled = true,
        provider = SearchProvider.Tavily,
        baseUrl = "https://api.tavily.com",
        apiKey = "test-key",
        maxResults = 5,
        searchDepth = "basic",
        topic = "general",
    )
