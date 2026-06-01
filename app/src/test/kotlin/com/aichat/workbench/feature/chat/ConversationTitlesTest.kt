package com.aichat.workbench.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTitlesTest {
    @Test
    fun previewCollapsesWhitespace() {
        assertEquals(
            "Summarize this release plan",
            conversationTitlePreview("  Summarize\nthis   release plan  "),
        )
    }

    @Test
    fun previewMarksLongTitlesAsTruncated() {
        val preview = conversationTitlePreview(
            "Create a detailed launch plan for the enterprise onboarding workflow",
        )

        assertEquals(40, preview.length)
        assertTrue(preview.endsWith("..."))
    }

    @Test
    fun previewFallsBackForBlankText() {
        assertEquals("新对话", conversationTitlePreview("   "))
    }
}
