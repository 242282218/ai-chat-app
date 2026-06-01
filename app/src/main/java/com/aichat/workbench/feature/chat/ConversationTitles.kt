package com.aichat.workbench.feature.chat

private const val ConversationTitleMaxLength = 40

fun conversationTitlePreview(message: String): String {
    val normalized = message.replace(Regex("\\s+"), " ").trim()
    return when {
        normalized.isBlank() -> "新对话"
        normalized.length <= ConversationTitleMaxLength -> normalized
        else -> "${normalized.take(ConversationTitleMaxLength - 3)}..."
    }
}
