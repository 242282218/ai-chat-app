package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationCompactorTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun existingSummaryReplacesEarlierMessagesInProviderContext() = runTest {
        val repository = CompactingConversationRepository()
        val chatProvider = SummaryChatProvider("unused")
        val compactor = ConversationCompactor(repository, clock)
        val conversation = conversation()
        val messages = listOf(
            message("old", "old content", createdAtOffset = 1),
            message(
                id = "summary",
                content = "saved summary",
                role = MessageRole.System,
                status = MessageStatus.Compressed,
                createdAtOffset = 2,
            ),
            message("recent", "recent content", createdAtOffset = 3),
        )

        val context = compactor.build(
            conversation = conversation,
            provider = provider(maxContextTokens = 1_000),
            apiKey = "key",
            model = "model-a",
            messages = messages,
            chatProvider = chatProvider,
        )

        assertEquals(0, chatProvider.requests.size)
        assertTrue(context.systemPrompt.orEmpty().contains("saved summary"))
        assertEquals(listOf("recent content"), context.history.map { it.content })
    }

    @Test
    fun overLimitCreatesSummaryAndKeepsRecentMessages() = runTest {
        val repository = CompactingConversationRepository()
        val chatProvider = SummaryChatProvider("compressed facts")
        val compactor = ConversationCompactor(repository, clock)
        val conversation = conversation()
        val messages = (1..14).map { index ->
            message(
                id = "message-$index",
                content = "message $index ${"x".repeat(48)}",
                createdAtOffset = index.toLong(),
            )
        }

        val context = compactor.build(
            conversation = conversation,
            provider = provider(maxContextTokens = 90),
            apiKey = "key",
            model = "model-a",
            messages = messages,
            chatProvider = chatProvider,
        )

        val summaryMessage = repository.savedMessages.singleOrNull { it.status == MessageStatus.Compressed }
        assertNotNull(summaryMessage)
        assertEquals(1, chatProvider.requests.size)
        assertTrue(summaryMessage?.content.orEmpty().contains("compressed facts"))
        assertTrue(context.systemPrompt.orEmpty().contains("compressed facts"))
        assertEquals(messages.takeLast(12).map { it.content }, context.history.map { it.content })
    }

    private fun conversation(): Conversation =
        Conversation(
            id = ConversationId("conversation-1"),
            title = "Chat",
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            defaultProviderId = ProviderId("provider-1"),
        )

    private fun provider(maxContextTokens: Int): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = "Provider",
            type = ProviderType.OpenAI,
            baseUrl = "https://example.test/v1",
            apiKeyRef = null,
            headers = emptyMap(),
            models = listOf(
                ModelConfig(
                    id = "model-a",
                    displayName = "Model A",
                    capability = ModelCapability(
                        model = "model-a",
                        text = true,
                        vision = false,
                        imageGeneration = false,
                        maxContextTokens = maxContextTokens,
                    ),
                ),
            ),
            defaultModel = "model-a",
            enabled = true,
        )

    private fun message(
        id: String,
        content: String,
        role: MessageRole = MessageRole.User,
        status: MessageStatus = MessageStatus.Completed,
        createdAtOffset: Long,
    ): Message =
        Message(
            id = MessageId(id),
            conversationId = ConversationId("conversation-1"),
            role = role,
            content = content,
            contentParts = listOf(MessagePart.Text(content)),
            providerId = ProviderId("provider-1"),
            model = "model-a",
            status = status,
            errorSummary = null,
            createdAt = clock.instant().plusMillis(createdAtOffset),
            updatedAt = clock.instant().plusMillis(createdAtOffset),
            parentMessageId = null,
        )

}

private class SummaryChatProvider(private val summary: String) : ChatProvider {
    val requests = mutableListOf<ChatProviderRequest>()

    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse {
        requests += request
        return ProviderTextResponse(summary)
    }

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> =
        flowOf(ProviderStreamEvent.Completed)
}

private class CompactingConversationRepository : ConversationRepository {
    val savedMessages = mutableListOf<Message>()
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messages = MutableStateFlow<List<Message>>(emptyList())

    override fun observeConversations(): Flow<List<Conversation>> = conversations

    override suspend fun getConversation(id: ConversationId): Conversation? =
        conversations.value.firstOrNull { it.id == id }

    override suspend fun saveConversation(conversation: Conversation) {
        conversations.value = conversations.value.filterNot { it.id == conversation.id } + conversation
    }

    override suspend fun renameConversation(id: ConversationId, title: String) = Unit

    override suspend fun deleteConversation(id: ConversationId) = Unit

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> = messages

    override suspend fun getMessages(conversationId: ConversationId): List<Message> = messages.value

    override suspend fun saveMessage(message: Message) {
        savedMessages += message
        messages.value = messages.value.filterNot { it.id == message.id } + message
    }

    override suspend fun deleteMessage(messageId: com.aichat.workbench.domain.model.MessageId) = Unit

    override fun observeConversationsWithPreview(): kotlinx.coroutines.flow.Flow<List<com.aichat.workbench.domain.model.ConversationPreview>> = kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun deleteMessages(conversationId: ConversationId) {
        messages.value = emptyList()
    }
}
