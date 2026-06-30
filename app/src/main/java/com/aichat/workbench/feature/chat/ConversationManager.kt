package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.usecase.CreateConversationUseCase
import java.time.Clock

class ConversationManager(
    private val conversationRepository: ConversationRepository,
    private val clock: Clock,
) {
    suspend fun createConversation(current: ChatUiState, title: String): Conversation =
        CreateConversationUseCase(conversationRepository, clock)(
            title = title,
            defaultProviderId = current.selectedProviderId?.let(::ProviderId),
        )

    fun withSelectedConversation(
        state: ChatUiState,
        conversations: List<Conversation>,
        conversation: Conversation,
    ): ChatUiState {
        val selectionChanged = state.selectedConversationId != conversation.id
        val selectedProviderId = conversation.defaultProviderId
            ?.takeIf { providerId -> state.providers.any { it.id == providerId && it.enabled } }
            ?.value
            ?: state.selectedProviderId
        return state.copy(
            conversations = conversations,
            selectedConversationId = conversation.id,
            shouldAutoSelectConversation = true,
            selectedProviderId = selectedProviderId,
            messages = if (selectionChanged) emptyList() else state.messages,
            selectedConversationMessageCount = if (selectionChanged) 0 else state.selectedConversationMessageCount,
            draft = DraftState(title = conversation.title),
            error = null,
        )
    }

    fun clearSelection(state: ChatUiState, shouldAutoSelectConversation: Boolean = true): ChatUiState =
        state.copy(
            selectedConversationId = null,
            shouldAutoSelectConversation = shouldAutoSelectConversation,
            messages = emptyList(),
            selectedConversationMessageCount = 0,
            draft = DraftState(),
        )
}
