package com.aichat.workbench.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.aichat.workbench.app.AppDispatchers
import com.aichat.workbench.app.ApplicationScope
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ConversationPreview
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ModelRolePreferenceId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ImageGenerationPreferences
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.StoredImagePaths
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderChatMessage
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.ImageGenerationProviderRequest
import com.aichat.workbench.provider.image.ImageGenerationProviderResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
        val legacyProvider = provider("legacy", ProviderType("legacy_vendor"))
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(
                providers = listOf(legacyProvider, openAi),
                apiKeys = mapOf(openAi.id to "openai-key", legacyProvider.id to "legacy-key"),
            ),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        assertEquals(listOf(openAi), viewModel.state.value.providers)
        assertEquals(openAi.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("openai / openai-model", chatSubtitle(viewModel.state.value))
    }

    @Test
    fun observesOnlyTextCapableChatProviders() = runTest(mainDispatcherRule.testDispatcher) {
        val chatProvider = provider(
            id = "chat",
            type = ProviderType.OpenAI,
            defaultModel = "gpt-5.4",
            models = listOf(model("gpt-5.4", text = true)),
        )
        val imageProvider = provider(
            id = "image",
            type = ProviderType.OpenAICompatible,
            defaultModel = "gpt-image-2",
            models = listOf(model("gpt-image-2", text = false, imageGeneration = true)),
        )
        val viewModel = startViewModel(
            conversationRepository = FakeConversationRepository(clock),
            providerRepository = FakeProviderConfigRepository(
                providers = listOf(imageProvider, chatProvider),
                apiKeys = mapOf(chatProvider.id to "chat-key", imageProvider.id to "image-key"),
            ),
            openAiProvider = RecordingChatProvider(),
            compatibleProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        assertEquals(listOf(chatProvider), viewModel.state.value.providers)
        assertEquals(chatProvider.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("chat / gpt-5.4", chatSubtitle(viewModel.state.value))
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

        viewModel.selectProvider(compatible.id.value)

        assertEquals(compatible.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("compatible / compatible-model", chatSubtitle(viewModel.state.value))
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

        viewModel.selectProvider(compatible.id.value)

        assertEquals(compatible.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("compatible / model-a", chatSubtitle(viewModel.state.value))
    }

    @Test
    fun editMessageSendsRewrittenHistory() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id)
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
        val conversation = conversation(defaultProviderId = openAi.id)
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
    fun retryMessageSendsHistoryBeforeCancelledMessage() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id)
        val user = message(conversation.id, MessageRole.User, "Question", MessageStatus.Completed)
        val cancelled = message(
            conversationId = conversation.id,
            role = MessageRole.Assistant,
            content = "partial",
            status = MessageStatus.Cancelled,
            providerId = openAi.id,
            model = "cancelled-model",
            errorSummary = "已停止，已保留当前回复内容。",
        )
        conversationRepository.seed(conversation, listOf(user, cancelled))
        val chatProvider = RecordingChatProvider(
            flowOf(ProviderStreamEvent.TextDelta("Retried"), ProviderStreamEvent.Completed),
        )
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = chatProvider,
        )
        advanceUntilIdle()

        viewModel.retryMessage(cancelled.id)
        advanceUntilIdle()

        val request = chatProvider.requests.single()
        assertEquals(listOf(ProviderChatMessage(MessageRole.User, "Question")), request.messages)
        assertEquals("cancelled-model", request.model)
        assertTrue(conversationRepository.allMessages().any { it.parentMessageId == cancelled.id && it.content == "Retried" })
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

    @Test
    fun cleanupOnExitMarksActiveAssistantMessageCancelled() = runTest(mainDispatcherRule.testDispatcher) {
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

        viewModel.cleanupOnExit()
        advanceUntilIdle()

        val assistant = conversationRepository.allMessages().single { it.role == MessageRole.Assistant }
        assertFalse(viewModel.state.value.isGenerating)
        assertEquals(MessageStatus.Cancelled, assistant.status)
        assertEquals("partial", assistant.content)
        assertEquals("已停止，已保留当前回复内容。", assistant.errorSummary)
    }


    @Test
    fun toggleSearchActivatesAndDeactivates() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id)
        conversationRepository.seed(conversation, emptyList())
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSearchActive)

        viewModel.toggleSearch()
        assertTrue(viewModel.state.value.isSearchActive)

        viewModel.toggleSearch()
        assertFalse(viewModel.state.value.isSearchActive)
    }

    @Test
    fun updateSearchQueryResetsMatchIndex() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id)
        conversationRepository.seed(conversation, listOf(
            message(conversation.id, MessageRole.User, "hello world", MessageStatus.Completed),
            message(conversation.id, MessageRole.Assistant, "hello there", MessageStatus.Completed),
        ))
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("hello")
        assertEquals(0, viewModel.state.value.currentMatchIndex)

        viewModel.navigateMatch(1)
        assertEquals(1, viewModel.state.value.currentMatchIndex)

        viewModel.updateSearchQuery("world")
        assertEquals(0, viewModel.state.value.currentMatchIndex)
    }

    @Test
    fun navigateMatchWithNoResultsIsNoop() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id)
        conversationRepository.seed(conversation, listOf(
            message(conversation.id, MessageRole.User, "hello", MessageStatus.Completed),
        ))
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.updateSearchQuery("zzzzz")
        assertEquals(0, viewModel.state.value.currentMatchIndex)
        assertEquals(0, viewModel.state.value.searchMatchCount)

        viewModel.navigateMatch(1)
        assertEquals(0, viewModel.state.value.currentMatchIndex)
    }
    @Test
    fun deleteMessageRemovesFromRepository() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id)
        val userMsg = message(conversation.id, MessageRole.User, "hello", MessageStatus.Completed)
        val assistantMsg = message(conversation.id, MessageRole.Assistant, "hi there", MessageStatus.Completed)
        conversationRepository.seed(conversation, listOf(userMsg, assistantMsg))
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.deleteMessage(assistantMsg.id)
        advanceUntilIdle()

        val remaining = conversationRepository.allMessages()
        assertEquals(1, remaining.size)
        assertEquals(userMsg.id, remaining[0].id)
    }

    @Test
    fun deleteMessageSkipsStreamingMessage() = runTest(mainDispatcherRule.testDispatcher) {
        val openAi = provider("openai", ProviderType.OpenAI)
        val conversationRepository = FakeConversationRepository(clock)
        val conversation = conversation(defaultProviderId = openAi.id)
        val userMsg = message(conversation.id, MessageRole.User, "hello", MessageStatus.Completed)
        val streamingMsg = message(conversation.id, MessageRole.Assistant, "", MessageStatus.Streaming)
        conversationRepository.seed(conversation, listOf(userMsg, streamingMsg))
        val viewModel = startViewModel(
            conversationRepository = conversationRepository,
            providerRepository = FakeProviderConfigRepository(listOf(openAi), mapOf(openAi.id to "key")),
            openAiProvider = RecordingChatProvider(),
        )
        advanceUntilIdle()

        viewModel.deleteMessage(streamingMsg.id)
        advanceUntilIdle()

        val remaining = conversationRepository.allMessages()
        assertEquals(2, remaining.size)
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
                    single {
                        AppDispatchers(
                            main = mainDispatcherRule.testDispatcher,
                            io = mainDispatcherRule.testDispatcher,
                            default = mainDispatcherRule.testDispatcher,
                        )
                    }
                    single { ApplicationScope(dispatchers = get()) }
                    single<ConversationRepository> { conversationRepository }
                    single<ProviderConfigRepository> { providerRepository }
                    single<ModelRolePreferenceRepository> { FakeModelRolePreferenceRepository() }
                    single<ImageGenerationPreferencesRepository> { FakeImageGenerationPreferencesRepository() }
                    single<ImageGenerationRepository> { FakeImageGenerationRepository() }
                    single<ImageGenerationProvider> { FakeImageGenerationProvider() }
                    single<ImageStorage> { FakeImageStorage() }
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
                        GenerationController(
                            conversationRepository = get(),
                            providerRepository = get(),
                            contextProvider = ConversationContextBuilder(get(), get()),
                            providerRegistry = get(),
                            createConversationUseCase = com.aichat.workbench.domain.usecase.CreateConversationUseCase(get(), get()),
                            clock = get(),
                        )
                    }
                    factory {
                        ChatViewModel(
                            savedStateHandle = get(),
                            conversationRepository = get(),
                            providerRepository = get(),
                            modelRolePreferenceRepository = get(),
                            conversationManager = get(),
                            generationController = get(),
                            providerRegistry = get(),
                            applicationScope = get(),
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

    private fun model(
        id: String,
        text: Boolean,
        imageGeneration: Boolean = false,
    ): ModelConfig =
        ModelConfig(
            id = id,
            displayName = id,
            capability = ModelCapability(
                model = id,
                text = text,
                vision = text,
                imageGeneration = imageGeneration,
                maxContextTokens = null,
            ),
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
            parentMessageId = null,
        )

    private var messageCounter = 0

}

@OptIn(ExperimentalCoroutinesApi::class)
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

private class FakeModelRolePreferenceRepository : ModelRolePreferenceRepository {
    private val preferences = MutableStateFlow<List<ModelRolePreference>>(emptyList())

    override fun observeAllRolePreferences(): Flow<List<ModelRolePreference>> = preferences

    override suspend fun setRoleModel(providerId: ProviderId, role: ModelRole, model: String?) {
        preferences.value = preferences.value.filterNot { it.providerId == providerId && it.role == role } +
            listOfNotNull(
                model?.trim()?.takeIf { it.isNotBlank() }?.let {
                    ModelRolePreference(
                        id = ModelRolePreferenceId("${providerId.value}:${role.name}"),
                        providerId = providerId,
                        role = role,
                        model = it,
                        updatedAt = Instant.parse("2026-06-01T00:00:00Z"),
                    )
                },
            )
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

    override suspend fun deleteMessages(conversationId: ConversationId) {
        messages.getOrPut(conversationId) { MutableStateFlow(emptyList()) }.value = emptyList()
    }

    override suspend fun deleteMessage(messageId: MessageId) {
        for ((_, flow) in messages) {
            val current = flow.value
            flow.value = current.filterNot { it.id == messageId }
        }
    }

    override fun observeConversationsWithPreview(): Flow<List<ConversationPreview>> =
        conversations.map { list ->
            list.map { c ->
                ConversationPreview(
                    id = c.id,
                    title = c.title,
                    createdAt = c.createdAt,
                    updatedAt = c.updatedAt,
                    defaultProviderId = c.defaultProviderId,
                    lastMessageContent = null,
                    lastMessageRole = null,
                )
            }
        }
}

private class FakeImageGenerationPreferencesRepository : ImageGenerationPreferencesRepository {
    private val preferences = MutableStateFlow(ImageGenerationPreferences())

    override fun observePreferences(): MutableStateFlow<ImageGenerationPreferences> = preferences

    override suspend fun saveSelectedProvider(providerId: String?) {
        preferences.value = ImageGenerationPreferences(providerId = providerId)
    }
}

private class FakeImageGenerationRepository : ImageGenerationRepository {
    private val generations = MutableStateFlow<List<ImageGeneration>>(emptyList())

    override fun observeImageGenerations(): Flow<List<ImageGeneration>> = generations

    override suspend fun getImageGeneration(id: ImageGenerationId): ImageGeneration? =
        generations.value.firstOrNull { it.id == id }

    override suspend fun saveImageGeneration(imageGeneration: ImageGeneration) {
        generations.value = generations.value.filterNot { it.id == imageGeneration.id } + imageGeneration
    }

    override suspend fun deleteImageGeneration(id: ImageGenerationId) {
        generations.value = generations.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllImageGenerations() {
        generations.value = emptyList()
    }
}

private class FakeImageGenerationProvider : ImageGenerationProvider {
    override suspend fun generate(
        request: ImageGenerationProviderRequest,
    ): ImageGenerationProviderResponse =
        ImageGenerationProviderResponse(emptyList())
}

private class FakeImageStorage : ImageStorage {
    override suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths =
        StoredImagePaths(
            originalPath = "original/${id.value}.png",
            thumbnailPath = "thumb/${id.value}.png",
        )

    override suspend fun deleteImage(id: ImageGenerationId) = Unit

    override suspend fun deleteAllImages() = Unit
}
