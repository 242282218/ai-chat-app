package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelParameters
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
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SendMessageUseCaseTest {
    @Test
    fun flushesStreamingDeltasEveryTenChunksAndFinalState() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-01T00:00:00Z"))
        val repository = CountingConversationRepository()
        val events = List(11) { ProviderStreamEvent.TextDelta("x") } + ProviderStreamEvent.Completed
        val useCase = SendMessageUseCase(repository, FlowChatProvider(flowOf(*events.toTypedArray())), clock)

        val states = useCase(assistantMessage(clock), request()).toList()

        assertEquals(13, states.size)
        assertEquals(3, repository.savedMessages.size)
        assertEquals("", repository.savedMessages[0].content)
        assertEquals(MessageStatus.Streaming, repository.savedMessages[0].status)
        assertEquals("xxxxxxxxxx", repository.savedMessages[1].content)
        assertEquals(MessageStatus.Streaming, repository.savedMessages[1].status)
        assertEquals("xxxxxxxxxxx", repository.savedMessages[2].content)
        assertEquals(MessageStatus.Completed, repository.savedMessages[2].status)
    }

    @Test
    fun flushesStreamingDeltasAfterIntervalAndFinalState() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-01T00:00:00Z"))
        val repository = CountingConversationRepository()
        val provider = FlowChatProvider(
            flow {
                emit(ProviderStreamEvent.TextDelta("a"))
                clock.advanceMillis(501)
                emit(ProviderStreamEvent.TextDelta("b"))
                emit(ProviderStreamEvent.Completed)
            },
        )
        val useCase = SendMessageUseCase(repository, provider, clock)

        useCase(assistantMessage(clock), request()).toList()

        assertEquals(3, repository.savedMessages.size)
        assertEquals("ab", repository.savedMessages[1].content)
        assertEquals(MessageStatus.Streaming, repository.savedMessages[1].status)
        assertEquals("ab", repository.savedMessages[2].content)
        assertEquals(MessageStatus.Completed, repository.savedMessages[2].status)
    }

    @Test
    fun imageDeltaAddsImageContentPartAndKeepsItAfterCompleted() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-01T00:00:00Z"))
        val repository = CountingConversationRepository()
        val image = MessagePart.Image("data:image/png;base64,abc", "image/png")
        val provider = FlowChatProvider(
            flowOf(
                ProviderStreamEvent.TextDelta("Image ready"),
                ProviderStreamEvent.ImageDelta(image),
                ProviderStreamEvent.Completed,
            ),
        )
        val useCase = SendMessageUseCase(repository, provider, clock)

        val states = useCase(assistantMessage(clock), request()).toList()

        assertEquals("Image ready", states.last().content)
        assertEquals(listOf(MessagePart.Text("Image ready"), image), states.last().contentParts)
        assertEquals(listOf(MessagePart.Text("Image ready"), image), repository.savedMessages.last().contentParts)
    }

    private fun assistantMessage(clock: Clock): Message =
        Message(
            id = MessageId("assistant-1"),
            conversationId = ConversationId("conversation-1"),
            role = MessageRole.Assistant,
            content = "",
            contentParts = emptyList(),
            providerId = ProviderId("provider-1"),
            model = "gpt-test",
            status = MessageStatus.Pending,
            errorSummary = null,
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            toolCallId = null,
            parentMessageId = null,
        )

    private fun request(): ChatProviderRequest =
        ChatProviderRequest(
            provider = ProviderConfig(
                id = ProviderId("provider-1"),
                name = "OpenAI",
                type = ProviderType.OpenAI,
                baseUrl = "https://example.test/v1",
                apiKeyRef = null,
                headers = emptyMap(),
                models = emptyList(),
                defaultModel = "gpt-test",
                enabled = true,
            ),
            apiKey = "key",
            model = "gpt-test",
            systemPrompt = null,
            messages = emptyList(),
            parameters = ModelParameters(),
        )
}

private class FlowChatProvider(
    private val events: Flow<ProviderStreamEvent>,
) : ChatProvider {
    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse =
        ProviderTextResponse("")

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> = events
}

private class CountingConversationRepository : ConversationRepository {
    val savedMessages = mutableListOf<Message>()

    override fun observeConversations(includeArchived: Boolean): Flow<List<Conversation>> = flowOf(emptyList())

    override suspend fun getConversation(id: ConversationId): Conversation? = null

    override suspend fun saveConversation(conversation: Conversation) = Unit

    override suspend fun renameConversation(id: ConversationId, title: String) = Unit

    override suspend fun archiveConversation(id: ConversationId) = Unit

    override suspend fun deleteConversation(id: ConversationId) = Unit

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> = flowOf(emptyList())

    override suspend fun getMessages(conversationId: ConversationId): List<Message> = emptyList()

    override suspend fun saveMessage(message: Message) {
        savedMessages += message
    }

    override suspend fun deleteMessages(conversationId: ConversationId) = Unit
}

private class MutableClock(
    private var current: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current

    fun advanceMillis(millis: Long) {
        current = current.plusMillis(millis)
    }
}
