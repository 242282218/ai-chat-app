package com.aichat.workbench.provider.compatible

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.provider.api.ChatCompletionMessage
import com.aichat.workbench.provider.openai.toOpenAiRole
import com.aichat.workbench.provider.api.ChatCompletionSseEvent
import com.aichat.workbench.provider.api.ChatCompletionsRequest
import com.aichat.workbench.provider.api.ChatCompletionsResponse
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderErrorEnvelope
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.providerJson
import com.aichat.workbench.provider.api.toProviderError
import com.aichat.workbench.provider.openai.openAiPostJson
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
        val body = ChatCompletionsRequest(
            model = request.model,
            messages = request.messages.toChatMessages(request.systemPrompt),
            stream = stream,
            store = false,
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            maxTokens = request.parameters.maxTokens,
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

    fun mapSse(data: String): List<ProviderStreamEvent> {
        if (data == "[DONE]") return listOf(ProviderStreamEvent.Completed)
        providerJson.decodeFromString<ProviderErrorEnvelope>(data).error?.let { error ->
            return listOf(ProviderStreamEvent.Failed(error.toProviderError()))
        }
        val choice = providerJson.decodeFromString<ChatCompletionSseEvent>(data).choices.firstOrNull()
            ?: return emptyList()
        val content = choice.delta?.content?.jsonPrimitive?.contentOrNull.orEmpty()
        return when {
            content.isNotEmpty() -> listOf(ProviderStreamEvent.TextDelta(content))
            choice.finishReason != null -> listOf(ProviderStreamEvent.Completed)
            else -> emptyList()
        }
    }
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
            role = role.toOpenAiRole(),
            content = content.takeIf { it.isNotBlank() }?.let(::JsonPrimitive),
        )
        else -> ChatCompletionMessage(
            role = role.toOpenAiRole(),
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
