package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderConfig
import kotlinx.coroutines.flow.Flow

interface ChatProvider {
    suspend fun complete(request: ChatProviderRequest): ProviderTextResponse

    fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent>
}

data class ChatProviderRequest(
    val provider: ProviderConfig,
    val apiKey: String?,
    val model: String,
    val systemPrompt: String?,
    val messages: List<ProviderChatMessage>,
    val parameters: ModelParameters = ModelParameters(),
)

data class ProviderChatMessage(
    val role: MessageRole,
    val content: String,
    val contentParts: List<MessagePart> = if (content.isBlank()) emptyList() else listOf(MessagePart.Text(content)),
)

data class ProviderTextResponse(
    val content: String,
)

sealed interface ProviderStreamEvent {
    data class TextDelta(val text: String) : ProviderStreamEvent

    data class ImageDelta(val image: MessagePart.Image) : ProviderStreamEvent

    data object Completed : ProviderStreamEvent

    data class Failed(val error: ProviderError) : ProviderStreamEvent
}

data class ProviderError(
    val code: String,
    val message: String,
    val statusCode: Int?,
    val retryable: Boolean,
)

class ProviderHttpException(
    val error: ProviderError,
) : RuntimeException(error.message)
