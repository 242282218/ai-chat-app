package com.aichat.workbench.provider.openai

import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.provider.api.ChatCompletionFunction
import com.aichat.workbench.provider.api.ChatCompletionMessage
import com.aichat.workbench.provider.api.ChatCompletionSseEvent
import com.aichat.workbench.provider.api.ChatCompletionsRequest
import com.aichat.workbench.provider.api.ChatCompletionsResponse
import com.aichat.workbench.provider.api.ChatCompletionTool
import com.aichat.workbench.provider.api.ChatCompletionToolCall
import com.aichat.workbench.provider.api.ChatCompletionToolCallFunction
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderErrorBody
import com.aichat.workbench.provider.api.ProviderErrorEnvelope
import com.aichat.workbench.provider.api.ProviderError
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
import com.aichat.workbench.provider.api.ResponsesMessage
import com.aichat.workbench.provider.api.ResponsesRequest
import com.aichat.workbench.provider.api.ResponsesResponse
import com.aichat.workbench.provider.api.ResponsesSseEvent
import com.aichat.workbench.provider.api.ResponsesTool
import com.aichat.workbench.provider.api.ToolChoice
import com.aichat.workbench.provider.api.providerJson
import com.aichat.workbench.provider.http.parseSse
import com.aichat.workbench.tool.model.ToolDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

