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
            temperatureDraft = "0.7",
            topPDraft = "0.9",
            maxTokensDraft = "512",
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
            state = ChatUiState(temperatureDraft = "2.1"),
            message = "temperature must be between 0 and 2.",
        )
        assertValidationMessage(
            state = ChatUiState(topPDraft = "-0.1"),
            message = "top_p must be between 0 and 1.",
        )
        assertValidationMessage(
            state = ChatUiState(maxTokensDraft = "0"),
            message = "max_tokens must be greater than 0.",
        )
    }

    @Test
    fun reportsActionableSummaryLabels() {
        assertEquals(
            ModelParameterDraftStatus(label = "Temp 0-2", isValid = false),
            modelParameterDraftStatus("3", ModelParameterDraftKind.Temperature),
        )
        assertEquals(
            ModelParameterDraftStatus(label = "Top P number", isValid = false),
            modelParameterDraftStatus("many", ModelParameterDraftKind.TopP),
        )
        assertEquals(
            ModelParameterDraftStatus(label = "Max > 0", isValid = false),
            modelParameterDraftStatus("-1", ModelParameterDraftKind.MaxTokens),
        )
        assertFalse(ChatUiState(temperatureDraft = "3").hasValidModelParameterDrafts())
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
