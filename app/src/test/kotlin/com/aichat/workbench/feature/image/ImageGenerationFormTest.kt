package com.aichat.workbench.feature.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGenerationFormTest {
    @Test
    fun imageGenerationErrorNoticeTextAddsFailurePrefixForCauseOnlyMessage() {
        assertEquals(
            "图片生成失败：API Key 缺失。",
            imageGenerationErrorNoticeText("API Key 缺失。"),
        )
    }

    @Test
    fun imageGenerationErrorNoticeTextDoesNotDuplicateFailurePrefix() {
        assertEquals(
            "图片生成失败。",
            imageGenerationErrorNoticeText("图片生成失败。"),
        )
        assertEquals(
            "图片生成失败，请重试。",
            imageGenerationErrorNoticeText(" 图片生成失败，请重试。 "),
        )
    }
}
