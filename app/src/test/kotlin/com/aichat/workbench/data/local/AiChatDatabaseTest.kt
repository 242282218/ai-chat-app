package com.aichat.workbench.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.crypto.SecretStoreException
import com.aichat.workbench.data.local.entity.ConversationEntity
import com.aichat.workbench.data.local.entity.MessageEntity
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.data.repository.RoomConversationRepository
import com.aichat.workbench.data.repository.RoomModelRolePreferenceRepository
import com.aichat.workbench.data.repository.RoomProviderConfigRepository
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.usecase.CreateConversationUseCase
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import com.aichat.workbench.domain.usecase.SendMessageUseCase
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderError
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiChatDatabaseTest {
    private lateinit var database: AiChatDatabase
    private lateinit var clock: Clock

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AiChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneOffset.UTC)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun conversations_canBeCreatedRenamedAndDeleted() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val createConversation = CreateConversationUseCase(repository, clock)

        val conversation = createConversation(title = "Planning")

        assertEquals("Planning", repository.getConversation(conversation.id)?.title)
        assertEquals(1, repository.observeConversations().first().size)

        repository.renameConversation(conversation.id, "Renamed")
        assertEquals("Renamed", repository.getConversation(conversation.id)?.title)

        repository.deleteConversation(conversation.id)
        assertNull(repository.getConversation(conversation.id))
    }

    @Test
    fun messages_areStoredByConversationAndCascadeWhenConversationDeleted() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val conversation = CreateConversationUseCase(repository, clock)(title = "Chat")
        val message = message(conversation.id)

        repository.saveMessage(message)

        assertEquals(listOf(message), repository.getMessages(conversation.id))

        repository.deleteConversation(conversation.id)
        assertEquals(emptyList<Message>(), repository.getMessages(conversation.id))
    }

    @Test
    fun messages_canBeClearedWithoutDeletingConversation() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val conversation = CreateConversationUseCase(repository, clock)(title = "Chat")

        repository.saveMessage(message(conversation.id).copy(id = MessageId("message-1")))
        repository.saveMessage(message(conversation.id).copy(id = MessageId("message-2")))

        repository.deleteMessages(conversation.id)

        assertEquals("Chat", repository.getConversation(conversation.id)?.title)
        assertEquals(emptyList<Message>(), repository.getMessages(conversation.id))
    }

    @Test
    fun recentMessages_returnWindowWhileCountTracksFullConversation() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val conversation = CreateConversationUseCase(repository, clock)(title = "Long chat")

        (1..5).forEach { index ->
            repository.saveMessage(
                message(conversation.id).copy(
                    id = MessageId("message-$index"),
                    content = "Message $index",
                    contentParts = listOf(MessagePart.Text("Message $index")),
                    createdAt = clock.instant().plusMillis(index.toLong()),
                    updatedAt = clock.instant().plusMillis(index.toLong()),
                ),
            )
        }

        val recent = repository.observeRecentMessages(conversation.id, limit = 2).first()
        val count = repository.observeMessageCount(conversation.id).first()

        assertEquals(listOf("message-4", "message-5"), recent.map { it.id.value })
        assertEquals(listOf("Message 4", "Message 5"), recent.map { it.content })
        assertEquals(5, count)
    }

    @Test
    fun conversationPreviews_useLatestMessageByCreatedAtAndId() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val conversation = CreateConversationUseCase(repository, clock)(title = "Preview chat")
        val createdAt = clock.instant().plusMillis(1)
        repository.saveMessage(
            message(conversation.id).copy(
                id = MessageId("message-a"),
                content = "Earlier id",
                contentParts = listOf(MessagePart.Text("Earlier id")),
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )
        repository.saveMessage(
            message(conversation.id).copy(
                id = MessageId("message-b"),
                role = MessageRole.Assistant,
                content = "Later id",
                contentParts = listOf(MessagePart.Text("Later id")),
                createdAt = createdAt,
                updatedAt = createdAt,
            ),
        )

        val preview = repository.observeConversationsWithPreview().first().single()

        assertEquals("Later id", preview.lastMessageContent)
        assertEquals("Assistant", preview.lastMessageRole)
    }

    @Test
    fun deleteMessageAndFollowing_removesEditedMessageBranch() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val conversation = CreateConversationUseCase(repository, clock)(title = "Chat")
        val first = message(conversation.id).copy(
            id = MessageId("message-1"),
            content = "Keep",
            contentParts = listOf(MessagePart.Text("Keep")),
            createdAt = clock.instant().plusMillis(1),
            updatedAt = clock.instant().plusMillis(1),
        )
        val edited = message(conversation.id).copy(
            id = MessageId("message-2"),
            content = "Old question",
            contentParts = listOf(MessagePart.Text("Old question")),
            createdAt = clock.instant().plusMillis(2),
            updatedAt = clock.instant().plusMillis(2),
        )
        val oldAssistant = message(conversation.id).copy(
            id = MessageId("message-3"),
            role = MessageRole.Assistant,
            content = "Old answer",
            contentParts = listOf(MessagePart.Text("Old answer")),
            createdAt = clock.instant().plusMillis(3),
            updatedAt = clock.instant().plusMillis(3),
        )
        listOf(first, edited, oldAssistant).forEach { repository.saveMessage(it) }

        repository.deleteMessageAndFollowing(edited)

        assertEquals(listOf("Keep"), repository.getMessages(conversation.id).map { it.content })
    }

    @Test
    fun compressedMessages_roundTripThroughRoom() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val conversation = CreateConversationUseCase(repository, clock)(title = "Chat")
        val summary = message(conversation.id).copy(
            id = MessageId("summary-1"),
            role = MessageRole.System,
            content = "早期对话摘要",
            contentParts = listOf(MessagePart.Text("早期对话摘要")),
            status = MessageStatus.Compressed,
        )

        repository.saveMessage(summary)

        assertEquals(summary, repository.getMessages(conversation.id).single())
    }

    @Test
    fun legacyOrMalformedMessagePayloads_doNotCrashConversationLoad() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        database.conversationDao().upsertConversation(
            ConversationEntity(
                id = "conversation-legacy",
                title = "Legacy",
                createdAt = 1L,
                updatedAt = 1L,
                defaultProviderId = null,
            ),
        )
        database.conversationDao().upsertMessage(
            MessageEntity(
                id = "message-legacy",
                conversationId = "conversation-legacy",
                role = "assistant",
                content = "legacy content",
                contentPartsJson = "{bad json",
                providerId = null,
                model = null,
                status = "Canceled",
                errorSummary = null,
                createdAt = 1L,
                updatedAt = 1L,
                parentMessageId = null,
            ),
        )

        val saved = repository.getMessages(ConversationId("conversation-legacy")).single()

        assertEquals(MessageRole.Assistant, saved.role)
        assertEquals(MessageStatus.Cancelled, saved.status)
        assertEquals("legacy content", saved.content)
        assertEquals(emptyList<MessagePart>(), saved.contentParts)
    }

    @Test
    fun legacyInlineMarkdownImages_areRecoveredIntoContentParts() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        database.conversationDao().upsertConversation(
            ConversationEntity(
                id = "conversation-inline-image",
                title = "Inline image",
                createdAt = 1L,
                updatedAt = 1L,
                defaultProviderId = null,
            ),
        )
        database.conversationDao().upsertMessage(
            MessageEntity(
                id = "message-inline-image",
                conversationId = "conversation-inline-image",
                role = "assistant",
                content = "生成结果\n![generated image](data:image/png;base64,AAAA)",
                contentPartsJson = "[]",
                providerId = null,
                model = null,
                status = "Completed",
                errorSummary = null,
                createdAt = 1L,
                updatedAt = 1L,
                parentMessageId = null,
            ),
        )

        val saved = repository.getMessages(ConversationId("conversation-inline-image")).single()

        assertEquals("生成结果", saved.content)
        assertEquals(1, saved.contentParts.size)
        val image = saved.contentParts.single() as MessagePart.Image
        assertEquals("data:image/png;base64,AAAA", image.uri)
        assertEquals("image/png", image.mimeType)
    }

    @Test
    fun modelRolePreferences_storeOneModelPerProviderRole() = runTest {
        val repository = RoomModelRolePreferenceRepository(database.modelRolePreferenceDao(), clock)
        val providerId = ProviderId("provider-1")
        database.providerConfigDao().upsertProvider(providerConfig(providerId).toEntity(clock.instant(), clock.instant()))

        repository.setRoleModel(providerId, ModelRole.Chat, "chat-model")
        repository.setRoleModel(providerId, ModelRole.Image, "image-model")
        repository.setRoleModel(providerId, ModelRole.Chat, "updated-chat-model")

        val preferences = repository.observeAllRolePreferences().first().filter { it.providerId == providerId }
        assertEquals(2, preferences.size)
        assertEquals("updated-chat-model", preferences.single { it.role == ModelRole.Chat }.model)
        assertEquals("image-model", preferences.single { it.role == ModelRole.Image }.model)

        repository.setRoleModel(providerId, ModelRole.Image, "")

        val updated = repository.observeAllRolePreferences().first().filter { it.providerId == providerId }
        assertNull(updated.firstOrNull { it.role == ModelRole.Image }?.model)
    }

    @Test
    fun providerConfigs_storeApiKeyRefOnlyAndDeleteSecretsWithRolePreferences() = runTest {
        val secretStore = FakeSecretStore()
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            secretStore,
            clock,
        )
        val modelRoleRepository = RoomModelRolePreferenceRepository(database.modelRolePreferenceDao(), clock)
        val providerId = ProviderId("provider-1")
        val saveProvider = SaveProviderConfigUseCase(providerRepository)

        saveProvider(
            provider = providerConfig(providerId),
            plaintextApiKey = "test-secret",
            allowInsecureHttp = false,
        )
        modelRoleRepository.setRoleModel(providerId, ModelRole.Image, "gpt-image")

        val entity = database.providerConfigDao().getProvider(providerId.value)
        val savedProvider = providerRepository.getProvider(providerId)

        requireNotNull(entity)
        requireNotNull(savedProvider)
        assertEquals("provider:provider-1:api-key", entity.apiKeyRef)
        assertEquals("test-secret", providerRepository.getApiKey(providerId))
        assertFalse(entity.headersJson.contains("test-secret"))
        assertFalse(entity.headersJson.contains("Authorization", ignoreCase = true))
        assertFalse(entity.headersJson.contains("x-secret-key", ignoreCase = true))
        assertFalse(entity.headersJson.contains("x-provider-key", ignoreCase = true))
        assertFalse(entity.headersJson.contains("cookie", ignoreCase = true))
        assertFalse(entity.headersJson.contains("x-auth-token", ignoreCase = true))
        assertTrue(entity.headersJson.contains("X-Trace"))
        assertEquals(listOf("gpt-4.1-mini"), savedProvider.models.map { it.id })

        providerRepository.deleteProvider(providerId)

        assertNull(database.providerConfigDao().getProvider(providerId.value))
        assertNull(providerRepository.getApiKey(providerId))
        assertEquals(
            emptyList<ModelRolePreference>(),
            modelRoleRepository.observeAllRolePreferences().first().filter { it.providerId == providerId },
        )
    }

    @Test
    fun providerConfigs_normalizeWhitespaceBeforePersisting() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            FakeSecretStore(),
            clock,
        )
        val providerId = ProviderId("provider-1")
        val saveProvider = SaveProviderConfigUseCase(providerRepository)

        saveProvider(
            provider = providerConfig(providerId).copy(
                name = "  OpenAI  ",
                baseUrl = " https://api.openai.com/v1/ ",
                models = listOf(ModelConfig(" gpt-4.1-mini ", " GPT-4.1 mini ", capability = null)),
                defaultModel = " gpt-4.1-mini ",
            ),
            plaintextApiKey = " test-secret ",
            allowInsecureHttp = false,
        )

        val savedProvider = requireNotNull(providerRepository.getProvider(providerId))
        assertEquals("OpenAI", savedProvider.name)
        assertEquals("https://api.openai.com/v1", savedProvider.baseUrl)
        assertEquals(listOf("gpt-4.1-mini"), savedProvider.models.map { it.id })
        assertEquals(listOf("GPT-4.1 mini"), savedProvider.models.map { it.displayName })
        assertEquals("gpt-4.1-mini", savedProvider.defaultModel)
        assertEquals("test-secret", providerRepository.getApiKey(providerId))
    }

    @Test
    fun providerConfigs_reportRecoverableSecretFailure() = runTest {
        val secretStore = FakeSecretStore()
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            secretStore,
            clock,
        )
        val providerId = ProviderId("provider-1")
        val saveProvider = SaveProviderConfigUseCase(providerRepository)

        saveProvider(
            provider = providerConfig(providerId),
            plaintextApiKey = "test-secret",
            allowInsecureHttp = false,
        )
        secretStore.failOnGet = true

        val error = runCatching { providerRepository.getApiKey(providerId) }.exceptionOrNull()

        require(error is SecretStoreException)
        assertEquals("API Key 解密失败，请重新保存模型连接中的 API Key。", error.message)
    }

    @Test
    fun providerConfigs_deleteProviderKeepsDatabaseDeleteWhenSecretCleanupFails() = runTest {
        val secretStore = FakeSecretStore()
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            secretStore,
            clock,
        )
        val providerId = ProviderId("provider-1")
        val saveProvider = SaveProviderConfigUseCase(providerRepository)
        saveProvider(providerConfig(providerId), plaintextApiKey = "test-secret", allowInsecureHttp = false)
        secretStore.failOnDelete = true

        providerRepository.deleteProvider(providerId)

        assertNull(database.providerConfigDao().getProvider(providerId.value))
        assertEquals(listOf("provider:provider-1:api-key"), secretStore.deleteAttempts)
    }

    @Test
    fun sendMessageUseCase_streamsAndPersistsAssistantMessage() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val conversation = CreateConversationUseCase(repository, clock)(title = "Chat")
        val assistant = message(conversation.id).copy(
            id = MessageId("assistant-1"),
            role = MessageRole.Assistant,
            content = "",
            contentParts = emptyList(),
            status = MessageStatus.Pending,
        )
        val useCase = SendMessageUseCase(
            conversationRepository = repository,
            chatProvider = FakeChatProvider(
                ProviderStreamEvent.TextDelta("Hel"),
                ProviderStreamEvent.TextDelta("lo"),
                ProviderStreamEvent.Completed,
            ),
            clock = clock,
        )

        val states = useCase(assistant, chatProviderRequest()).toList()
        val saved = repository.getMessages(conversation.id).last()

        assertEquals(MessageStatus.Completed, states.last().status)
        assertEquals("Hello", states.last().content)
        assertEquals(saved, states.last())
    }

    @Test
    fun sendMessageUseCase_persistsFailedAssistantWhenProviderThrows() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val conversation = CreateConversationUseCase(repository, clock)(title = "Chat")
        val assistant = message(conversation.id).copy(
            id = MessageId("assistant-1"),
            role = MessageRole.Assistant,
            content = "",
            contentParts = emptyList(),
            status = MessageStatus.Pending,
        )
        val useCase = SendMessageUseCase(
            conversationRepository = repository,
            chatProvider = ThrowingChatProvider(),
            clock = clock,
        )

        val states = useCase(assistant, chatProviderRequest()).toList()
        val saved = repository.getMessages(conversation.id).last()

        assertEquals(MessageStatus.Failed, states.last().status)
        assertEquals(
            "Unauthorized（code: unauthorized，HTTP 401，需检查配置） 请检查 Provider、Base URL、模型和 API Key。",
            states.last().errorSummary,
        )
        assertEquals(saved, states.last())
    }

    private fun message(conversationId: ConversationId): Message =
        Message(
            id = MessageId("message-1"),
            conversationId = conversationId,
            role = MessageRole.User,
            content = "Hello",
            contentParts = listOf(MessagePart.Text("Hello")),
            providerId = ProviderId("provider-1"),
            model = "model-a",
            status = MessageStatus.Completed,
            errorSummary = null,
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            parentMessageId = null,
        )

    private fun providerConfig(providerId: ProviderId): ProviderConfig =
        ProviderConfig(
            id = providerId,
            name = "OpenAI",
            type = ProviderType.OpenAI,
            baseUrl = "https://api.openai.com/v1",
            apiKeyRef = null,
            headers = mapOf(
                "Authorization" to "Bearer test-secret",
                "x-secret-key" to "secret-value",
                "x-provider-key" to "provider-secret",
                "cookie" to "cookie-secret",
                "x-auth-token" to "auth-token-secret",
                "X-Trace" to "phase2",
            ),
            models = listOf(ModelConfig("gpt-4.1-mini", "GPT-4.1 mini", capability = null)),
            defaultModel = "gpt-4.1-mini",
            enabled = true,
        )

    private class FakeSecretStore : SecretStore {
        private val values = mutableMapOf<String, String>()
        val deleteAttempts = mutableListOf<String>()
        var failOnGet: Boolean = false
        var failOnDelete: Boolean = false

        override suspend fun putSecret(ref: String, value: String) {
            values[ref] = value
        }

        override suspend fun getSecret(ref: String): String? {
            if (failOnGet) throw SecretStoreException("corrupted")
            return values[ref]
        }

        override suspend fun deleteSecret(ref: String) {
            deleteAttempts += ref
            if (failOnDelete) throw SecretStoreException("delete failed")
            values.remove(ref)
        }
    }

    private fun chatProviderRequest(): ChatProviderRequest =
        ChatProviderRequest(
            provider = providerConfig(ProviderId("provider-1")),
            apiKey = "test-key",
            model = "gpt-4.1-mini",
            systemPrompt = null,
            messages = emptyList(),
        )

    private class FakeChatProvider(
        private vararg val events: ProviderStreamEvent,
    ) : ChatProvider {
        override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse =
            ProviderTextResponse(content = events.filterIsInstance<ProviderStreamEvent.TextDelta>().joinToString("") { it.text })

        override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> =
            flowOf(*events)
    }

    private class ThrowingChatProvider : ChatProvider {
        override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse =
            ProviderTextResponse(content = "")

        override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> =
            flow {
                throw ProviderHttpException(
                    ProviderError(
                        code = "unauthorized",
                        message = "Unauthorized",
                        statusCode = 401,
                        retryable = false,
                    ),
                )
            }
    }
}
