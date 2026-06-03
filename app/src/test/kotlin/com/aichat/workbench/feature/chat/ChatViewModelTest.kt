package com.aichat.workbench.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.aichat.workbench.data.settings.GatewaySettings
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.PromptPresetRepository
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest : KoinTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun sendMessageUsesSelectedProvider() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val compatible = provider("compatible", ProviderType.OpenAICompatible)
        val conversationRepository = FakeConversationRepository(clock)
        val providerRepository = FakeProviderConfigRepository(
            providers = listOf(openAi, compatible),
            apiKeys = mapOf(openAi.id to "openai-key", compatible.id to "compatible-key"),
        )
        val openAiProvider = RecordingChatProvider()
        val compatibleProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("兼容回复"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = providerRepository,
            openAiProvider = openAiProvider,
            compatibleProvider = compatibleProvider,
        )
        advanceUntilIdle()

        viewModel.selectProvider(compatible.id.value)
        viewModel.updateModelDraft("compatible-model")
        viewModel.updateInput("Hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(0, openAiProvider.requests.size)
        assertEquals(1, compatibleProvider.requests.size)
        val request = compatibleProvider.requests.single()
        assertEquals(compatible, request.provider)
        assertEquals("compatible-key", request.apiKey)
        assertEquals("compatible-model", request.model)
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Hello")), request.messages)
        assertFalse(viewModel.state.value.isGenerating)
        assertEquals("", viewModel.state.value.input)
        assertTrue(conversationRepository.allMessages().any { it.content == "兼容回复" && it.status == MessageStatus.Completed })
    }

    @Test
    fun observesOnlyRegisteredChatProviders() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val anthropic = provider("anthropic", ProviderType.Anthropic)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(
                providers = listOf(anthropic, openAi),
                apiKeys = mapOf(openAi.id to "openai-key", anthropic.id to "anthropic-key"),
            ),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        assertEquals(listOf(openAi), viewModel.state.value.providers)
        assertEquals(openAi.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("openai-model", viewModel.state.value.modelDraft)
    }

    @Test
    fun selectProviderResetsModelToSelectedProviderDefault() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val compatible = provider("compatible", ProviderType.OpenAICompatible)
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(
                providers = listOf(openAi, compatible),
                apiKeys = mapOf(openAi.id to "openai-key", compatible.id to "compatible-key"),
            ),
            openAiProvider = RecordingChatProvider(),
            compatibleProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.updateModelDraft("manual-openai-model")
        viewModel.selectProvider(compatible.id.value)

        assertEquals(compatible.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("compatible-model", viewModel.state.value.modelDraft)
    }

    @Test
    fun selectProviderFallsBackToFirstDiscoveredModel() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val compatible = provider(
            id = "compatible",
            type = ProviderType.OpenAICompatible,
            defaultModel = null,
            models = listOf(ModelConfig("model-a", "Model A", capability = null)),
        )
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(
                providers = listOf(openAi, compatible),
                apiKeys = mapOf(openAi.id to "openai-key", compatible.id to "compatible-key"),
            ),
            openAiProvider = RecordingChatProvider(),
            compatibleProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.updateModelDraft("manual-openai-model")
        viewModel.selectProvider(compatible.id.value)

        assertEquals(compatible.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("model-a", viewModel.state.value.modelDraft)
    }

    @Test
    fun editMessageSendsRewrittenHistory() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id, defaultModel = "gpt-test")
        val originalUser = message(conversation.id, MessageRole.User, "Original", MessageStatus.Completed)
        conversationRepository.seed(conversation, listOf(originalUser, message(conversation.id, MessageRole.Assistant, "Old", MessageStatus.Completed)))
        val chatProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("New answer"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        advanceUntilIdle()

        viewModel.editMessage(originalUser.id)
        viewModel.updateInput("Revised")
        viewModel.sendMessage()
        advanceUntilIdle()

        val request = chatProvider.requests.single()
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Revised")), request.messages)
        assertNotNull(conversationRepository.allMessages().first { it.content == "Revised" }.parentMessageId)
    }

    @Test
    fun sendMessageWithImageSendsImageContentParts() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val chatProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("Image answer"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        val image = MessagePart.Image("data:image/jpeg;base64,abc", "image/jpeg")
        advanceUntilIdle()

        viewModel.addImageDraft(image)
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(
            listOf(
                ProviderChatMessage(
                    role = MessageRole.User,
                    content = "图片消息",
                    contentParts = listOf(MessagePart.Text("图片消息"), image),
                ),
            ),
            chatProvider.requests.single().messages,
        )
        assertEquals(emptyList<MessagePart.Image>(), viewModel.state.value.imageDrafts)
    }

    @Test
    fun sendMessageWithImageShowsErrorForKnownNonVisionModel() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI, vision = false)
        val conversationRepository = FakeConversationRepository(clock)
        val chatProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("should not send"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        val image = MessagePart.Image("data:image/jpeg;base64,abc", "image/jpeg")
        advanceUntilIdle()

        viewModel.addImageDraft(image)
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(0, chatProvider.requests.size)
        assertEquals("当前模型不支持图片输入，请切换到视觉模型。", viewModel.state.value.error)
        assertEquals(listOf(image), viewModel.state.value.imageDrafts)
    }

    @Test
    fun retryMessageSendsHistoryBeforeFailure() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id, defaultModel = "gpt-test")
        val user = message(conversation.id, MessageRole.User, "Question", MessageStatus.Completed)
        val failed = message(
            conversationId = conversation.id,
            role = MessageRole.Assistant,
            content = "",
            status = MessageStatus.Failed,
            providerId = openAi.id,
            model = "failed-model",
            errorSummary = "network error",
        )
        conversationRepository.seed(conversation, listOf(user, failed))
        val chatProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("Retried"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        advanceUntilIdle()

        viewModel.retryMessage(failed.id)
        advanceUntilIdle()

        val request = chatProvider.requests.single()
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Question")), request.messages)
        assertEquals("failed-model", request.model)
        assertTrue(conversationRepository.allMessages().any { it.parentMessageId == failed.id && it.content == "Retried" })
    }

    @Test
    fun generationStateStaysActiveUntilStreamCompletes() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val events = Channel<ProviderStreamEvent>(Channel.UNLIMITED)
        val chatProvider = RecordingChatProvider(events.receiveAsFlow())
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        advanceUntilIdle()

        viewModel.updateInput("Stream")
        viewModel.sendMessage()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isGenerating)

        events.send(ProviderStreamEvent.TextDelta("partial"))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isGenerating)
        assertTrue(viewModel.state.value.messages.any { it.content == "partial" && it.status == MessageStatus.Streaming })

        events.send(ProviderStreamEvent.Completed)
        events.close()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isGenerating)
        assertTrue(conversationRepository.allMessages().any { it.content == "partial" && it.status == MessageStatus.Completed })
    }

    @Test
    fun stopGenerationMarksActiveAssistantMessageCancelled() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val events = Channel<ProviderStreamEvent>(Channel.UNLIMITED)
        val chatProvider = RecordingChatProvider(events.receiveAsFlow())
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        advanceUntilIdle()

        viewModel.updateInput("Stream")
        viewModel.sendMessage()
        advanceUntilIdle()
        events.send(ProviderStreamEvent.TextDelta("partial"))
        advanceUntilIdle()

        viewModel.stopGeneration()
        advanceUntilIdle()

        val assistant = conversationRepository.allMessages().single { it.role == MessageRole.Assistant }
        assertFalse(viewModel.state.value.isGenerating)
        assertEquals(MessageStatus.Cancelled, assistant.status)
        assertEquals("partial", assistant.content)
        assertEquals("已停止，已保留当前回复内容。", assistant.errorSummary)
    }

    private fun startViewModel(
        conversationRepository: ConversationRepository,
        providerRepository: ProviderConfigRepository,
        openAiProvider: ChatProvider,
        compatibleProvider: ChatProvider = RecordingChatProvider(),
    ): ChatViewModel {
        runCatching { stopKoin() }
        startKoin {
            modules(
                module {
                    single { clock }
                    single<ConversationRepository> { conversationRepository }
                    single<ProviderConfigRepository> { providerRepository }
                    single<PromptPresetRepository> { FakePromptPresetRepository() }
                    single<ToolInvocationRepository> { FakeToolInvocationRepository() }
                    single<ChatProvider>(named("openai")) { openAiProvider }
                    single<ChatProvider>(named("compatible")) { compatibleProvider }
                    single {
                        ProviderRegistry().apply {
                            register(ProviderType.OpenAI.value, get(named("openai")))
                            register(ProviderType.OpenAICompatible.value, get(named("compatible")))
                        }
                    }
                    factory { SavedStateHandle() }
                    factory { ConversationManager(conversationRepository = get(), clock = get()) }
                    factory {
                        ToolExecutor(
                            gatewaySettingsProvider = { GatewaySettings(enabled = false, baseUrl = "", apiToken = "") },
                            gatewayClientProvider = { GatewayClient() },
                            toolInvocationRepository = get(),
                            clock = get(),
                        )
                    }
                    factory {
                        GenerationController(
                            conversationRepository = get(),
                            providerRepository = get(),
                            conversationManager = get(),
                            conversationCompactor = ConversationCompactor(get(), get()),
                            providerRegistry = get(),
                            toolExecutor = get(),
                            clock = get(),
                        )
                    }
                    factory {
                        ChatViewModel(
                            savedStateHandle = get(),
                            conversationRepository = get(),
                            providerRepository = get(),
                            promptPresetRepository = get(),
                            conversationManager = get(),
                            generationController = get(),
                            providerRegistry = get(),
                        )
                    }
                },
            )
        }
        return get()
    }

    private fun provider(
        id: String,
        type: ProviderType,
        vision: Boolean? = null,
        defaultModel: String? = "$id-model",
        models: List<ModelConfig> = listOfNotNull(
            vision?.let {
                ModelConfig(
                    id = "$id-model",
                    displayName = "$id model",
                    capability = ModelCapability(
                        model = "$id-model",
                        text = true,
                        vision = it,
                        imageGeneration = false,
                        toolCalling = true,
                        structuredOutput = false,
                        longContext = false,
                        maxContextTokens = 32_000,
                    ),
                )
            },
        ),
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId(id),
            name = id,
            type = type,
            baseUrl = "https://example.test/v1",
            apiKeyRef = null,
            headers = emptyMap(),
            models = models,
            defaultModel = defaultModel,
            enabled = true,
        )

    private fun conversation(
        defaultProviderId: ProviderId?,
        defaultModel: String?,
    ): Conversation =
        Conversation(
            id = ConversationId("conversation-${defaultProviderId?.value ?: "none"}"),
            title = "Existing",
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            defaultProviderId = defaultProviderId,
            defaultModel = defaultModel,
            modelParameters = ModelParameters(),
            systemPrompt = null,
            isTemporary = false,
            isSensitive = false,
            archivedAt = null,
        )

    private fun message(
        conversationId: ConversationId,
        role: MessageRole,
        content: String,
        status: MessageStatus,
        providerId: ProviderId? = null,
        model: String? = null,
        errorSummary: String? = null,
    ): Message =
        Message(
            id = MessageId("message-${messageCounter++}"),
            conversationId = conversationId,
            role = role,
            content = content,
            contentParts = if (content.isBlank()) emptyList() else listOf(MessagePart.Text(content)),
            providerId = providerId,
            model = model,
            status = status,
            errorSummary = errorSummary,
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            toolCallId = null,
            parentMessageId = null,
        )

    private var messageCounter = 0
}

