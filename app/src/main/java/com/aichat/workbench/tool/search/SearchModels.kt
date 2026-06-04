package com.aichat.workbench.tool.search

import java.time.Instant

enum class SearchProvider {
    Tavily,
}

data class SearchConfig(
    val enabled: Boolean,
    val provider: SearchProvider,
    val baseUrl: String,
    val apiKey: String,
    val maxResults: Int,
    val searchDepth: String,
    val topic: String,
)

data class SearchResponse(
    val query: String,
    val fetchedAt: Instant,
    val results: List<SearchResult>,
)

data class SearchResult(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: Instant?,
)
