package com.aichat.workbench.feature.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.repository.ConversationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ConversationsUiState(
    val recentConversations: List<Conversation> = emptyList(),
)

class ConversationsViewModel(
    conversationRepository: ConversationRepository,
) : ViewModel() {
    val state: StateFlow<ConversationsUiState> =
        conversationRepository
            .observeConversations()
            .map { conversations -> ConversationsUiState(conversations.take(30)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ConversationsUiState(),
            )
}
