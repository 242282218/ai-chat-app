package com.aichat.workbench.feature.conversations

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ConversationPreview
import com.aichat.workbench.domain.model.MessageRole
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationPreviewFormattingTest {
    @Test
    fun lastMessagePreviewAddsRolePrefixForStoredEnumNames() {
        assertEquals(
            "你: hello",
            preview(role = MessageRole.User.name, content = "hello").lastMessagePreview(),
        )
        assertEquals(
            "AI: answer",
            preview(role = MessageRole.Assistant.name, content = "answer").lastMessagePreview(),
        )
    }

    @Test
    fun lastMessagePreviewHandlesLegacyLowercaseRoles() {
        assertEquals("你: hello", preview(role = "user", content = "hello").lastMessagePreview())
        assertEquals("AI: answer", preview(role = "assistant", content = "answer").lastMessagePreview())
    }

    @Test
    fun lastMessagePreviewCompactsContentAndSkipsBlankContent() {
        assertEquals("AI: line 1 line 2", preview(role = "Assistant", content = "line 1\nline 2").lastMessagePreview())
        assertNull(preview(role = "Assistant", content = "   ").lastMessagePreview())
    }

    private fun preview(role: String?, content: String?): ConversationPreview =
        ConversationPreview(
            id = ConversationId("conversation"),
            title = "Conversation",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            defaultProviderId = null,
            lastMessageContent = content,
            lastMessageRole = role,
        )
}
