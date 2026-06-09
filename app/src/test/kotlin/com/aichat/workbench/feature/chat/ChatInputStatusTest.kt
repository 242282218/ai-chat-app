package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.ui.component.StatusTone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputStatusTest {
    @Test
    fun inputStatusWarnsWhenImageDraftsWillBeSentToModel() {
        val status = inputStatus(
            isGenerating = false,
            isEditing = false,
            hasImageDrafts = true,
            canSend = true,
        )

        assertEquals(
            InputStatus(
                label = "图片将作为多模态内容发送给模型",
                tone = StatusTone.Warning,
            ),
            status,
        )
    }

    @Test
    fun inputStatusKeepsHigherPriorityStatesBeforeImageWarning() {
        assertEquals(
            "生成中",
            inputStatus(
                isGenerating = true,
                isEditing = false,
                hasImageDrafts = true,
                canSend = true,
            )?.label,
        )
        assertEquals(
            "编辑中",
            inputStatus(
                isGenerating = false,
                isEditing = true,
                hasImageDrafts = true,
                canSend = true,
            )?.label,
        )
    }

    @Test
    fun inputStatusRequiresProviderBeforeImageWarning() {
        val status = inputStatus(
            isGenerating = false,
            isEditing = false,
            hasImageDrafts = true,
            canSend = false,
        )

        assertEquals(
            InputStatus(
                label = "需要模型连接",
                tone = StatusTone.Critical,
            ),
            status,
        )
    }

    @Test
    fun inputStatusIsEmptyWhenReadyWithoutImageDrafts() {
        assertNull(
            inputStatus(
                isGenerating = false,
                isEditing = false,
                hasImageDrafts = false,
                canSend = true,
            ),
        )
    }

    @Test
    fun shouldConfirmImageSendOnlyWhenImageDraftsExist() {
        assertFalse(shouldConfirmImageSend(emptyList()))
        assertTrue(
            shouldConfirmImageSend(
                listOf(MessagePart.Image("data:image/jpeg;base64,abc", "image/jpeg")),
            ),
        )
    }
}
