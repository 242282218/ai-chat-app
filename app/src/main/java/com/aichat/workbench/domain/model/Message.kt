package com.aichat.workbench.domain.model

import java.time.Instant

data class Message(
    val id: MessageId,
    val conversationId: ConversationId,
    val role: MessageRole,
    val content: String,
    val contentParts: List<MessagePart>,
    val providerId: ProviderId?,
    val model: String?,
    val status: MessageStatus,
    val errorSummary: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val toolCallId: ToolCallId?,
    val parentMessageId: MessageId?,
)

enum class MessageRole {
    System,
    User,
    Assistant,
    Tool,
}

enum class MessageStatus {
    Draft,
    Pending,
    Streaming,
    Completed,
    Failed,
    Cancelled,
}

sealed interface MessagePart {
    data class Text(val text: String) : MessagePart
    data class Image(val uri: String, val mimeType: String?) : MessagePart
}
