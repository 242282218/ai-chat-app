package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.provider.api.ProviderChatMessage

class ContextTokenEstimator {
    fun estimateText(value: String): Int {
        if (value.isBlank()) return 0
        var cjkChars = 0
        var otherChars = 0
        value.forEach { char ->
            if (char.isWhitespace()) return@forEach
            if (char.isCjk()) {
                cjkChars += 1
            } else {
                otherChars += 1
            }
        }
        return cjkChars.ceilDiv(CHINESE_CHARS_PER_TOKEN) + otherChars.ceilDiv(ENGLISH_CHARS_PER_TOKEN)
    }

    fun estimateMessages(messages: List<ProviderChatMessage>): Int =
        messages.sumOf(::estimateMessage)

    fun estimateMessage(message: ProviderChatMessage): Int =
            ROLE_OVERHEAD_TOKENS +
            estimateText(message.role.name) +
            estimateText(message.content) +
            message.contentParts.sumOf { part -> estimatePart(part, message.content) } +
            message.toolCalls.sumOf { estimateText(it.name) + estimateText(it.arguments) } +
            estimateText(message.toolCallId?.value.orEmpty())

    private fun estimatePart(part: MessagePart, messageContent: String): Int =
        when (part) {
            is MessagePart.Text -> if (messageContent.isBlank()) estimateText(part.text) else 0
            is MessagePart.Image -> IMAGE_TOKEN_ESTIMATE
        }

    private fun Char.isCjk(): Boolean =
        code in 0x4E00..0x9FFF ||
            code in 0x3400..0x4DBF ||
            code in 0xF900..0xFAFF

    private fun Int.ceilDiv(divisor: Int): Int =
        if (this == 0) 0 else (this + divisor - 1) / divisor

    private companion object {
        const val ENGLISH_CHARS_PER_TOKEN = 4
        const val CHINESE_CHARS_PER_TOKEN = 2
        const val ROLE_OVERHEAD_TOKENS = 4
        const val IMAGE_TOKEN_ESTIMATE = 512
    }
}
