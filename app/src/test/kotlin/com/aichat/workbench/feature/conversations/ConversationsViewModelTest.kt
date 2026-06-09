package com.aichat.workbench.feature.conversations

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.repository.ConversationRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ConversationsViewModelTest {
    @get:Rule
    val mainDispatcherRule = ConversationsMainDispatcherRule()

    @Test
    fun exposesRecentThirtyConversations() = runTest(mainDispatcherRule.testDispatcher) {
        val conversations = (1..35).map(::testConversation)
        val viewModel = ConversationsViewModel(
            conversationRepository = ConversationsOnlyRepository(conversations),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        advanceUntilIdle()

        assertEquals(30, viewModel.state.value.recentConversations.size)
        assertEquals("Conversation 1", viewModel.state.value.recentConversations.first().title)
        assertEquals("Conversation 30", viewModel.state.value.recentConversations.last().title)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationsMainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class ConversationsOnlyRepository(
    private val conversations: List<Conversation> = emptyList(),
) : ConversationRepository {
    override fun observeConversations(): Flow<List<Conversation>> = flowOf(conversations)

    override suspend fun getConversation(id: ConversationId): Conversation? = null

    override suspend fun saveConversation(conversation: Conversation) = Unit

    override suspend fun renameConversation(id: ConversationId, title: String) = Unit

    override suspend fun deleteConversation(id: ConversationId) = Unit

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> = flowOf(emptyList())

    override suspend fun getMessages(conversationId: ConversationId): List<Message> = emptyList()

    override suspend fun saveMessage(message: Message) = Unit

    override suspend fun deleteMessages(conversationId: ConversationId) = Unit
}

private fun testConversation(index: Int): Conversation =
    Conversation(
        id = ConversationId("conversation-$index"),
        title = "Conversation $index",
        createdAt = Instant.EPOCH.plusSeconds(index.toLong()),
        updatedAt = Instant.EPOCH.plusSeconds(index.toLong()),
        defaultProviderId = null,
    )
