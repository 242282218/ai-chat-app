package com.aichat.workbench.provider.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal val providerJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
internal data class ResponsesRequest(
    val model: String,
    val input: List<ResponsesMessage>,
    val stream: Boolean = false,
    val store: Boolean = false,
    val instructions: String? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
    val tools: List<ResponsesTool>? = null,
)

@Serializable
internal data class ResponsesTool(
    val type: String,
    val name: String? = null,
    val description: String? = null,
    val parameters: JsonElement? = null,
    val strict: Boolean? = null,
    val container: ResponsesToolContainer? = null,
)

@Serializable
internal data class ResponsesToolContainer(
    val type: String,
)

@Serializable
internal data class ResponsesMessage(
    val role: String,
    val content: String,
)

@Serializable
internal data class ResponsesResponse(
    @SerialName("output_text") val outputText: String? = null,
    val output: List<ResponsesOutputItem> = emptyList(),
)

@Serializable
internal data class ResponsesOutputItem(
    val id: String? = null,
    val type: String? = null,
    @SerialName("call_id") val callId: String? = null,
    val name: String? = null,
    val arguments: String? = null,
    val content: List<ResponsesContentPart> = emptyList(),
    val result: String? = null,
)

@Serializable
internal data class ResponsesContentPart(
    val type: String? = null,
    val text: String? = null,
)

@Serializable
internal data class ChatCompletionsRequest(
    val model: String,
    val messages: List<ChatCompletionMessage>,
    val stream: Boolean = false,
    val store: Boolean = false,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val tools: List<ChatCompletionTool>? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
    @SerialName("parallel_tool_calls") val parallelToolCalls: Boolean? = null,
)

@Serializable
internal data class ChatCompletionMessage(
    val role: String? = null,
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<ChatCompletionToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
)

@Serializable
internal data class ChatCompletionsResponse(
    val choices: List<ChatCompletionChoice> = emptyList(),
)

@Serializable
internal data class ChatCompletionChoice(
    val message: ChatCompletionMessage? = null,
    val delta: ChatCompletionMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
internal data class ChatCompletionTool(
    val type: String = "function",
    val function: ChatCompletionFunction,
)

@Serializable
internal data class ChatCompletionFunction(
    val name: String,
    val description: String,
    val parameters: JsonElement? = null,
    val strict: Boolean = false,
)

@Serializable
internal data class ChatCompletionToolCall(
    val index: Int? = null,
    val id: String? = null,
    val type: String? = "function",
    val function: ChatCompletionToolCallFunction? = null,
)

@Serializable
internal data class ChatCompletionToolCallFunction(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
internal data class ResponsesSseEvent(
    val type: String? = null,
    val delta: String? = null,
    val item: ResponsesOutputItem? = null,
    val error: ProviderErrorBody? = null,
    val code: String? = null,
    val message: String? = null,
)

@Serializable
internal data class ChatCompletionSseEvent(
    val choices: List<ChatCompletionChoice> = emptyList(),
)

@Serializable
internal data class ProviderErrorEnvelope(
    val error: ProviderErrorBody? = null,
)

@Serializable
internal data class ProviderErrorBody(
    val code: String? = null,
    val message: String? = null,
)
