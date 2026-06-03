package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.PromptPresetRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.provider.preferredModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun CoroutineScope.observeChatStateSources(
    conversationRepository: ConversationRepository,
    providerRepository: ProviderConfigRepository,
    promptPresetRepository: PromptPresetRepository,
    conversationManager: ConversationManager,
    providerRegistry: ProviderRegistry,
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
                val chatProviders = providers.filter { providerRegistry.isRegistered(it.type) }
                val selected = current.selectedProviderId
                    ?.let { id -> chatProviders.firstOrNull { it.id.value == id && it.enabled } }
                val fallback = selected ?: chatProviders.firstOrNull { it.enabled }
                val selectedProviderChanged = current.selectedProviderId != fallback?.id?.value
                current.copy(
                    providers = chatProviders,
                    selectedProviderId = fallback?.id?.value,
                    draft = current.draft.copy(
                        model = when {
                            fallback == null -> current.modelDraft
                            selectedProviderChanged -> fallback.preferredModel()
                            else -> current.modelDraft.ifBlank { fallback.preferredModel() }
                        },
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
