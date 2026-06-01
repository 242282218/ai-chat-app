package com.aichat.workbench.feature.home

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.MessageSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = HomeMainDispatcherRule()

    @Test
    fun doesNotCreateConversationRepositoryUntilSearchQueryIsNonBlank() = runTest(mainDispatcherRule.testDispatcher) {
        var repositoryCreations = 0
        val viewModel = HomeViewModel(
            conversationRepositoryProvider = {
                repositoryCreations += 1
                SearchOnlyConversationRepository()
            },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        advanceUntilIdle()

        assertEquals(0, repositoryCreations)

        viewModel.updateSearchQuery("needle")
        advanceUntilIdle()

        assertEquals(1, repositoryCreations)
        assertEquals("needle", viewModel.state.value.searchQuery)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeMainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class SearchOnlyConversationRepository : ConversationRepository {
    override fun observeConversations(includeArchived: Boolean): Flow<List<Conversation>> = flowOf(emptyList())

    override suspend fun getConversation(id: ConversationId): Conversation? = null

    override suspend fun saveConversation(conversation: Conversation) = Unit

    override suspend fun renameConversation(id: ConversationId, title: String) = Unit

    override suspend fun archiveConversation(id: ConversationId) = Unit

    override suspend fun deleteConversation(id: ConversationId) = Unit

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> = flowOf(emptyList())

    override suspend fun getMessages(conversationId: ConversationId): List<Message> = emptyList()

    override suspend fun saveMessage(message: Message) = Unit

    override suspend fun deleteMessages(conversationId: ConversationId) = Unit

    override fun searchMessages(query: String, limit: Int): Flow<List<MessageSearchResult>> =
        flowOf(emptyList())
}
