package com.aichat.workbench.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.aichat.workbench.domain.model.MessageId

data class DraftState(
    val title: String = "",
    val systemPrompt: String = "",
    val model: String = "",
    val temperature: String = "",
    val topP: String = "",
    val maxTokens: String = "",
    val temporary: Boolean = false,
    val sensitive: Boolean = false,
    val input: String = "",
    val editingMessageId: MessageId? = null,
) {
    fun toSavedState(handle: SavedStateHandle) {
        handle[KEY_TITLE] = title
        handle[KEY_SYSTEM_PROMPT] = systemPrompt
        handle[KEY_MODEL] = model
        handle[KEY_TEMPERATURE] = temperature
        handle[KEY_TOP_P] = topP
        handle[KEY_MAX_TOKENS] = maxTokens
        handle[KEY_TEMPORARY] = temporary
        handle[KEY_SENSITIVE] = sensitive
        handle[KEY_INPUT] = input
        handle[KEY_EDITING_MESSAGE_ID] = editingMessageId?.value
    }

    companion object {
        fun fromSavedState(handle: SavedStateHandle): DraftState =
            DraftState(
                title = handle[KEY_TITLE] ?: "",
                systemPrompt = handle[KEY_SYSTEM_PROMPT] ?: "",
                model = handle[KEY_MODEL] ?: "",
                temperature = handle[KEY_TEMPERATURE] ?: "",
                topP = handle[KEY_TOP_P] ?: "",
                maxTokens = handle[KEY_MAX_TOKENS] ?: "",
                temporary = handle[KEY_TEMPORARY] ?: false,
                sensitive = handle[KEY_SENSITIVE] ?: false,
                input = handle[KEY_INPUT] ?: "",
                editingMessageId = handle.get<String>(KEY_EDITING_MESSAGE_ID)?.let(::MessageId),
            )
    }
}

private const val KEY_TITLE = "chat.draft.title"
private const val KEY_SYSTEM_PROMPT = "chat.draft.systemPrompt"
private const val KEY_MODEL = "chat.draft.model"
private const val KEY_TEMPERATURE = "chat.draft.temperature"
private const val KEY_TOP_P = "chat.draft.topP"
private const val KEY_MAX_TOKENS = "chat.draft.maxTokens"
private const val KEY_TEMPORARY = "chat.draft.temporary"
private const val KEY_SENSITIVE = "chat.draft.sensitive"
private const val KEY_INPUT = "chat.draft.input"
private const val KEY_EDITING_MESSAGE_ID = "chat.draft.editingMessageId"
