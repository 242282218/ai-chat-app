package com.aichat.workbench.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.aichat.workbench.domain.model.MessageId

data class DraftState(
    val title: String = "",
    val input: String = "",
    val editingMessageId: MessageId? = null,
) {
    fun toSavedState(handle: SavedStateHandle) {
        handle[KEY_TITLE] = title
        handle[KEY_INPUT] = input
        handle[KEY_EDITING_MESSAGE_ID] = editingMessageId?.value
    }

    companion object {
        fun fromSavedState(handle: SavedStateHandle): DraftState =
            DraftState(
                title = handle[KEY_TITLE] ?: "",
                input = handle[KEY_INPUT] ?: "",
                editingMessageId = handle.get<String>(KEY_EDITING_MESSAGE_ID)?.let(::MessageId),
            )
    }
}

private const val KEY_TITLE = "chat.draft.title"
private const val KEY_INPUT = "chat.draft.input"
private const val KEY_EDITING_MESSAGE_ID = "chat.draft.editingMessageId"
