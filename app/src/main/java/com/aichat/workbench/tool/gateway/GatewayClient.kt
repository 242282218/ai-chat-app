package com.aichat.workbench.tool.gateway

import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolManifest
import com.aichat.workbench.tool.model.ToolSource
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

data class GatewayHealth(
    val status: String,
    val service: String,
    val version: String,
    val time: Instant?,
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

data class SandboxRunResponse(
    val language: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean,
    val truncated: Boolean,
)

class GatewayClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun health(baseUrl: String): GatewayHealth =
        withContext(Dispatchers.IO) {
            client.newCall(get("${baseUrl.trimEnd('/')}/health")).execute().use { response ->
                response.requireSuccessful()
                parseHealth(response.bodyText())
            }
        }

    suspend fun toolManifest(baseUrl: String): ToolManifest =
        withContext(Dispatchers.IO) {
            client.newCall(get("${baseUrl.trimEnd('/')}/v1/tools/manifest")).execute().use { response ->
                response.requireSuccessful()
                parseToolManifest(response.bodyText())
            }
        }

    suspend fun search(baseUrl: String, query: String, apiToken: String = ""): SearchResponse =
        withContext(Dispatchers.IO) {
            val body = gatewayJson.encodeToString(SearchRequestJson(query))
            client.newCall(postJson("${baseUrl.trimEnd('/')}/v1/search", body, apiToken)).execute().use { response ->
                response.requireSuccessful()
                parseSearchResponse(response.bodyText())
            }
        }

    suspend fun runSandbox(
        baseUrl: String,
        language: String,
        code: String,
        timeoutSeconds: Int,
        apiToken: String = "",
    ): SandboxRunResponse =
        withContext(Dispatchers.IO) {
            val body = gatewayJson.encodeToString(
                SandboxRunRequestJson(
                    language = language,
                    code = code,
                    timeoutSeconds = timeoutSeconds,
                ),
            )
            client.newCall(postJson("${baseUrl.trimEnd('/')}/v1/sandbox/run", body, apiToken)).execute().use { response ->
                response.requireSuccessful()
                parseSandboxRunResponse(response.bodyText())
            }
        }

    fun parseToolManifest(body: String): ToolManifest {
        val json = gatewayJson.decodeFromString<ToolManifestJson>(body)
        return ToolManifest(
            version = json.version,
            generatedAt = Instant.parse(json.generatedAt),
            tools = json.tools.map { tool ->
                ToolDescriptor(
                    name = tool.name,
                    displayName = tool.name.toDisplayName(),
                    description = tool.description.orEmpty(),
                    permissionLevel = ToolPermissionLevel.valueOf(tool.permissionLevel),
                    inputSchemaJson = gatewayJson.encodeToString(tool.inputSchema),
                    outputSchemaJson = tool.outputSchema?.let { gatewayJson.encodeToString(it) },
                    timeoutSeconds = tool.timeoutSeconds?.takeIf { it > 0 },
                    source = ToolSource.Gateway,
                )
            },
        )
    }

    private fun parseHealth(body: String): GatewayHealth {
        val json = gatewayJson.decodeFromString<GatewayHealthJson>(body)
        return GatewayHealth(
            status = json.status.orEmpty(),
            service = json.service.orEmpty(),
            version = json.version.orEmpty(),
            time = json.time?.takeIf { it.isNotBlank() }?.let(Instant::parse),
        )
    }

    fun parseSearchResponse(body: String): SearchResponse {
        val json = gatewayJson.decodeFromString<SearchResponseJson>(body)
        return SearchResponse(
            query = json.query,
            fetchedAt = Instant.parse(json.fetchedAt),
            results = json.results.map { it.toDomain() },
        )
    }

    fun parseSandboxRunResponse(body: String): SandboxRunResponse {
        val json = gatewayJson.decodeFromString<SandboxRunResponseJson>(body)
        return SandboxRunResponse(
            language = json.language,
            stdout = json.stdout.orEmpty(),
            stderr = json.stderr.orEmpty(),
            exitCode = json.exitCode,
            durationMs = json.durationMs ?: 0,
            timedOut = json.timedOut ?: false,
            truncated = json.truncated ?: false,
        )
    }

    private fun SearchResultJson.toDomain(): SearchResult =
        SearchResult(
            title = title,
            summary = summary.orEmpty(),
            url = url,
            source = source,
            publishedAt = publishedAt
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?.let(Instant::parse),
        )

    private fun get(url: String): Request =
        Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

    private fun postJson(url: String, body: String, apiToken: String): Request {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Request-Id", UUID.randomUUID().toString())
        if (apiToken.isNotBlank()) {
            builder.header("Authorization", "Bearer ${apiToken.trim()}")
        }
        return builder.build()
    }

    private fun Response.requireSuccessful() {
        if (isSuccessful) return
        val errorBody = bodyText()
        val gatewayError = errorBody.toGatewayErrorOrNull()
        throw GatewayHttpException(
            statusCode = code,
            gatewayCode = gatewayError?.code ?: "http_$code",
            requestId = gatewayError?.requestId,
            message = gatewayError?.message ?: errorBody.ifBlank { "Gateway 请求失败。" },
        )
    }

    private fun Response.bodyText(): String =
        body?.string().orEmpty()

    private fun String.toDisplayName(): String =
        when (this) {
            "web_search" -> "Web Search"
            "code_sandbox" -> "Code Sandbox"
            else -> split('_')
                .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        }

    private fun String.toGatewayErrorOrNull(): GatewayErrorResponse? =
        runCatching {
            val json = gatewayJson.decodeFromString<GatewayErrorJson>(this)
            val code = json.code.orEmpty()
            if (code.isBlank()) {
                null
            } else {
                GatewayErrorResponse(
                    code = code,
                    message = json.message.orEmpty().ifBlank { "Gateway 请求失败。" },
                    requestId = json.requestId?.takeIf { it.isNotBlank() && it != "null" },
                )
            }
        }.getOrNull()
}

