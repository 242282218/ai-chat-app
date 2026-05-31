package com.aichat.workbench.provider.openai

import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderError
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
import com.aichat.workbench.provider.http.parseSse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

open class OpenAiChatProvider(
    private val client: OkHttpClient = OkHttpClient(),
    private val useResponsesApi: Boolean = true,
) : ChatProvider {
    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse {
        if (!useResponsesApi) {
            return executeChatCompletion(request, stream = false)
        }
        val response = execute(request.toResponsesRequest(stream = false))
        response.use {
            if (it.code == 404 && request.provider.type == ProviderType.OpenAI) {
                return executeChatCompletion(request, stream = false)
            }
            it.requireSuccessful()
            return ProviderTextResponse(content = parseResponsesText(it.bodyText()))
        }
    }

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> =
        flow {
            if (!useResponsesApi) {
                emitChatCompletionStream(request)
                return@flow
            }
            val response = execute(request.toResponsesRequest(stream = true))
            response.use {
                if (it.code == 404 && request.provider.type == ProviderType.OpenAI) {
                    emitChatCompletionStream(request)
                    return@flow
                }
                it.requireSuccessful()
                for (event in parseSse(it.requireBody().byteStream())) {
                    mapResponsesSse(event.data)?.let { mapped -> emit(mapped) }
                }
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun executeChatCompletion(
        request: ChatProviderRequest,
        stream: Boolean,
    ): ProviderTextResponse {
        val response = execute(request.toChatCompletionsRequest(stream))
        response.use {
            it.requireSuccessful()
            return ProviderTextResponse(content = parseChatCompletionText(it.bodyText()))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ProviderStreamEvent>.emitChatCompletionStream(
        request: ChatProviderRequest,
    ) {
        val response = execute(request.toChatCompletionsRequest(stream = true))
        response.use {
            it.requireSuccessful()
            for (event in parseSse(it.requireBody().byteStream())) {
                mapChatCompletionSse(event.data)?.let { mapped -> emit(mapped) }
            }
        }
    }

    private fun ChatProviderRequest.toResponsesRequest(stream: Boolean): Request {
        val json = JSONObject()
            .put("model", model)
            .put("input", messages.toResponsesInput())
            .put("stream", stream)
            .put("store", false)
        systemPrompt?.takeIf { it.isNotBlank() }?.let { json.put("instructions", it) }
        json.putModelParameters(parameters, responses = true)

        return postJson("${provider.baseUrl.trimEnd('/')}/responses", json)
    }

    private fun ChatProviderRequest.toChatCompletionsRequest(stream: Boolean): Request {
        val json = JSONObject()
            .put("model", model)
            .put("messages", messages.toChatMessages(systemPrompt))
            .put("stream", stream)
            .put("store", false)
        json.putModelParameters(parameters, responses = false)

        return postJson("${provider.baseUrl.trimEnd('/')}/chat/completions", json)
    }

    private fun ChatProviderRequest.postJson(url: String, json: JSONObject): Request {
        val builder = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody(JSON))
            .header("Accept", if (json.optBoolean("stream")) "text/event-stream" else "application/json")
            .header("Content-Type", "application/json")

        apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        provider.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun List<com.aichat.workbench.provider.api.ProviderChatMessage>.toResponsesInput(): JSONArray {
        val array = JSONArray()
        forEach { message ->
            if (message.role != MessageRole.System) {
                array.put(
                    JSONObject()
                        .put("role", message.role.toProviderRole())
                        .put("content", message.content),
                )
            }
        }
        return array
    }

    private fun List<com.aichat.workbench.provider.api.ProviderChatMessage>.toChatMessages(
        systemPrompt: String?,
    ): JSONArray {
        val array = JSONArray()
        systemPrompt?.takeIf { it.isNotBlank() }?.let {
            array.put(JSONObject().put("role", "system").put("content", it))
        }
        forEach { message ->
            array.put(
                JSONObject()
                    .put("role", message.role.toProviderRole())
                    .put("content", message.content),
            )
        }
        return array
    }

    private fun MessageRole.toProviderRole(): String =
        when (this) {
            MessageRole.System -> "system"
            MessageRole.User -> "user"
            MessageRole.Assistant -> "assistant"
            MessageRole.Tool -> "tool"
        }

    private fun JSONObject.putModelParameters(parameters: ModelParameters, responses: Boolean) {
        parameters.temperature?.let { put("temperature", it) }
        parameters.topP?.let { put("top_p", it) }
        parameters.maxTokens?.let { put(if (responses) "max_output_tokens" else "max_tokens", it) }
    }

    private fun parseResponsesText(body: String): String {
        val json = JSONObject(body)
        json.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = json.optJSONArray("output") ?: return ""
        val text = StringBuilder()
        for (index in 0 until output.length()) {
            val item = output.optJSONObject(index) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (contentIndex in 0 until content.length()) {
                val part = content.optJSONObject(contentIndex) ?: continue
                if (part.optString("type") == "output_text") {
                    text.append(part.optString("text"))
                }
            }
        }
        return text.toString()
    }

    private fun parseChatCompletionText(body: String): String =
        JSONObject(body)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")

    private fun mapResponsesSse(data: String): ProviderStreamEvent? {
        if (data == "[DONE]") return ProviderStreamEvent.Completed
        val json = JSONObject(data)
        return when (json.optString("type")) {
            "response.output_text.delta" -> ProviderStreamEvent.TextDelta(json.optString("delta"))
            "response.completed" -> ProviderStreamEvent.Completed
            "response.failed", "error" -> ProviderStreamEvent.Failed(json.toProviderError(statusCode = null))
            else -> null
        }
    }

    private fun mapChatCompletionSse(data: String): ProviderStreamEvent? {
        if (data == "[DONE]") return ProviderStreamEvent.Completed
        val json = JSONObject(data)
        val choice = json.getJSONArray("choices").getJSONObject(0)
        val content = choice.optJSONObject("delta")?.optString("content").orEmpty()
        return when {
            content.isNotEmpty() -> ProviderStreamEvent.TextDelta(content)
            !choice.isNull("finish_reason") -> ProviderStreamEvent.Completed
            else -> null
        }
    }

    private fun Response.requireSuccessful() {
        if (isSuccessful) return
        val bodyText = bodyText()
        throw ProviderHttpException(parseHttpError(code, bodyText))
    }

    private fun Response.bodyText(): String =
        body?.string().orEmpty()

    private fun Response.requireBody() =
        requireNotNull(body) { "Provider response body is empty." }

    private fun parseHttpError(statusCode: Int, body: String): ProviderError {
        val message = runCatching {
            JSONObject(body).getJSONObject("error").optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Provider request failed."
        val code = when (statusCode) {
            401 -> "authentication_failed"
            429 -> "rate_limited"
            in 500..599 -> "provider_unavailable"
            else -> "provider_error"
        }
        return ProviderError(
            code = code,
            message = message,
            statusCode = statusCode,
            retryable = statusCode == 429 || statusCode in 500..599,
        )
    }

    private fun JSONObject.toProviderError(statusCode: Int?): ProviderError {
        val error = optJSONObject("error") ?: this
        return ProviderError(
            code = error.optString("code", "provider_error"),
            message = error.optString("message", "Provider request failed."),
            statusCode = statusCode,
            retryable = statusCode == 429 || (statusCode != null && statusCode in 500..599),
        )
    }

    private fun execute(request: Request): Response =
        client.newCall(request).execute()

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
