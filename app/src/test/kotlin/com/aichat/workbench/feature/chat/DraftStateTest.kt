package com.aichat.workbench.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.aichat.workbench.domain.model.MessageId
import org.junit.Assert.assertEquals
import org.junit.Test

class DraftStateTest {
    @Test
    fun roundTripsThroughSavedStateHandle() {
        val handle = SavedStateHandle()
        val draft = DraftState(
            title = "Plan",
            input = "Draft text",
            editingMessageId = MessageId("message-1"),
        )

        draft.toSavedState(handle)

        assertEquals(draft, DraftState.fromSavedState(handle))
    }
}
