package com.aichat.workbench.feature.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ConversationPreview
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.supportsTextGeneration
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

data class ConversationsUiState(
    val recentConversations: List<ConversationPreview> = emptyList(),
    val hasAvailableChatProvider: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class ConversationsViewModel(
    private val conversationRepository: ConversationRepository,
    providerRepository: ProviderConfigRepository,
    providerRegistry: ProviderRegistry,
) : ViewModel() {
    val state: StateFlow<ConversationsUiState> =
        combine(
            conversationRepository.observeConversationsWithPreview(),
            providerRepository.observeProviders(),
        ) { conversations, providers ->
            ConversationsUiState(
                recentConversations = conversations,
                hasAvailableChatProvider = providers.any {
                    it.enabled &&
                        providerRegistry.isRegistered(it.type) &&
                        it.supportsTextGeneration()
                },
                isLoading = false,
                error = null,
            )
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ConversationsUiState(),
            )

    fun deleteConversation(id: ConversationId) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
        }
    }
}
