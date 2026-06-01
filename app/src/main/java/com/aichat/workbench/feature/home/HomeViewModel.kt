package com.aichat.workbench.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.MessageSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val searchQuery: String = "",
    val searchResults: List<MessageSearchResult> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val conversationRepositoryProvider: () -> ConversationRepository,
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")

    val state: StateFlow<HomeUiState> =
        searchQuery.flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(HomeUiState(searchQuery = query))
            } else {
                conversationRepositoryProvider().searchMessages(query.trim()).map { results ->
                    HomeUiState(searchQuery = query, searchResults = results)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun updateSearchQuery(value: String) {
        searchQuery.value = value
    }
}
