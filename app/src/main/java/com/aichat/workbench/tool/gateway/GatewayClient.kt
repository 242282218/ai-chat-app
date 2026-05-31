package com.aichat.workbench.tool.gateway

import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolManifest
import com.aichat.workbench.tool.model.ToolSource
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

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

    suspend fun search(baseUrl: String, query: String): SearchResponse =
        withContext(Dispatchers.IO) {
            val json = JSONObject().put("query", query)
            client.newCall(postJson("${baseUrl.trimEnd('/')}/v1/search", json)).execute().use { response ->
                response.requireSuccessful()
                parseSearchResponse(response.bodyText())
            }
        }

    suspend fun runSandbox(
        baseUrl: String,
        language: String,
        code: String,
        timeoutSeconds: Int,
    ): SandboxRunResponse =
        withContext(Dispatchers.IO) {
            val json = JSONObject()
                .put("language", language)
                .put("code", code)
                .put("timeoutSeconds", timeoutSeconds)
            client.newCall(postJson("${baseUrl.trimEnd('/')}/v1/sandbox/run", json)).execute().use { response ->
                response.requireSuccessful()
                parseSandboxRunResponse(response.bodyText())
            }
        }

    fun parseToolManifest(body: String): ToolManifest {
        val json = JSONObject(body)
        val tools = json.getJSONArray("tools")
        return ToolManifest(
            version = json.getInt("version"),
            generatedAt = Instant.parse(json.getString("generatedAt")),
            tools = buildList {
                for (index in 0 until tools.length()) {
                    val tool = tools.getJSONObject(index)
                    add(
                        ToolDescriptor(
                            name = tool.getString("name"),
                            displayName = tool.getString("name").toDisplayName(),
                            description = tool.optString("description"),
                            permissionLevel = ToolPermissionLevel.valueOf(tool.getString("permissionLevel")),
                            inputSchemaJson = tool.getJSONObject("inputSchema").toString(),
                            outputSchemaJson = tool.optJSONObject("outputSchema")?.toString(),
                            timeoutSeconds = tool.optInt("timeoutSeconds").takeIf { it > 0 },
                            source = ToolSource.Gateway,
                        ),
                    )
                }
            },
        )
    }

    private fun parseHealth(body: String): GatewayHealth {
        val json = JSONObject(body)
        return GatewayHealth(
            status = json.optString("status"),
            service = json.optString("service"),
            version = json.optString("version"),
            time = json.optString("time").takeIf { it.isNotBlank() }?.let(Instant::parse),
        )
    }

    fun parseSearchResponse(body: String): SearchResponse {
        val json = JSONObject(body)
        val results = json.getJSONArray("results").toSearchResults()
        return SearchResponse(
            query = json.getString("query"),
            fetchedAt = Instant.parse(json.getString("fetchedAt")),
            results = results,
        )
    }

    fun parseSandboxRunResponse(body: String): SandboxRunResponse {
        val json = JSONObject(body)
        return SandboxRunResponse(
            language = json.getString("language"),
            stdout = json.optString("stdout"),
            stderr = json.optString("stderr"),
            exitCode = json.getInt("exitCode"),
            durationMs = json.optLong("durationMs"),
            timedOut = json.optBoolean("timedOut"),
            truncated = json.optBoolean("truncated"),
        )
    }

    private fun JSONArray.toSearchResults(): List<SearchResult> =
        buildList {
            for (index in 0 until length()) {
                val item = getJSONObject(index)
                add(
                    SearchResult(
                        title = item.getString("title"),
                        summary = item.optString("summary"),
                        url = item.getString("url"),
                        source = item.getString("source"),
                        publishedAt = item.optString("publishedAt")
                            .takeIf { it.isNotBlank() && it != "null" }
                            ?.let(Instant::parse),
                    ),
                )
            }
        }

    private fun get(url: String): Request =
        Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

    private fun postJson(url: String, json: JSONObject): Request =
        Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody(JSON))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Request-Id", UUID.randomUUID().toString())
            .build()

    private fun Response.requireSuccessful() {
        if (isSuccessful) return
        val errorBody = bodyText()
        val gatewayError = errorBody.toGatewayErrorOrNull()
        throw GatewayHttpException(
            statusCode = code,
            gatewayCode = gatewayError?.code ?: "http_$code",
            requestId = gatewayError?.requestId,
            message = gatewayError?.message ?: errorBody.ifBlank { "Gateway request failed." },
        )
    }

    private fun Response.bodyText(): String =
        body?.string().orEmpty()

    private fun String.toDisplayName(): String =
        split('_')
            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    private fun String.toGatewayErrorOrNull(): GatewayErrorResponse? =
        runCatching {
            val json = JSONObject(this)
            val code = json.optString("code")
            if (code.isBlank()) {
                null
            } else {
                GatewayErrorResponse(
                    code = code,
                    message = json.optString("message").ifBlank { "Gateway request failed." },
                    requestId = json.optString("requestId").takeIf { it.isNotBlank() && it != "null" },
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
