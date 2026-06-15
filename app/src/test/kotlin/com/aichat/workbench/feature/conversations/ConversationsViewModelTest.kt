package com.aichat.workbench.feature.conversations

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationPreview
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
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
    fun exposesAllConversations() = runTest(mainDispatcherRule.testDispatcher) {
        val conversations = (1..35).map(::testConversation)
        val viewModel = ConversationsViewModel(
            conversationRepository = ConversationsOnlyRepository(conversations),
            providerRepository = ProvidersOnlyRepository(listOf(testProvider())),
            providerRegistry = testProviderRegistry(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        advanceUntilIdle()

        assertEquals(35, viewModel.state.value.recentConversations.size)
        assertEquals("Conversation 1", viewModel.state.value.recentConversations.first().title)
        assertEquals("Conversation 35", viewModel.state.value.recentConversations.last().title)
    }

    @Test
    fun reportsAvailableChatProviderOnlyForEnabledRegisteredTextProvider() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = ConversationsViewModel(
            conversationRepository = ConversationsOnlyRepository(),
            providerRepository = ProvidersOnlyRepository(
                listOf(
                    testProvider(id = "disabled", enabled = false),
                    testProvider(
                        id = "image-only",
                        models = listOf(
                            ModelConfig(
                                id = "gpt-image-1",
                                displayName = "gpt-image-1",
                                capability = ModelCapability(
                                    model = "gpt-image-1",
                                    text = false,
                                    vision = false,
                                    imageGeneration = true,
                                    maxContextTokens = null,
                                ),
                            ),
                        ),
                        defaultModel = "gpt-image-1",
                    ),
                    testProvider(id = "chat"),
                ),
            ),
            providerRegistry = testProviderRegistry(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.hasAvailableChatProvider)
    }

    @Test
    fun reportsNoChatProviderWhenOnlyDisabledOrImageOnlyProvidersExist() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = ConversationsViewModel(
            conversationRepository = ConversationsOnlyRepository(),
            providerRepository = ProvidersOnlyRepository(
                listOf(
                    testProvider(id = "disabled", enabled = false),
                    testProvider(
                        id = "image-only",
                        models = listOf(
                            ModelConfig(
                                id = "gpt-image-1",
                                displayName = "gpt-image-1",
                                capability = ModelCapability(
                                    model = "gpt-image-1",
                                    text = false,
                                    vision = false,
                                    imageGeneration = true,
                                    maxContextTokens = null,
                                ),
                            ),
                        ),
                        defaultModel = "gpt-image-1",
                    ),
                ),
            ),
            providerRegistry = testProviderRegistry(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        advanceUntilIdle()

        assertEquals(false, viewModel.state.value.hasAvailableChatProvider)
    }

    @Test
    fun deleteConversationCallsRepository() = runTest(mainDispatcherRule.testDispatcher) {
        var deletedId: ConversationId? = null
        val repo = object : ConversationRepository by ConversationsOnlyRepository(listOf(testConversation(1))) {
            override suspend fun deleteConversation(id: ConversationId) { deletedId = id }
        }
        val viewModel = ConversationsViewModel(
            conversationRepository = repo,
            providerRepository = ProvidersOnlyRepository(listOf(testProvider())),
            providerRegistry = testProviderRegistry(),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        advanceUntilIdle()

        viewModel.deleteConversation(ConversationId("conversation-1"))
        advanceUntilIdle()

        assertEquals(ConversationId("conversation-1"), deletedId)
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

    override suspend fun deleteMessage(messageId: com.aichat.workbench.domain.model.MessageId) = Unit

    override fun observeConversationsWithPreview(): Flow<List<ConversationPreview>> = flowOf(
        conversations.map { conv ->
            ConversationPreview(
                id = conv.id,
                title = conv.title,
                createdAt = conv.createdAt,
                updatedAt = conv.updatedAt,
                defaultProviderId = conv.defaultProviderId,
                lastMessageContent = null,
                lastMessageRole = null,
            )
        }
    )

    override suspend fun deleteMessages(conversationId: ConversationId) = Unit
}

private class ProvidersOnlyRepository(
    private val providers: List<ProviderConfig> = emptyList(),
) : ProviderConfigRepository {
    override fun observeProviders(): Flow<List<ProviderConfig>> = flowOf(providers)

    override suspend fun getProvider(id: ProviderId): ProviderConfig? =
        providers.firstOrNull { it.id == id }

    override suspend fun saveProvider(
        provider: ProviderConfig,
        plaintextApiKey: String?,
        preserveExistingApiKey: Boolean,
        deleteReplacedApiKey: Boolean,
    ) = Unit

    override suspend fun getApiKey(providerId: ProviderId): String? = null

    override suspend fun deleteApiKeyRef(ref: String) = Unit

    override suspend fun deleteProvider(id: ProviderId) = Unit
}

private class NoopChatProvider : ChatProvider {
    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse =
        ProviderTextResponse("")

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> =
        flowOf(ProviderStreamEvent.Completed)
}

private fun testConversation(index: Int): Conversation =
    Conversation(
        id = ConversationId("conversation-$index"),
        title = "Conversation $index",
        createdAt = Instant.EPOCH.plusSeconds(index.toLong()),
        updatedAt = Instant.EPOCH.plusSeconds(index.toLong()),
        defaultProviderId = null,
    )

private fun testProvider(
    id: String = "provider",
    enabled: Boolean = true,
    models: List<ModelConfig> = emptyList(),
    defaultModel: String? = "chat-model",
): ProviderConfig =
    ProviderConfig(
        id = ProviderId(id),
        name = id,
        type = ProviderType.OpenAI,
        baseUrl = "https://example.test/v1",
        apiKeyRef = null,
        headers = emptyMap(),
        models = models,
        defaultModel = defaultModel,
        enabled = enabled,
    )

private fun testProviderRegistry(): ProviderRegistry =
    ProviderRegistry().apply {
        register(ProviderType.OpenAI.value, NoopChatProvider())
    }
