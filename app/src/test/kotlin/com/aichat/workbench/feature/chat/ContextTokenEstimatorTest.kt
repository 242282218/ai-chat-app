package com.aichat.workbench.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ContextTokenEstimatorTest {
    private val estimator = ContextTokenEstimator()

    @Test
    fun estimateTextUsesEnglishFourCharsPerToken() {
        assertEquals(2, estimator.estimateText("abcdefgh"))
    }

    @Test
    fun estimateTextUsesChineseTwoCharsPerToken() {
        assertEquals(2, estimator.estimateText("你好世界"))
    }
}
