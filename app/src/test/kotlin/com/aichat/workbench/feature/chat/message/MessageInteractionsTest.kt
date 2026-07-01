package com.aichat.workbench.feature.chat.message

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageInteractionsTest {
    @Test
    fun copyableTextPrefersMessageContent() {
        val message = message(
            content = "Primary text",
            parts = listOf(MessagePart.Text("Part text")),
        )

        assertEquals("Primary text", message.copyableText())
    }

    @Test
    fun copyableTextFallsBackToTextParts() {
        val message = message(
            content = "",
            parts = listOf(
                MessagePart.Image("data:image/png;base64,abc", "image/png"),
                MessagePart.Text("First"),
                MessagePart.Text("Second"),
            ),
        )

        assertEquals("First\nSecond", message.copyableText())
    }

    @Test
    fun imageOnlyMessageDoesNotOfferEmptyCopyOrShareActions() {
        val actions = buildMessageActions(
            message(
                content = "",
                parts = listOf(MessagePart.Image("data:image/png;base64,abc", "image/png")),
            ),
        )

        assertFalse(actions.any { it.action is MessageAction.Copy })
        assertFalse(actions.any { it.action is MessageAction.Share })
        assertTrue(actions.any { it.action is MessageAction.Delete })
    }

    private fun message(
        content: String,
        parts: List<MessagePart> = emptyList(),
    ): Message =
        Message(
            id = MessageId("message-id"),
            conversationId = ConversationId("conversation-id"),
            role = MessageRole.Assistant,
            content = content,
            contentParts = parts,
            providerId = null,
            model = null,
            status = MessageStatus.Completed,
            errorSummary = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            parentMessageId = null,
        )
}
