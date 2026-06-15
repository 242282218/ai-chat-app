package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message

/**
 * Simplified business state for ChatScreen.
 * This separates business logic state from UI control state.
 *
 * Part of Phase 3 refactoring: State Management Simplification
 */
data class ChatBusinessState(
    val conversation: Conversation?,
    val messages: List<Message>,
    val searchQuery: String,
    val isGenerating: Boolean,
    val error: String?,
    val hasAvailableProvider: Boolean
) {
    companion object {
        fun empty() = ChatBusinessState(
            conversation = null,
            messages = emptyList(),
            searchQuery = "",
            isGenerating = false,
            error = null,
            hasAvailableProvider = false
        )
    }
}

/**
 * UI control state for dialogs and transient UI elements.
 * This is managed locally in the Composable using remember/rememberSaveable.
 *
 * Part of Phase 3 refactoring: State Management Simplification
 */
data class ChatUiControlState(
    val showDeleteDialog: Boolean = false,
    val showClearDialog: Boolean = false,
    val showImageConfirm: Boolean = false,
    val showControls: Boolean = false,
    val pendingDeleteMessageId: String? = null
)

/**
 * Helper to convert existing ChatUiState to simplified ChatBusinessState.
 * This is for gradual migration.
 */
fun ChatUiState.toBusinessState(): ChatBusinessState {
    return ChatBusinessState(
        conversation = conversations.firstOrNull { it.id == selectedConversationId },
        messages = messages,
        searchQuery = searchQuery,
        isGenerating = isGenerating,
        error = error,
        hasAvailableProvider = providers.any { it.enabled }
    )
}
