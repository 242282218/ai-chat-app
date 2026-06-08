package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.MemoryItem
import com.aichat.workbench.domain.model.MemoryItemId
import com.aichat.workbench.domain.model.MemoryKind
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.MemoryRepository
import com.aichat.workbench.domain.repository.MessageSearchResult
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
        val conversation = conversation(systemPrompt = "Base system")
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

        val context = compactor.compactIfNeeded(
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
        val conversation = conversation(systemPrompt = "Base system")
        val messages = (1..14).map { index ->
            message(
                id = "message-$index",
                content = "message $index ${"x".repeat(48)}",
                createdAtOffset = index.toLong(),
            )
        }

        val context = compactor.compactIfNeeded(
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

    @Test
    fun cancelledAndDeniedToolMessagesStayOutOfProviderContext() = runTest {
        val repository = CompactingConversationRepository()
        val chatProvider = SummaryChatProvider("unused")
        val compactor = ConversationCompactor(repository, clock)
        val conversation = conversation(systemPrompt = "Base system")
        val completedTool = message(
            id = "tool-completed",
            content = """{"ok":true}""",
            role = MessageRole.Tool,
            status = MessageStatus.Completed,
            createdAtOffset = 1,
        )
        val failedTool = message(
            id = "tool-failed",
            content = """{"code":"local_search_http_429","message":"Rate limit exceeded"}""",
            role = MessageRole.Tool,
            status = MessageStatus.Failed,
            createdAtOffset = 2,
        )
        val cancelledTool = message(
            id = "tool-cancelled",
            content = """{"code":"tool_cancelled","message":"工具执行已取消。"}""",
            role = MessageRole.Tool,
            status = MessageStatus.Cancelled,
            createdAtOffset = 3,
        )
        val deniedTool = message(
            id = "tool-denied",
            content = """{"code":"tool_denied","message":"用户拒绝执行工具。"}""",
            role = MessageRole.Tool,
            status = MessageStatus.Cancelled,
            createdAtOffset = 4,
        )

        val context = compactor.compactIfNeeded(
            conversation = conversation,
            provider = provider(maxContextTokens = 1_000),
            apiKey = "key",
            model = "model-a",
            messages = listOf(completedTool, failedTool, cancelledTool, deniedTool),
            chatProvider = chatProvider,
        )

        assertEquals(0, chatProvider.requests.size)
        assertEquals(2, context.history.size)
        assertTrue(context.history[0].content.contains("工具结果摘要"))
        assertTrue(context.history[0].content.contains("status: Completed"))
        assertTrue(context.history[0].content.contains(completedTool.content))
        assertTrue(context.history[1].content.contains("status: Failed"))
        assertTrue(context.history[1].content.contains(failedTool.content))
        assertTrue(context.history.none { it.content.contains(cancelledTool.content) })
        assertTrue(context.history.none { it.content.contains(deniedTool.content) })
    }

    @Test
    fun relevantMemoriesAreInjectedIntoSystemPrompt() = runTest {
        val repository = CompactingConversationRepository()
        val memoryRepository = CompactingMemoryRepository(
            listOf(
                memory("memory-1", "用户偏好 Kotlin 简洁实现。"),
                memory("memory-2", "项目约束：不要写入真实 API Key。"),
            ),
        )
        val chatProvider = SummaryChatProvider("unused")
        val compactor = ConversationCompactor(
            conversationRepository = repository,
            clock = clock,
            memoryRepository = memoryRepository,
        )
        val conversation = conversation(systemPrompt = "Base system")

        val context = compactor.compactIfNeeded(
            conversation = conversation,
            provider = provider(maxContextTokens = 1_000),
            apiKey = "key",
            model = "model-a",
            messages = listOf(message("recent", "请按项目约束实现", createdAtOffset = 1)),
            chatProvider = chatProvider,
        )

        assertEquals(0, chatProvider.requests.size)
        assertTrue(context.systemPrompt.orEmpty().contains("用户手动保存的长期记忆"))
        assertTrue(context.systemPrompt.orEmpty().contains("用户偏好 Kotlin 简洁实现"))
        assertTrue(context.systemPrompt.orEmpty().contains("不要写入真实 API Key"))
    }

    @Test
    fun longToolResultIsSummarizedAndTruncatedBeforeProviderContext() = runTest {
        val repository = CompactingConversationRepository()
        val chatProvider = SummaryChatProvider("unused")
        val compactor = ConversationCompactor(repository, clock)
        val longResult = "result-" + "x".repeat(2_000)

        val context = compactor.compactIfNeeded(
            conversation = conversation(systemPrompt = "Base system"),
            provider = provider(maxContextTokens = 1_000),
            apiKey = "key",
            model = "model-a",
            messages = listOf(
                message(
                    id = "tool-long",
                    content = longResult,
                    role = MessageRole.Tool,
                    status = MessageStatus.Completed,
                    createdAtOffset = 1,
                ),
            ),
            chatProvider = chatProvider,
        )

        val content = context.history.single().content
        assertTrue(content.startsWith("工具结果摘要"))
        assertTrue(content.contains("status: Completed"))
        assertTrue(content.contains("...[truncated]"))
        assertTrue(content.length < longResult.length)
    }

    private fun conversation(systemPrompt: String?): Conversation =
        Conversation(
            id = ConversationId("conversation-1"),
            title = "Chat",
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            defaultProviderId = ProviderId("provider-1"),
            defaultModel = "model-a",
            modelParameters = ModelParameters(),
            systemPrompt = systemPrompt,
            isTemporary = false,
            isSensitive = false,
            archivedAt = null,
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
                        toolCalling = false,
                        structuredOutput = false,
                        longContext = true,
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
            toolCallId = null,
            parentMessageId = null,
        )

    private fun memory(id: String, content: String): MemoryItem =
        MemoryItem(
            id = MemoryItemId(id),
            kind = MemoryKind.UserFact,
            content = content,
            sourceConversationId = ConversationId("conversation-1"),
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
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

    override fun observeConversations(includeArchived: Boolean): Flow<List<Conversation>> = conversations

    override suspend fun getConversation(id: ConversationId): Conversation? =
        conversations.value.firstOrNull { it.id == id }

    override suspend fun saveConversation(conversation: Conversation) {
        conversations.value = conversations.value.filterNot { it.id == conversation.id } + conversation
    }

    override suspend fun renameConversation(id: ConversationId, title: String) = Unit

    override suspend fun archiveConversation(id: ConversationId) = Unit

    override suspend fun deleteConversation(id: ConversationId) = Unit

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> = messages

    override suspend fun getMessages(conversationId: ConversationId): List<Message> = messages.value

    override suspend fun saveMessage(message: Message) {
        savedMessages += message
        messages.value = messages.value.filterNot { it.id == message.id } + message
    }

    override suspend fun deleteMessages(conversationId: ConversationId) {
        messages.value = emptyList()
    }

    override fun searchMessages(query: String, limit: Int): Flow<List<MessageSearchResult>> =
        flowOf(emptyList())
}

private class CompactingMemoryRepository(
    private val memories: List<MemoryItem>,
) : MemoryRepository {
    override fun observeMemories(): Flow<List<MemoryItem>> = flowOf(memories)

    override suspend fun getMemory(id: MemoryItemId): MemoryItem? =
        memories.firstOrNull { it.id == id }

    override suspend fun saveMemory(memory: MemoryItem) = Unit

    override suspend fun deleteMemory(id: MemoryItemId) = Unit

    override suspend fun findRelevantMemories(query: String, limit: Int): List<MemoryItem> =
        memories.take(limit.coerceAtLeast(0))
}
