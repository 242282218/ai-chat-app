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
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
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
        val repository = GenerationControllerConversationRepository(clock)
        val chatProvider = GenerationControllerChatProvider(
            streamEvents = listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = controller(provider, repository, chatProvider)
        var state = stateFor(provider, input = "Question")

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

        val request = chatProvider.requests.single()
        assertFalse(state.isGenerating)
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Question")), request.messages)
        assertTrue(repository.allMessages().any { it.role == MessageRole.User && it.content == "Question" })
        assertTrue(
            repository.allMessages().any {
                it.role == MessageRole.Assistant &&
                    it.content == "Answer" &&
                    it.status == MessageStatus.Completed
            },
        )
    }

    @Test
    fun startSendsSingleProviderRequestForHostedProviders() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("new-api", ProviderType.NewApi)
        val repository = GenerationControllerConversationRepository(clock)
        val chatProvider = GenerationControllerChatProvider(
            streamEvents = listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = controller(provider, repository, chatProvider)
        var state = stateFor(provider, input = "Search")

        controller.start(
            scope = this,
            current = state,
            userText = "Search",
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

        val request = chatProvider.requests.single()
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Search")), request.messages)
    }

    @Test
    fun startKeepsChatModelForCodeLikeInput() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider(
            "openai",
            ProviderType.OpenAI,
            models = listOf(textModel("chat-model")),
            defaultModel = "chat-model",
        )
        val repository = GenerationControllerConversationRepository(clock)
        val chatProvider = GenerationControllerChatProvider(
            streamEvents = listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = controller(provider, repository, chatProvider)
        var state = stateFor(
            provider = provider,
            input = "请帮我修复 Kotlin 编译错误",
        )

        controller.start(
            scope = this,
            current = state,
            userText = state.input,
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

        assertEquals("chat-model", chatProvider.requests.single().model)
    }

    @Test
    fun startShowsClearErrorForUnregisteredProvider() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("custom", ProviderType.Custom)
        val repository = GenerationControllerConversationRepository(clock)
        val providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key"))
        val controller = GenerationController(
            conversationRepository = repository,
            providerRepository = providerRepository,
            contextProvider = ConversationContextBuilder(repository, clock),
            providerRegistry = ProviderRegistry(),
            createConversationUseCase = com.aichat.workbench.domain.usecase.CreateConversationUseCase(repository, clock),
            sendMessageUseCaseFactory = sendMessageUseCaseFactory(repository),
            clock = clock,
        )
        var state = stateFor(provider, input = "Question")

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

        assertEquals("当前 Provider 暂未接入聊天发送：custom。", state.error)
        assertEquals(emptyList<Conversation>(), repository.allConversations())
        assertEquals(emptyList<Message>(), repository.allMessages())
    }

    @Test
    fun startDoesNotCreateConversationWhenProviderIsMissing() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = GenerationControllerConversationRepository(clock)
        val controller = GenerationController(
            conversationRepository = repository,
            providerRepository = GenerationControllerProviderRepository(emptyList(), emptyMap()),
            contextProvider = ConversationContextBuilder(repository, clock),
            providerRegistry = ProviderRegistry(),
            createConversationUseCase = com.aichat.workbench.domain.usecase.CreateConversationUseCase(repository, clock),
            sendMessageUseCaseFactory = sendMessageUseCaseFactory(repository),
            clock = clock,
        )
        var state = ChatUiState(draft = DraftState(input = "Question"))

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

        assertEquals("模型连接未配置。", state.error)
        assertEquals(emptyList<Conversation>(), repository.allConversations())
        assertEquals(emptyList<Message>(), repository.allMessages())
    }

    @Test
    fun startDoesNotCreateConversationWhenApiKeyIsMissing() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI)
        val repository = GenerationControllerConversationRepository(clock)
        val chatProvider = GenerationControllerChatProvider(
            streamEvents = listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = GenerationController(
            conversationRepository = repository,
            providerRepository = GenerationControllerProviderRepository(listOf(provider), emptyMap()),
            contextProvider = ConversationContextBuilder(repository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(provider.type.value, chatProvider)
            },
            createConversationUseCase = com.aichat.workbench.domain.usecase.CreateConversationUseCase(repository, clock),
            sendMessageUseCaseFactory = sendMessageUseCaseFactory(repository),
            clock = clock,
        )
        var state = stateFor(provider, input = "Question")

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

        assertEquals("API Key 缺失。", state.error)
        assertEquals(emptyList<Conversation>(), repository.allConversations())
        assertEquals(emptyList<Message>(), repository.allMessages())
        assertEquals(0, chatProvider.requests.size)
    }

    @Test
    fun startCompressesLongHistoryBeforeProviderRequest() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI, maxContextTokens = 120)
        val conversation = conversation(provider)
        val repository = GenerationControllerConversationRepository(clock).apply {
            seed(
                conversation,
                (1..16).map { index ->
                    historyMessage(
                        conversation = conversation,
                        id = "history-$index",
                        content = "history $index ${"x".repeat(48)}",
                        createdAtOffset = index.toLong(),
                    )
                },
            )
        }
        val chatProvider = GenerationControllerChatProvider(
            summary = "compressed summary",
            streamEvents = listOf(flowOf(ProviderStreamEvent.TextDelta("Answer"), ProviderStreamEvent.Completed)),
        )
        val controller = controller(provider, repository, chatProvider)
        var state = stateFor(provider, input = "New question").copy(
            conversations = listOf(conversation),
            selectedConversationId = conversation.id,
        )

        controller.start(
            scope = this,
            current = state,
            userText = "New question",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { updated -> state = state.copy(selectedConversationId = updated.id) },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        val request = chatProvider.requests.single()
        assertTrue(request.systemPrompt.orEmpty().contains("compressed summary"))
        assertTrue(repository.allMessages().any { it.status == MessageStatus.Compressed })
    }

    @Test
    fun startReportsCompressionFailureBeforeProviderStream() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider("openai", ProviderType.OpenAI, maxContextTokens = 120)
        val conversation = conversation(provider)
        val repository = GenerationControllerConversationRepository(clock).apply {
            seed(
                conversation,
                (1..16).map { index ->
                    historyMessage(
                        conversation = conversation,
                        id = "history-$index",
                        content = "history $index ${"x".repeat(48)}",
                        createdAtOffset = index.toLong(),
                    )
                },
            )
        }
        val chatProvider = GenerationControllerChatProvider(summaryError = IllegalStateException("summary failed"))
        val controller = controller(provider, repository, chatProvider)
        var state = stateFor(provider, input = "New question").copy(
            conversations = listOf(conversation),
            selectedConversationId = conversation.id,
        )

        controller.start(
            scope = this,
            current = state,
            userText = "New question",
            editedMessage = null,
            retryFailedMessage = null,
            onConversationReady = { updated -> state = state.copy(selectedConversationId = updated.id) },
            onStateChanged = { transform -> state = transform(state) },
        )
        advanceUntilIdle()

        assertTrue(state.error.orEmpty().contains("长对话压缩失败"))
        assertEquals(0, chatProvider.requests.size)
    }

    private fun controller(
        provider: ProviderConfig,
        repository: GenerationControllerConversationRepository,
        chatProvider: ChatProvider,
    ): GenerationController =
        GenerationController(
            conversationRepository = repository,
            providerRepository = GenerationControllerProviderRepository(listOf(provider), mapOf(provider.id to "key")),
            contextProvider = ConversationContextBuilder(repository, clock),
            providerRegistry = ProviderRegistry().apply {
                register(provider.type.value, chatProvider)
            },
            createConversationUseCase = com.aichat.workbench.domain.usecase.CreateConversationUseCase(repository, clock),
            sendMessageUseCaseFactory = sendMessageUseCaseFactory(repository),
            clock = clock,
        )

    private fun sendMessageUseCaseFactory(repository: ConversationRepository): SendMessageUseCaseFactory =
        SendMessageUseCaseFactory { chatProvider ->
            com.aichat.workbench.domain.usecase.SendMessageUseCase(repository, chatProvider, clock)
        }

    private fun stateFor(
        provider: ProviderConfig,
        input: String,
        rolePreferences: List<ModelRolePreference> = emptyList(),
    ): ChatUiState =
        ChatUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id.value,
            modelRolePreferences = rolePreferences,
            draft = DraftState(input = input),
        )

    private fun provider(
        id: String,
        type: ProviderType,
        models: List<ModelConfig> = listOf(textModel("$id-model")),
        defaultModel: String? = models.firstOrNull()?.id,
        maxContextTokens: Int? = null,
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId(id),
            name = id,
            type = type,
            baseUrl = "https://example.test/v1",
            apiKeyRef = null,
            headers = emptyMap(),
            models = models.map { model ->
                model.copy(capability = model.capability?.copy(maxContextTokens = maxContextTokens ?: model.capability.maxContextTokens))
            },
            defaultModel = defaultModel,
            enabled = true,
        )

    private fun textModel(id: String): ModelConfig =
        ModelConfig(
            id = id,
            displayName = id,
            capability = ModelCapability(
                model = id,
                text = true,
                vision = true,
                imageGeneration = false,
                maxContextTokens = null,
            ),
        )

    private fun conversation(provider: ProviderConfig): Conversation =
        Conversation(
            id = ConversationId("conversation-1"),
            title = "Existing",
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            defaultProviderId = provider.id,
        )

    private fun historyMessage(
        conversation: Conversation,
        id: String,
        content: String,
        createdAtOffset: Long,
    ): Message =
        Message(
            id = MessageId(id),
            conversationId = conversation.id,
            role = MessageRole.User,
            content = content,
            contentParts = listOf(MessagePart.Text(content)),
            providerId = conversation.defaultProviderId,
            model = "model-a",
            status = MessageStatus.Completed,
            errorSummary = null,
            createdAt = clock.instant().plusMillis(createdAtOffset),
            updatedAt = clock.instant().plusMillis(createdAtOffset),
            parentMessageId = null,
        )

}

