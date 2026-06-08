package com.aichat.workbench.provider.compatible

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.provider.api.ChatCompletionFunction
import com.aichat.workbench.provider.api.ChatCompletionMessage
import com.aichat.workbench.provider.api.ChatCompletionSseEvent
import com.aichat.workbench.provider.api.ChatCompletionTool
import com.aichat.workbench.provider.api.ChatCompletionToolCall
import com.aichat.workbench.provider.api.ChatCompletionToolCallFunction
import com.aichat.workbench.provider.api.ChatCompletionWebSearchOptions
import com.aichat.workbench.provider.api.ChatCompletionsRequest
import com.aichat.workbench.provider.api.ChatCompletionsResponse
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ToolChoice
import com.aichat.workbench.provider.api.providerJson
import com.aichat.workbench.provider.openai.openAiPostJson
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request

internal object OpenAiChatCompletionsProtocol {
    fun buildRequest(
        request: ChatProviderRequest,
        stream: Boolean,
    ): Request {
        val chatTools = request.tools.toChatTools()
        val body = ChatCompletionsRequest(
            model = request.model,
            messages = request.messages.toChatMessages(request.systemPrompt),
            stream = stream,
            store = false,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            maxTokens = request.parameters.maxTokens,
            tools = chatTools,
            toolChoice = request.toolChoice.toChatToolChoice(),
            parallelToolCalls = chatTools?.let { false },
            webSearchOptions = request.tools.toChatCompletionsWebSearchOptions(),
        )

        return request.openAiPostJson("chat/completions", providerJson.encodeToString(body), stream)
    }

    fun parseText(body: String): String =
        providerJson.decodeFromString<ChatCompletionsResponse>(body)
            .choices
            .firstOrNull()
            ?.message
            ?.content
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()

    fun mapSse(
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
}

internal class ChatToolCallAccumulator {
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

private fun List<ProviderChatMessage>.toChatMessages(
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

private fun ProviderChatMessage.toChatMessage(): ChatCompletionMessage =
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

private fun ProviderChatMessage.toChatContent(): JsonElement =
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

private fun List<ToolDescriptor>.toChatTools(): List<ChatCompletionTool>? =
    filter { it.source != ToolSource.Official }.takeIf { it.isNotEmpty() }?.map { tool ->
        ChatCompletionTool(
            function = ChatCompletionFunction(
                name = tool.name,
                description = tool.description,
                parameters = tool.inputSchemaJson.toJsonElementOrNull(),
                strict = false,
            ),
        )
    }

private fun List<ToolDescriptor>.toChatCompletionsWebSearchOptions(): ChatCompletionWebSearchOptions? =
    firstOrNull { it.source == ToolSource.Official && it.name == "web_search" }
        ?.let { ChatCompletionWebSearchOptions() }

private fun ToolChoice.toChatToolChoice(): JsonElement? =
    when (this) {
        ToolChoice.Auto -> null
        ToolChoice.None -> JsonPrimitive("none")
        is ToolChoice.Named -> providerJson.parseToJsonElement("""{"type":"function","function":{"name":"$name"}}""")
    }

private fun String.toJsonElementOrNull(): JsonElement? =
    runCatching { providerJson.parseToJsonElement(this) }.getOrNull()