class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class RecordingChatProvider(
    private val events: Flow<ProviderStreamEvent> = flowOf(ProviderStreamEvent.Completed),
) : ChatProvider {
    val requests = mutableListOf<ChatProviderRequest>()

    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse {
        requests += request
        return ProviderTextResponse("")
    }

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> {
        requests += request
        return events
    }
}

private class FakeProviderConfigRepository(
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

private class FakeConversationRepository(
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

    override suspend fun archiveConversation(id: ConversationId) {
        conversations.value = conversations.value.map {
            if (it.id == id) it.copy(archivedAt = clock.instant(), updatedAt = clock.instant()) else it
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

    override suspend fun deleteMessages(conversationId: ConversationId) {
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value = emptyList()
    }
}

private class FakePromptPresetRepository : PromptPresetRepository {
    private val presets = MutableStateFlow<List<PromptPreset>>(emptyList())

    override fun observePromptPresets(): Flow<List<PromptPreset>> = presets

    override suspend fun getPromptPreset(id: PromptPresetId): PromptPreset? =
        presets.value.firstOrNull { it.id == id }

    override suspend fun savePromptPreset(promptPreset: PromptPreset) {
        presets.value = presets.value.filterNot { it.id == promptPreset.id } + promptPreset
    }

    override suspend fun deletePromptPreset(id: PromptPresetId) {
        presets.value = presets.value.filterNot { it.id == id }
    }
}

private class FakeToolInvocationRepository : ToolInvocationRepository {
    private val results = MutableStateFlow<List<ToolResult>>(emptyList())

    override fun observeToolInvocations(): Flow<List<ToolResult>> = results

    override suspend fun saveToolResult(conversationId: ConversationId?, toolResult: ToolResult) {
        results.value = results.value + toolResult
    }
}
