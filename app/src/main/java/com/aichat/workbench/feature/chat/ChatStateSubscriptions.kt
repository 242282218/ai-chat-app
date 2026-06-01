package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.PromptPresetRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun CoroutineScope.observeChatStateSources(
    conversationRepository: ConversationRepository,
    providerRepository: ProviderConfigRepository,
    promptPresetRepository: PromptPresetRepository,
    conversationManager: ConversationManager,
    currentState: () -> ChatUiState,
    updateState: (((ChatUiState) -> ChatUiState) -> Unit),
    observeMessages: (ConversationId) -> Unit,
) {
    launch {
        conversationRepository.observeConversations().collect { conversations ->
            val selected = conversations.firstOrNull { it.id == currentState().selectedConversationId }
                ?: conversations.firstOrNull()
            updateState { state ->
                if (selected == null || selected.id == state.selectedConversationId) {
                    state.copy(conversations = conversations, selectedConversationId = selected?.id)
                } else {
                    conversationManager.withSelectedConversation(state, conversations, selected)
                }
            }
            selected?.id?.let(observeMessages)
        }
    }
    launch {
        providerRepository.observeProviders().collect { providers ->
            updateState { current ->
                val selected = current.selectedProviderId
                    ?.let { id -> providers.firstOrNull { it.id.value == id && it.enabled } }
                val fallback = selected ?: providers.firstOrNull { it.enabled }
                current.copy(
                    providers = providers,
                    selectedProviderId = fallback?.id?.value,
                    draft = current.draft.copy(
                        model = current.modelDraft.ifBlank { fallback?.defaultModel.orEmpty() },
                    ),
                )
            }
        }
    }
    launch {
        promptPresetRepository.observePromptPresets().collect { presets ->
            updateState { it.copy(promptPresets = presets) }
        }
    }
}
