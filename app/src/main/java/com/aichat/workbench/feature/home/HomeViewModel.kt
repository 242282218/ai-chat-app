package com.aichat.workbench.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.MessageSearchResult
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val taskDraft: String = "",
    val recentConversations: List<Conversation> = emptyList(),
    val providers: List<ProviderConfig> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<MessageSearchResult> = emptyList(),
) {
    val enabledProviderCount: Int get() = providers.count { it.enabled }
    val hasEnabledProvider: Boolean get() = enabledProviderCount > 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val conversationRepositoryProvider: () -> ConversationRepository,
    private val providerRepository: ProviderConfigRepository,
) : ViewModel() {
    private val taskDraft = MutableStateFlow("")
    private val searchQuery = MutableStateFlow("")

    val state: StateFlow<HomeUiState> =
        combine(
            taskDraft,
            conversationRepositoryProvider()
                .observeConversations(includeArchived = false)
                .map { conversations -> conversations.take(3) },
            providerRepository.observeProviders(),
            searchResults(),
        ) { draft, conversations, providers, search ->
            search.copy(
                taskDraft = draft,
                recentConversations = conversations,
                providers = providers,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun updateTaskDraft(value: String) {
        taskDraft.value = value
    }

    fun consumeTaskDraft(): String =
        taskDraft.value.trim().also {
            if (it.isNotBlank()) taskDraft.value = ""
        }

    fun updateSearchQuery(value: String) {
        searchQuery.value = value
    }

    private fun searchResults(): Flow<HomeUiState> =
        searchQuery.flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(HomeUiState(searchQuery = query))
            } else {
                conversationRepositoryProvider().searchMessages(query.trim()).map { results ->
                    HomeUiState(searchQuery = query, searchResults = results)
                }
            }
        }
}