private class GenerationControllerChatProvider(
    private val summary: String = "summary",
    private val summaryError: Throwable? = null,
    private val streamEvents: List<Flow<ProviderStreamEvent>> = emptyList(),
) : ChatProvider {
    val requests = mutableListOf<ChatProviderRequest>()

    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse {
        summaryError?.let { throw it }
        return ProviderTextResponse(summary)
    }

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> {
        requests += request
        return streamEvents.getOrNull(requests.lastIndex) ?: flowOf(ProviderStreamEvent.Completed)
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

    fun seed(conversation: Conversation, seedMessages: List<Message>) {
        conversations.value = conversations.value.filterNot { it.id == conversation.id } + conversation
        messages.getOrPut(conversation.id) { MutableStateFlow(emptyList()) }.value = seedMessages
    }

    fun allMessages(): List<Message> =
        messages.values.flatMap { it.value }

    fun allConversations(): List<Conversation> =
        conversations.value

    override fun observeConversations(): Flow<List<Conversation>> = conversations

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

    override suspend fun deleteConversation(id: ConversationId) {
        conversations.value = conversations.value.filterNot { it.id == id }
        messages.remove(id)
    }

    override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> =
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }

    override suspend fun getMessages(conversationId: ConversationId): List<Message> =
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value

    override suspend fun saveMessage(message: Message) {
        val flow = messages.getOrPut(message.conversationId) { MutableStateFlow(emptyList()) }
        flow.value = flow.value.filterNot { it.id == message.id } + message
    }

    override suspend fun deleteMessage(messageId: com.aichat.workbench.domain.model.MessageId) = Unit

    override fun observeConversationsWithPreview(): kotlinx.coroutines.flow.Flow<List<com.aichat.workbench.domain.model.ConversationPreview>> = kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun deleteMessages(conversationId: ConversationId) {
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value = emptyList()
    }
}