open class OpenAiChatProvider(
    private val client: OkHttpClient = OkHttpClient(),
    private val useResponsesApi: Boolean = true,
) : ChatProvider {
    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse {
        if (!useResponsesApi || request.tools.isNotEmpty() || request.hasImageInput()) {
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
            if (!useResponsesApi || request.tools.isNotEmpty() || request.hasImageInput()) {
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
            val toolCalls = ChatToolCallAccumulator()
            for (event in parseSse(it.requireBody().byteStream())) {
                for (mapped in mapChatCompletionSse(event.data, toolCalls)) {
                    emit(mapped)
                }
            }
        }
    }

    private fun ChatProviderRequest.toResponsesRequest(stream: Boolean): Request {
        val body = ResponsesRequest(
            model = model,
            input = messages.toResponsesInput(),
            stream = stream,
            store = false,
            instructions = systemPrompt?.takeIf { it.isNotBlank() },
            temperature = parameters.temperature,
            topP = parameters.topP,
            maxOutputTokens = parameters.maxTokens,
            tools = tools.toResponsesTools(),
        )

        return postJson("${provider.apiBaseUrl()}/responses", providerJson.encodeToString(body), stream)
    }

    private fun ChatProviderRequest.toChatCompletionsRequest(stream: Boolean): Request {
        val body = ChatCompletionsRequest(
            model = model,
            messages = messages.toChatMessages(systemPrompt),
            stream = stream,
            store = false,
            temperature = parameters.temperature,
            topP = parameters.topP,
            maxTokens = parameters.maxTokens,
            tools = tools.toChatTools(),
            toolChoice = toolChoice.toChatToolChoice(),
            parallelToolCalls = tools.takeIf { it.isNotEmpty() }?.let { false },
        )

        return postJson("${provider.apiBaseUrl()}/chat/completions", providerJson.encodeToString(body), stream)
    }

    private fun com.aichat.workbench.domain.model.ProviderConfig.apiBaseUrl(): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (type == ProviderType.Ollama && !trimmed.endsWith("/v1")) "$trimmed/v1" else trimmed
    }

    private fun ChatProviderRequest.postJson(url: String, body: String, stream: Boolean): Request {
        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON))
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .header("Content-Type", "application/json")

        apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        provider.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun List<com.aichat.workbench.provider.api.ProviderChatMessage>.toResponsesInput(): List<ResponsesMessage> =
        filter { it.role != MessageRole.System }
            .map { message ->
                ResponsesMessage(
                    role = message.role.toProviderRole(),
                    content = message.content,
                )
            }

    private fun List<com.aichat.workbench.provider.api.ProviderChatMessage>.toChatMessages(
        systemPrompt: String?,
    ): List<ChatCompletionMessage> =
        buildList {
            systemPrompt?.takeIf { it.isNotBlank() }?.let {
                add(ChatCompletionMessage(role = "system", content = JsonPrimitive(it)))
            }
            this@toChatMessages.forEach { message ->
                add(message.toChatMessage())
            }
        }

    private fun com.aichat.workbench.provider.api.ProviderChatMessage.toChatMessage(): ChatCompletionMessage =
        when (role) {
            MessageRole.Assistant -> ChatCompletionMessage(
                role = role.toProviderRole(),
                content = content.takeIf { it.isNotBlank() }?.let(::JsonPrimitive),
                toolCalls = toolCalls.takeIf { it.isNotEmpty() }?.map { it.toChatToolCall() },
            )
            MessageRole.Tool -> ChatCompletionMessage(
                role = role.toProviderRole(),
                content = JsonPrimitive(content),
                toolCallId = toolCallId?.value,
            )
            else -> ChatCompletionMessage(
                role = role.toProviderRole(),
                content = toChatContent(),
            )
        }

    private fun com.aichat.workbench.provider.api.ProviderChatMessage.toChatContent(): JsonElement =
        if (contentParts.none { it is MessagePart.Image }) {
            JsonPrimitive(content)
        } else {
            buildJsonArray {
                contentParts.forEach { part ->
                    when (part) {
                        is MessagePart.Text -> add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", part.text)
                            },
                        )
                        is MessagePart.Image -> add(
                            buildJsonObject {
                                put("type", "image_url")
                                put(
                                    "image_url",
                                    buildJsonObject {
                                        put("url", part.uri)
                                        put("detail", "auto")
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }

    private fun ChatProviderRequest.hasImageInput(): Boolean =
        messages.any { message -> message.contentParts.any { it is MessagePart.Image } }

    private fun ToolCall.toChatToolCall(): ChatCompletionToolCall =
        ChatCompletionToolCall(
            id = id.value,
            type = "function",
            function = ChatCompletionToolCallFunction(
                name = name,
                arguments = arguments,
            ),
        )

    private fun MessageRole.toProviderRole(): String =
        when (this) {
            MessageRole.System -> "system"
            MessageRole.User -> "user"
            MessageRole.Assistant -> "assistant"
            MessageRole.Tool -> "tool"
        }

    private fun List<ToolDescriptor>.toResponsesTools(): List<ResponsesTool>? =
        takeIf { it.isNotEmpty() }?.map { tool ->
            ResponsesTool(
                name = tool.name,
                description = tool.description,
                parameters = tool.inputSchemaJson.toJsonElementOrNull(),
                strict = false,
            )
        }

    private fun List<ToolDescriptor>.toChatTools(): List<ChatCompletionTool>? =
        takeIf { it.isNotEmpty() }?.map { tool ->
            ChatCompletionTool(
                function = ChatCompletionFunction(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.inputSchemaJson.toJsonElementOrNull(),
                    strict = false,
                ),
            )
        }

    private fun ToolChoice.toChatToolChoice(): JsonElement? =
        when (this) {
            ToolChoice.Auto -> null
            ToolChoice.None -> JsonPrimitive("none")
            is ToolChoice.Named -> providerJson.parseToJsonElement("""{"type":"function","function":{"name":"$name"}}""")
        }

    private fun String.toJsonElementOrNull(): JsonElement? =
        runCatching { providerJson.parseToJsonElement(this) }.getOrNull()

    private fun parseResponsesText(body: String): String {
        val response = providerJson.decodeFromString<ResponsesResponse>(body)
        response.outputText?.takeIf { it.isNotBlank() }?.let { return it }
        return response.output
            .flatMap { it.content }
            .filter { it.type == "output_text" }
            .joinToString(separator = "") { it.text.orEmpty() }
    }

    private fun parseChatCompletionText(body: String): String =
        providerJson.decodeFromString<ChatCompletionsResponse>(body)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()

    private fun mapResponsesSse(data: String): ProviderStreamEvent? {
        if (data == "[DONE]") return ProviderStreamEvent.Completed
        val event = providerJson.decodeFromString<ResponsesSseEvent>(data)
        return when (event.type) {
            "response.output_text.delta" -> ProviderStreamEvent.TextDelta(event.delta.orEmpty())
            "response.completed" -> ProviderStreamEvent.Completed
            "response.failed", "error" -> ProviderStreamEvent.Failed(event.toProviderError(statusCode = null))
            else -> null
        }
    }

    private fun mapChatCompletionSse(
        data: String,
        toolCalls: ChatToolCallAccumulator,
    ): List<ProviderStreamEvent> {
        if (data == "[DONE]") return listOf(ProviderStreamEvent.Completed)
        val choice = providerJson.decodeFromString<ChatCompletionSseEvent>(data).choices.firstOrNull()
            ?: return emptyList()
        val content = choice.delta?.content?.jsonPrimitive?.contentOrNull.orEmpty()
        choice.delta?.toolCalls.orEmpty().forEach(toolCalls::append)
        return when {
            content.isNotEmpty() -> listOf(ProviderStreamEvent.TextDelta(content))
            choice.finishReason == "tool_calls" -> toolCalls.completed().map(ProviderStreamEvent::ToolCallDelta) +
                ProviderStreamEvent.Completed
            choice.finishReason != null -> listOf(ProviderStreamEvent.Completed)
            else -> emptyList()
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
        requireNotNull(body) { "Provider 响应 body 为空。" }

    private fun parseHttpError(statusCode: Int, body: String): ProviderError {
        val message = runCatching {
            providerJson.decodeFromString<ProviderErrorEnvelope>(body).error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Provider 请求失败。"
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

    private fun ResponsesSseEvent.toProviderError(statusCode: Int?): ProviderError {
        val error = error ?: ProviderErrorBody(code = code, message = message)
        return ProviderError(
            code = error.code ?: "provider_error",
            message = error.message ?: "Provider 请求失败。",
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

private class ChatToolCallAccumulator {
    private val calls = linkedMapOf<Int, PartialToolCall>()

    fun append(delta: ChatCompletionToolCall) {
        val index = delta.index ?: calls.size
        val current = calls[index] ?: PartialToolCall()
        calls[index] = current.copy(
            id = delta.id ?: current.id,
            name = delta.function?.name ?: current.name,
            arguments = current.arguments + delta.function?.arguments.orEmpty(),
        )
    }

    fun completed(): List<ToolCall> =
        calls.values.mapNotNull { call ->
            val id = call.id ?: return@mapNotNull null
            val name = call.name ?: return@mapNotNull null
            ToolCall(
                id = ToolCallId(id),
                name = name,
                arguments = call.arguments,
            )
        }

    private data class PartialToolCall(
        val id: String? = null,
        val name: String? = null,
        val arguments: String = "",
    )
}
