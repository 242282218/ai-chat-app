package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.ModelParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelParameterDraftsTest {
    @Test
    fun blankDraftsUseProviderDefaults() {
        val parameters = ChatUiState().validatedModelParameters()

        assertEquals(ModelParameters(), parameters)
        assertTrue(ChatUiState().hasValidModelParameterDrafts())
    }

    @Test
    fun parsesValidDrafts() {
        val state = ChatUiState(
            draft = DraftState(
                temperature = "0.7",
                topP = "0.9",
                maxTokens = "512",
            ),
        )

        assertEquals(
            ModelParameters(temperature = 0.7, topP = 0.9, maxTokens = 512),
            state.validatedModelParameters(),
        )
        assertTrue(state.hasValidModelParameterDrafts())
    }

    @Test
    fun rejectsOutOfRangeDrafts() {
        assertValidationMessage(
            state = ChatUiState(draft = DraftState(temperature = "2.1")),
            message = "temperature 必须在 0 到 2 之间。",
        )
        assertValidationMessage(
            state = ChatUiState(draft = DraftState(topP = "-0.1")),
            message = "top_p 必须在 0 到 1 之间。",
        )
        assertValidationMessage(
            state = ChatUiState(draft = DraftState(maxTokens = "0")),
            message = "max_tokens 必须大于 0。",
        )
    }

    @Test
    fun reportsActionableSummaryLabels() {
        assertEquals(
            ModelParameterDraftStatus(label = "温度 0-2", isValid = false),
            modelParameterDraftStatus("3", ModelParameterDraftKind.Temperature),
        )
        assertEquals(
            ModelParameterDraftStatus(label = "采样阈值 需为数字", isValid = false),
            modelParameterDraftStatus("many", ModelParameterDraftKind.TopP),
        )
        assertEquals(
            ModelParameterDraftStatus(label = "最大输出 > 0", isValid = false),
            modelParameterDraftStatus("-1", ModelParameterDraftKind.MaxTokens),
        )
        assertFalse(ChatUiState(draft = DraftState(temperature = "3")).hasValidModelParameterDrafts())
    }

    private fun assertValidationMessage(
        state: ChatUiState,
        message: String,
    ) {
        val error = runCatching { state.validatedModelParameters() }.exceptionOrNull()

        assertEquals(message, error?.message)
        assertFalse(state.hasValidModelParameterDrafts())
    }
}