class GatewayHttpException(
    val statusCode: Int,
    val gatewayCode: String,
    val requestId: String?,
    message: String,
) : RuntimeException(message)

private data class GatewayErrorResponse(
    val code: String,
    val message: String,
    val requestId: String?,
)

private val JSON = "application/json; charset=utf-8".toMediaType()

private val gatewayJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
private data class SearchRequestJson(
    val query: String,
)

@Serializable
private data class SandboxRunRequestJson(
    val language: String,
    val code: String,
    val timeoutSeconds: Int,
)

@Serializable
private data class ToolManifestJson(
    val version: Int,
    val generatedAt: String,
    val tools: List<ToolDescriptorJson> = emptyList(),
)

@Serializable
private data class ToolDescriptorJson(
    val name: String,
    val description: String? = null,
    val permissionLevel: String,
    val inputSchema: JsonElement,
    val outputSchema: JsonElement? = null,
    val timeoutSeconds: Int? = null,
)

@Serializable
private data class GatewayHealthJson(
    val status: String? = null,
    val service: String? = null,
    val version: String? = null,
    val time: String? = null,
)

@Serializable
private data class SearchResponseJson(
    val query: String,
    val fetchedAt: String,
    val results: List<SearchResultJson> = emptyList(),
)

@Serializable
private data class SearchResultJson(
    val title: String,
    val summary: String? = null,
    val url: String,
    val source: String,
    val publishedAt: String? = null,
)

@Serializable
private data class SandboxRunResponseJson(
    val language: String,
    val stdout: String? = null,
    val stderr: String? = null,
    val exitCode: Int,
    val durationMs: Long? = null,
    val timedOut: Boolean? = null,
    val truncated: Boolean? = null,
)

@Serializable
private data class GatewayErrorJson(
    val code: String? = null,
    val message: String? = null,
    val requestId: String? = null,
)
