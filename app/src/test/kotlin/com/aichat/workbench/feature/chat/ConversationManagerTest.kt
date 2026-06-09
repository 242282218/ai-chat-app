package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationManagerTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun withSelectedConversationCopiesTitleIntoDraft() {
        val provider = provider("provider-1", defaultModel = "fallback-model")
        val conversation = conversation(
            defaultProviderId = provider.id,
        )
        val manager = ConversationManager(ConversationManagerRepository(clock), clock)

        val state = manager.withSelectedConversation(
            state = ChatUiState(providers = listOf(provider), selectedProviderId = provider.id.value),
            conversations = listOf(conversation),
            conversation = conversation,
        )

        assertEquals(conversation.id, state.selectedConversationId)
        assertEquals("Existing", state.titleDraft)
    }

    @Test
    fun withSelectedConversationSelectsConversationDefaultProvider() {
        val selected = provider("selected", ProviderType.OpenAI, defaultModel = "selected-model")
        val conversationProvider = provider("conversation", ProviderType.OpenAICompatible, defaultModel = "conversation-model")
        val conversation = conversation(
            defaultProviderId = conversationProvider.id,
        )
        val manager = ConversationManager(ConversationManagerRepository(clock), clock)

        val state = manager.withSelectedConversation(
            state = ChatUiState(
                providers = listOf(selected, conversationProvider),
                selectedProviderId = selected.id.value,
            ),
            conversations = listOf(conversation),
            conversation = conversation,
        )

        assertEquals(conversationProvider.id.value, state.selectedProviderId)
    }

    @Test
    fun createConversationUsesSelectedProviderAsDefault() = runTest {
        val repository = ConversationManagerRepository(clock)
        val manager = ConversationManager(repository, clock)
        val provider = provider("provider-1")

        val conversation = manager.createConversation(
            current = ChatUiState(
                providers = listOf(provider),
                selectedProviderId = provider.id.value,
            ),
            title = "New chat",
        )

        assertEquals("New chat", conversation.title)
        assertEquals(provider.id, conversation.defaultProviderId)
    }

    private fun provider(
        id: String,
        type: ProviderType = ProviderType.OpenAI,
        defaultModel: String? = "$id-model",
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId(id),
            name = id,
            type = type,
            baseUrl = "https://example.test/v1",
            apiKeyRef = null,
            headers = emptyMap(),
            models = emptyList(),
            defaultModel = defaultModel,
            enabled = true,
        )

    private fun conversation(
        defaultProviderId: ProviderId?,
    ): Conversation =
        Conversation(
            id = ConversationId("conversation-${defaultProviderId?.value ?: "none"}"),
            title = "Existing",
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            defaultProviderId = defaultProviderId,
        )

}

private class ConversationManagerRepository(
    private val clock: Clock,
) : ConversationRepository {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messages = mutableMapOf<ConversationId, MutableStateFlow<List<Message>>>()

    override fun observeConversations(): Flow<List<Conversation>> = conversations

    override suspend fun getConversation(id: ConversationId): Conversation? =
        conversations.value.firstOrNull { it.id == id }

    override suspend fun saveConversation(conversation: Conversation) {
        conversations.value = conversations.value.filterNot { it.id == conversation.id } + conversation
    }

    override suspend fun renameConversation(id: ConversationId, title: String) {
        conversations.value = conversations.value.map {
            if (it.id == id) it.copy(title = title, updatedAt = clock.instant()) else it
        }
    }

    override suspend fun deleteConversation(id: ConversationId) = Unit

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> =
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    override suspend fun getMessages(conversationId: ConversationId): List<Message> =
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value

    override suspend fun saveMessage(message: Message) {
        val flow = messages.getOrPut(message.conversationId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value.filterNot { it.id == message.id } + message
    }

    override suspend fun deleteMessages(conversationId: ConversationId) {
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value = emptyList()
    }
}
