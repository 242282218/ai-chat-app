package com.aichat.workbench.provider.openai

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderErrorBody
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ResponsesMessage
import com.aichat.workbench.provider.api.ResponsesOutputItem
import com.aichat.workbench.provider.api.ResponsesRequest
import com.aichat.workbench.provider.api.ResponsesResponse
import com.aichat.workbench.provider.api.ResponsesSseEvent
import com.aichat.workbench.provider.api.providerJson
import com.aichat.workbench.provider.api.toProviderError
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.Request

internal object OpenAiResponsesProtocol {
    fun buildRequest(
        request: ChatProviderRequest,
        stream: Boolean,
    ): Request {
        val body = ResponsesRequest(
            model = request.model,
            input = request.messages.toResponsesInput(),
            stream = stream,
            store = false,
            instructions = request.systemPrompt?.takeIf { it.isNotBlank() },
            temperature = request.parameters.temperature,
            topP = request.parameters.topP,
            maxOutputTokens = request.parameters.maxTokens,
        )

        return request.openAiPostJson("responses", providerJson.encodeToString(body), stream)
    }

    fun parseText(body: String): String {
        val response = providerJson.decodeFromString<ResponsesResponse>(body)
        response.outputText?.takeIf { it.isNotBlank() }?.let { return it }
        return response.output
            .flatMap { item -> item.toTextParts() }
            .joinToString(separator = "")
    }

    fun mapSse(data: String): ProviderStreamEvent? {
        if (data == "[DONE]") return ProviderStreamEvent.Completed
        val event = providerJson.decodeFromString<ResponsesSseEvent>(data)
        return when (event.type) {
            "response.output_text.delta" -> ProviderStreamEvent.TextDelta(event.delta.orEmpty())
            "response.output_item.done" -> event.item?.toImageDelta()
            "response.completed" -> ProviderStreamEvent.Completed
            "response.failed", "error" -> ProviderStreamEvent.Failed(event.toProviderError())
            else -> null
        }
    }
}

private fun List<ProviderChatMessage>.toResponsesInput(): List<ResponsesMessage> =
    filter { it.role != MessageRole.System }
        .map { message ->
            ResponsesMessage(
                role = message.role.toProviderRole(),
                content = message.content,
            )
        }

private fun MessageRole.toProviderRole(): String =
    when (this) {
        MessageRole.System -> "system"
        MessageRole.User -> "user"
        MessageRole.Assistant -> "assistant"
    }

private fun ResponsesOutputItem.toTextParts(): List<String> =
    when (type) {
        "message" -> content
            .filter { it.type == "output_text" }
            .map { it.text.orEmpty() }
        "image_generation_call" -> listOfNotNull(result?.takeIf { it.isNotBlank() }?.let {
            "![generated image](data:image/png;base64,$it)"
        })
        else -> emptyList()
    }

private fun ResponsesOutputItem.toImageDelta(): ProviderStreamEvent.ImageDelta? =
    result
        ?.takeIf { type == "image_generation_call" && it.isNotBlank() }
        ?.toImageDelta()

private fun String.toImageDelta(): ProviderStreamEvent.ImageDelta =
    ProviderStreamEvent.ImageDelta(MessagePart.Image("data:image/png;base64,$this", "image/png"))

private fun ResponsesSseEvent.toProviderError(): com.aichat.workbench.provider.api.ProviderError {
    val eventError = error ?: ProviderErrorBody(code = code, message = message)
    return eventError.toProviderError(statusCode = null)
}
