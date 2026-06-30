package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.provider.supportsTextGeneration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal fun CoroutineScope.observeChatStateSources(
    conversationRepository: ConversationRepository,
    providerRepository: ProviderConfigRepository,
    modelRolePreferenceRepository: ModelRolePreferenceRepository,
    conversationManager: ConversationManager,
    providerRegistry: ProviderRegistry,
    currentState: () -> ChatUiState,
    updateState: (((ChatUiState) -> ChatUiState) -> Unit),
    observeMessages: (ConversationId) -> Unit,
) {
    launch {
        conversationRepository.observeConversations().collect { conversations ->
            val current = currentState()
            val selected = conversations.firstOrNull { it.id == current.selectedConversationId }
                ?: conversations.firstOrNull().takeIf { current.shouldAutoSelectConversation }
            updateState { state ->
                when {
                    selected == null -> state.copy(
                        conversations = conversations,
                        selectedConversationId = null,
                        messages = if (state.selectedConversationId == null) state.messages else emptyList(),
                        selectedConversationMessageCount = if (state.selectedConversationId == null) {
                            state.selectedConversationMessageCount
                        } else {
                            0
                        },
                    )
                    selected.id == state.selectedConversationId -> state.copy(conversations = conversations)
                    else -> conversationManager.withSelectedConversation(state, conversations, selected)
                }
            }
            selected?.id?.let(observeMessages)
        }
    }
    launch {
        combine(
            providerRepository.observeProviders(),
            modelRolePreferenceRepository.observeAllRolePreferences(),
        ) { providers, rolePreferences ->
            providers to rolePreferences
        }.collect { (providers, rolePreferences) ->
            updateState { current ->
                val chatProviders = providers.filter {
                    providerRegistry.isRegistered(it.type) && it.supportsTextGeneration()
                }
                val selected = current.selectedProviderId
                    ?.let { id -> chatProviders.firstOrNull { it.id.value == id && it.enabled } }
                val fallback = selected ?: chatProviders.firstOrNull { it.enabled }
                current.copy(
                    providers = chatProviders,
                    modelRolePreferences = rolePreferences,
                    selectedProviderId = fallback?.id?.value,
                )
            }
        }
    }
}
