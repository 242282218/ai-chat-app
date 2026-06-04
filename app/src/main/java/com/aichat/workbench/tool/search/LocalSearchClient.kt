package com.aichat.workbench.tool.search

import java.net.URI
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

interface LocalSearchClient {
    suspend fun search(query: String, config: SearchConfig): SearchResponse
}

class TavilyLocalSearchClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
) : LocalSearchClient {
    override suspend fun search(query: String, config: SearchConfig): SearchResponse {
        val normalizedQuery = query.trim()
        require(normalizedQuery.isNotBlank()) { "搜索关键词不能为空。" }
        return withContext(Dispatchers.IO) {
            val body = searchJson.encodeToString(
                TavilySearchRequestJson(
                    query = normalizedQuery,
                    maxResults = config.maxResults.coerceIn(MIN_RESULTS, MAX_RESULTS),
                    searchDepth = config.searchDepth.tavilySearchDepth(),
                    topic = config.topic.tavilyTopic(),
                    includeAnswer = false,
                    includeRawContent = false,
                ),
            )
            client.newCall(postJson(config.baseUrl.searchUrl(), body, config.apiKey)).execute().use { response ->
                response.requireSuccessful()
                response.parseTavilySearchResponse(query = normalizedQuery, fetchedAt = clock.instant())
            }
        }
    }

    private fun Response.parseTavilySearchResponse(
        query: String,
        fetchedAt: Instant,
    ): SearchResponse {
        val json = searchJson.decodeFromString<TavilySearchResponseJson>(bodyText())
        return SearchResponse(
            query = query,
            fetchedAt = fetchedAt,
            results = json.results.mapNotNull { result -> result.toDomainOrNull() },
        )
    }

    private fun TavilyResultJson.toDomainOrNull(): SearchResult? {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return null
        val normalizedTitle = title.trim().ifBlank { normalizedUrl }
        return SearchResult(
            title = normalizedTitle,
            summary = content.orEmpty().trim(),
            url = normalizedUrl,
            source = normalizedUrl.hostOrFallback(),
            publishedAt = publishedDate?.parseInstantOrNull(),
        )
    }

    private fun postJson(url: String, body: String, apiKey: String): Request {
        val trimmedKey = apiKey.trim()
        require(trimmedKey.isNotBlank()) { "搜索 API Key 未配置。" }
        return Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $trimmedKey")
            .build()
    }

    private fun Response.requireSuccessful() {
        if (isSuccessful) return
        val errorBody = bodyText()
        val parsed = errorBody.toTavilyErrorOrNull()
        throw LocalSearchHttpException(
            statusCode = code,
            code = parsed?.code ?: "local_search_http_$code",
            message = parsed?.message ?: httpFallbackErrorMessage(code, errorBody),
        )
    }

    private fun Response.bodyText(): String =
        body?.string().orEmpty()
}

class LocalSearchHttpException(
    val statusCode: Int,
    val code: String,
    message: String,
) : RuntimeException(message)

private fun String.searchUrl(): String =
    trim().trimEnd('/') + "/search"

private fun String.tavilySearchDepth(): String =
    trim().lowercase().takeIf { it in setOf("basic", "advanced") } ?: "basic"

private fun String.tavilyTopic(): String =
    trim().lowercase().takeIf { it in setOf("general", "news", "finance") } ?: "general"

private fun String.hostOrFallback(): String =
    runCatching { URI(this).host }
        .getOrNull()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }
        ?: "web"

private fun String.parseInstantOrNull(): Instant? =
    trim()
        .takeIf { it.isNotBlank() && it != "null" }
        ?.let { value ->
            runCatching { Instant.parse(value) }
                .getOrElse {
                    runCatching { java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant() }
                        .getOrNull()
                }
        }

private fun String.toTavilyErrorOrNull(): TavilyErrorJson? =
    runCatching {
        val json = searchJson.decodeFromString<JsonObject>(this)
        val detail = json["detail"]
        val message = when (detail) {
            is JsonPrimitive -> detail.contentOrNull.orEmpty()
            is JsonObject -> detail["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            else -> ""
        }.ifBlank {
            json["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        TavilyErrorJson(
            code = json["code"]?.jsonPrimitive?.contentOrNull
                ?: json["error"]?.jsonPrimitive?.contentOrNull,
            message = message.ifBlank { "本地搜索请求失败。" },
        )
    }.getOrNull()

private fun httpFallbackErrorMessage(statusCode: Int, body: String): String {
    val preview = body.trim().replace(errorWhitespace, " ")
    if (preview.isBlank()) {
        return "本地搜索 HTTP $statusCode 请求失败。"
    }
    val suffix = if (preview.length > MAX_ERROR_PREVIEW_LENGTH) "..." else ""
    return "本地搜索 HTTP $statusCode：${preview.take(MAX_ERROR_PREVIEW_LENGTH)}$suffix"
}

private val JSON = "application/json; charset=utf-8".toMediaType()
private const val MIN_RESULTS = 1
private const val MAX_RESULTS = 20
private const val MAX_ERROR_PREVIEW_LENGTH = 240
private val errorWhitespace = Regex("\\s+")

private val searchJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
private data class TavilySearchRequestJson(
    val query: String,
    @SerialName("max_results") val maxResults: Int,
    @SerialName("search_depth") val searchDepth: String,
    val topic: String,
    @SerialName("include_answer") val includeAnswer: Boolean,
    @SerialName("include_raw_content") val includeRawContent: Boolean,
)

@Serializable
private data class TavilySearchResponseJson(
    val results: List<TavilyResultJson> = emptyList(),
)

@Serializable
private data class TavilyResultJson(
    val title: String = "",
    val url: String = "",
    val content: String? = null,
    @SerialName("published_date") val publishedDate: String? = null,
)

private data class TavilyErrorJson(
    val code: String? = null,
    val message: String,
)
