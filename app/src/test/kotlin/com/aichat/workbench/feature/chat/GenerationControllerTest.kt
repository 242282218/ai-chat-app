package com.aichat.workbench.feature.chat

import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
import com.aichat.workbench.tool.gateway.GatewayClient
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun startPersistsUserMessageAndCompletedAssistantMessage() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAI.value, chatProvider)
                register(ProviderType.OpenAICompatible.value, GenerationControllerChatProvider())
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Question"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Question",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertFalse(state.isGenerating)
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Question")), chatProvider.requests.single().messages)
        assertTrue(conversationRepository.allMessages().any { it.role == MessageRole.User && it.content == "Question" })
        assertTrue(
            conversationRepository.allMessages().any {
                it.role == MessageRole.Assistant &&
                    it.content == "Answer" &&
                    it.status == MessageStatus.Completed
            },
        )
    }

    @Test
    fun toolCallExecutesToolAndContinuesGeneration() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val toolCall = ToolCall(ToolCallId("call_1"), "time", "{}")
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Final answer"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAI.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock, toolRepository),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "What time is it?"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "What time is it?",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertFalse(state.isGenerating)
        assertEquals(2, chatProvider.requests.size)
        assertTrue(chatProvider.requests.first().tools.any { it.name == "time" })
        assertEquals(
            listOf(
                ProviderChatMessage(MessageRole.User, "What time is it?"),
                ProviderChatMessage(MessageRole.Assistant, "", toolCalls = listOf(toolCall)),
                ProviderChatMessage(
                    role = MessageRole.Tool,
                    content = """{"currentTime":"2026-06-01T00:00:00Z"}""",
                    toolCallId = toolCall.id,
                ),
            ),
            chatProvider.requests[1].messages,
        )
        assertEquals(1, toolRepository.savedResults.value.size)
        assertTrue(conversationRepository.allMessages().any { it.role == MessageRole.Tool && it.toolCallId == toolCall.id })
        assertTrue(conversationRepository.allMessages().any { it.content == "Final answer" && it.status == MessageStatus.Completed })
    }

    @Test
    fun failedToolCallPersistsReadableErrorSummary() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val toolRepository = GenerationControllerToolInvocationRepository()
        val toolCall = ToolCall(ToolCallId("call_unknown"), "web_search", """{"query":"AI"}""")
        val chatProvider = GenerationControllerChatProvider(
            listOf(
                flowOf(ProviderStreamEvent.ToolCallDelta(toolCall), ProviderStreamEvent.Completed),
                flowOf(ProviderStreamEvent.TextDelta("Final answer"), ProviderStreamEvent.Completed),
            ),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAI.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock, toolRepository),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "gpt-test", input = "Search the web"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Search the web",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val toolMessage = conversationRepository.allMessages().single { it.role == MessageRole.Tool }
        assertEquals(MessageStatus.Failed, toolMessage.status)
        assertEquals("未知工具。", toolMessage.errorSummary)
        assertEquals("unknown_tool", toolRepository.savedResults.value.single().error?.code)
        assertTrue(conversationRepository.allMessages().any { it.content == "Final answer" && it.status == MessageStatus.Completed })
    }

    @Test
    fun startShowsClearErrorForUnregisteredProvider() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("anthropic", ProviderType.Anthropic)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            listOf(flowOf(ProviderStreamEvent.TextDelta("Should not send"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAI.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "claude-test", input = "Question"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "Question",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { conversation ->
                state = state.copy(
                    conversations = state.conversations + conversation,
                    selectedConversationId = conversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertFalse(state.isGenerating)
        assertEquals("当前 Provider 暂未接入聊天发送：anthropic。", state.error)
        assertEquals(0, chatProvider.requests.size)
        assertEquals(emptyList<Message>(), conversationRepository.allMessages())
    }

    @Test
    fun startCompressesLongHistoryBeforeProviderRequest() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI, maxContextTokens = 90)
        val conversationRepository = GenerationControllerConversationRepository(clock)
        val conversation = conversation(provider)
        conversationRepository.saveConversation(conversation)
        (1..14).forEach { index ->
            conversationRepository.saveMessage(
                historyMessage(
                    conversation = conversation,
                    id = "message-$index",
                    content = "message $index ${"x".repeat(48)}",
                    createdAt = clock.instant().plusMillis(index.toLong()),
                ),
            )
        }
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val chatProvider = GenerationControllerChatProvider(
            eventTurns = listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
            completionContent = "compressed summary",
        )
        val controller = GenerationController(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            conversationManager = ConversationManager(conversationRepository, clock),
            conversationCompactor = ConversationCompactor(conversationRepository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(ProviderType.OpenAI.value, chatProvider)
            },
            toolExecutor = toolExecutor(clock),
            clock = clock,
        )
        var state = ChatUiState(
            conversations = listOf(conversation),
            selectedConversationId = conversation.id,
            messages = conversationRepository.getMessages(conversation.id),
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            draft = DraftState(model = "model-a", input = "new question"),
        )

        controller.start(
            scope = this,
            current = state,
            userText = "new question",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { readyConversation ->
                state = state.copy(
                    conversations = state.conversations.filterNot { it.id == readyConversation.id } + readyConversation,
                    selectedConversationId = readyConversation.id,
                )
            },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val providerRequest = chatProvider.requests.last()
        assertTrue(providerRequest.systemPrompt.orEmpty().contains("compressed summary"))
        assertFalse(providerRequest.messages.any { it.content.startsWith("message 1 ") })
        assertTrue(providerRequest.messages.any { it.content.contains("message 14") })
        assertTrue(conversationRepository.allMessages().any { it.status == MessageStatus.Compressed })
    }

    private fun provider(id: String, type: ProviderType, maxContextTokens: Int? = null): ProviderConfig =
        ProviderConfig(
            id = ProviderId(id),
            name = id,
            type = type,
            baseUrl = "https://example.test/v1",
            apiKeyRef = null,
            headers = emptyMap(),
            models = listOfNotNull(
                maxContextTokens?.let {
                    ModelConfig(
                        id = "$id-model",
                        displayName = "$id model",
                        capability = ModelCapability(
                            model = "$id-model",
                            text = true,
                            vision = false,
                            imageGeneration = false,
                            toolCalling = true,
                            structuredOutput = false,
                            longContext = true,
                            maxContextTokens = it,
                        ),
                    )
                },
            ),
            defaultModel = "$id-model",
            enabled = true,
        )

    private fun conversation(provider: ProviderConfig): Conversation =
        Conversation(
            id = ConversationId("conversation-1"),
            title = "Chat",
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            defaultProviderId = provider.id,
            defaultModel = provider.defaultModel,
            modelParameters = ModelParameters(),
            systemPrompt = "Base system",
            isTemporary = false,
            isSensitive = false,
            archivedAt = null,
        )

    private fun historyMessage(
        conversation: Conversation,
        id: String,
        content: String,
        createdAt: Instant,
    ): Message =
        Message(
            id = MessageId(id),
            conversationId = conversation.id,
            role = MessageRole.User,
            content = content,
            contentParts = listOf(com.aichat.workbench.domain.model.MessagePart.Text(content)),
            providerId = conversation.defaultProviderId,
            model = conversation.defaultModel,
            status = MessageStatus.Completed,
            errorSummary = null,
            createdAt = createdAt,
            updatedAt = createdAt,
            toolCallId = null,
            parentMessageId = null,
        )
}

private class GenerationControllerChatProvider(
    private val eventTurns: List<Flow<ProviderStreamEvent>> = listOf(flowOf(ProviderStreamEvent.Completed)),
    private val completionContent: String = "",
) : ChatProvider {
    val requests = mutableListOf<ChatProviderRequest>()
    private var streamCount = 0

    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse {
        requests += request
        return ProviderTextResponse(completionContent)
    }

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> {
        requests += request
        val index = streamCount++
        return eventTurns.getOrElse(index) { flowOf(ProviderStreamEvent.Completed) }
    }
}

private fun toolExecutor(
    clock: Clock,
    toolInvocationRepository: ToolInvocationRepository = GenerationControllerToolInvocationRepository(),
): ToolExecutor =
    ToolExecutor(
        gatewaySettingsProvider = { GatewaySettings(enabled = false, baseUrl = "", apiToken = "") },
        gatewayClientProvider = { GatewayClient() },
        toolInvocationRepository = toolInvocationRepository,
        clock = clock,
    )

private class GenerationControllerToolInvocationRepository : ToolInvocationRepository {
    val savedResults = MutableStateFlow<List<ToolResult>>(emptyList())

    override fun observeToolInvocations(): Flow<List<ToolResult>> = savedResults

    override suspend fun saveToolResult(conversationId: ConversationId?, toolResult: ToolResult) {
        savedResults.value = savedResults.value + toolResult
    }
}

private class GenerationControllerProviderRepository(
    providers: List<ProviderConfig>,
    private val apiKeys: Map<ProviderId, String>,
) : ProviderConfigRepository {
    private val providers = MutableStateFlow(providers)

    override fun observeProviders(): Flow<List<ProviderConfig>> = providers

    override suspend fun getProvider(id: ProviderId): ProviderConfig? =
        providers.value.firstOrNull { it.id == id }

    override suspend fun saveProvider(
        provider: ProviderConfig,
        plaintextApiKey: String?,
        preserveExistingApiKey: Boolean,
        deleteReplacedApiKey: Boolean,
    ) {
        providers.value = providers.value.filterNot { it.id == provider.id } + provider
    }

    override suspend fun getApiKey(providerId: ProviderId): String? = apiKeys[providerId]

    override suspend fun deleteApiKeyRef(ref: String) = Unit

    override suspend fun deleteProvider(id: ProviderId) {
        providers.value = providers.value.filterNot { it.id == id }
    }
}

private class GenerationControllerConversationRepository(
    private val clock: Clock,
) : ConversationRepository {
    private val conversations = MutableStateFlow<List<Conversation>>(emptyList())
    private val messages = mutableMapOf<ConversationId, MutableStateFlow<List<Message>>>()

    fun allMessages(): List<Message> =
        messages.values.flatMap { it.value }

    override fun observeConversations(includeArchived: Boolean): Flow<List<Conversation>> = conversations

    override suspend fun getConversation(id: ConversationId): Conversation? =
        conversations.value.firstOrNull { it.id == id }

    override suspend fun saveConversation(conversation: Conversation) {
        conversations.value = conversations.value.filterNot { it.id == conversation.id } + conversation
        messages.getOrPut(conversation.id) { MutableStateFlow(emptyList()) }
    }

    override suspend fun renameConversation(id: ConversationId, title: String) {
        conversations.value = conversations.value.map {
            if (it.id == id) it.copy(title = title, updatedAt = clock.instant()) else it
        }
    }

    override suspend fun archiveConversation(id: ConversationId) = Unit

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
