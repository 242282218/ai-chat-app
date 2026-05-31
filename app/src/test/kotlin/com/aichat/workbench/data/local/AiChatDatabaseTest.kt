package com.aichat.workbench.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.data.backup.AppBackupService
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.data.repository.RoomConversationRepository
import com.aichat.workbench.data.repository.RoomModelPreferenceRepository
import com.aichat.workbench.data.repository.RoomPromptPresetRepository
import com.aichat.workbench.data.repository.RoomProviderConfigRepository
import com.aichat.workbench.data.repository.RoomToolInvocationRepository
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelPreference
import com.aichat.workbench.domain.model.ModelPreferenceId
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.StoredImagePaths
import com.aichat.workbench.domain.usecase.ArchiveConversationUseCase
import com.aichat.workbench.domain.usecase.CreateConversationUseCase
import com.aichat.workbench.domain.usecase.DeleteConversationUseCase
import com.aichat.workbench.domain.usecase.DeleteProviderConfigUseCase
import com.aichat.workbench.domain.usecase.RenameConversationUseCase
import com.aichat.workbench.domain.usecase.SaveMessageUseCase
import com.aichat.workbench.domain.usecase.SaveModelPreferenceUseCase
import com.aichat.workbench.domain.usecase.SavePromptPresetUseCase
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import com.aichat.workbench.domain.usecase.SendMessageUseCase
import com.aichat.workbench.domain.usecase.SetDefaultModelUseCase
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
    fun conversations_canBeCreatedRenamedArchivedAndDeleted() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val createConversation = CreateConversationUseCase(repository, clock)
        val renameConversation = RenameConversationUseCase(repository)
        val archiveConversation = ArchiveConversationUseCase(repository)
        val deleteConversation = DeleteConversationUseCase(repository)

        val conversation = createConversation(title = "Planning", isTemporary = true, isSensitive = true)

        assertEquals("Planning", repository.getConversation(conversation.id)?.title)
        assertEquals(true, repository.getConversation(conversation.id)?.isSensitive)
        assertEquals(1, repository.observeConversations().first().size)

        renameConversation(conversation.id, "Renamed")
        assertEquals("Renamed", repository.getConversation(conversation.id)?.title)

        archiveConversation(conversation.id)
        assertEquals(0, repository.observeConversations().first().size)
        assertEquals(1, repository.observeConversations(includeArchived = true).first().size)

        deleteConversation(conversation.id)
        assertNull(repository.getConversation(conversation.id))
    }

    @Test
    fun messages_areStoredByConversationAndCascadeWhenConversationDeleted() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val saveMessage = SaveMessageUseCase(repository)
        val conversation = CreateConversationUseCase(repository, clock)(title = "Chat")
        val message = message(conversation.id)

        saveMessage(message)

        assertEquals(listOf(message), repository.getMessages(conversation.id))

        DeleteConversationUseCase(repository)(conversation.id)
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
    fun promptPresets_areStoredAndObserved() = runTest {
        val repository = RoomPromptPresetRepository(database.promptPresetDao())
        val savePromptPreset = SavePromptPresetUseCase(repository)
        val preset = PromptPreset(
            id = PromptPresetId("prompt-1"),
            name = "Writer",
            description = "Polish writing",
            systemPrompt = "Improve clarity.",
            defaultModel = "gpt-4.1-mini",
            defaultToolNames = listOf("web_search"),
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
        )

        savePromptPreset(preset)

        assertEquals(preset, repository.getPromptPreset(preset.id))
        assertEquals(listOf(preset), repository.observePromptPresets().first())
    }

    @Test
    fun modelPreferences_storeFavoritesAndKeepOneDefaultPerProvider() = runTest {
        val repository = RoomModelPreferenceRepository(database.modelPreferenceDao(), clock)
        val saveModelPreference = SaveModelPreferenceUseCase(repository)
        val setDefaultModel = SetDefaultModelUseCase(repository)
        val providerId = ProviderId("provider-1")

        saveModelPreference(modelPreference(providerId, "model-a", favorite = true))
        saveModelPreference(modelPreference(providerId, "model-b", favorite = true))
        setDefaultModel(providerId, "model-a")
        setDefaultModel(providerId, "model-b")

        val preferences = repository.observeModelPreferences(providerId).first()
        assertEquals(2, preferences.size)
        assertEquals(listOf("model-b"), preferences.filter { it.isDefault }.map { it.model })
        assertEquals(setOf("model-a", "model-b"), preferences.filter { it.isFavorite }.map { it.model }.toSet())
    }

    @Test
    fun providerConfigs_storeApiKeyRefOnlyAndDeleteSecretsWithPreferences() = runTest {
        val secretStore = FakeSecretStore()
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            secretStore,
            clock,
        )
        val modelRepository = RoomModelPreferenceRepository(database.modelPreferenceDao(), clock)
        val providerId = ProviderId("provider-1")
        val saveProvider = SaveProviderConfigUseCase(providerRepository)
        val deleteProvider = DeleteProviderConfigUseCase(providerRepository)

        saveProvider(
            provider = providerConfig(providerId),
            plaintextApiKey = "test-secret",
            allowInsecureHttp = false,
        )
        SaveModelPreferenceUseCase(modelRepository)(modelPreference(providerId, "gpt-4.1-mini", favorite = true))

        val entity = database.providerConfigDao().getProvider(providerId.value)
        val savedProvider = providerRepository.getProvider(providerId)

        requireNotNull(entity)
        requireNotNull(savedProvider)
        assertEquals("provider:provider-1:api-key", entity.apiKeyRef)
        assertEquals("test-secret", providerRepository.getApiKey(providerId))
        assertFalse(entity.headersJson.contains("test-secret"))
        assertFalse(entity.headersJson.contains("Authorization", ignoreCase = true))
        assertEquals(listOf("gpt-4.1-mini"), savedProvider.models.map { it.id })

        deleteProvider(providerId)

        assertNull(database.providerConfigDao().getProvider(providerId.value))
        assertNull(providerRepository.getApiKey(providerId))
        assertEquals(emptyList<ModelPreference>(), modelRepository.observeModelPreferences(providerId).first())
    }

    @Test
    fun toolResults_areStoredWithoutConversation() = runTest {
        val repository = RoomToolInvocationRepository(database.toolInvocationDao())
        val result = ToolResult(
            id = ToolCallId("tool-call-1"),
            toolName = "web_search",
            permissionLevel = ToolPermissionLevel.Network,
            inputSummary = "query: AI news",
            output = ToolOutput.Json(
                """{"query":"AI news","fetchedAt":"2026-05-31T00:00:00Z","results":[]}""",
            ),
            status = ToolStatus.Completed,
            startedAt = clock.instant(),
            finishedAt = clock.instant().plusSeconds(1),
            error = null,
        )

        repository.saveToolResult(conversationId = null, toolResult = result)

        assertEquals(listOf(result), repository.observeToolInvocations().first())
    }

    @Test
    fun backupExport_omitsApiKeysAndSensitiveOrTemporaryChats() = runTest {
        val secretStore = FakeSecretStore()
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            secretStore,
            clock,
        )
        val conversationRepository = RoomConversationRepository(database.conversationDao(), clock)
        val service = AppBackupService(
            database = database,
            providerRepository = providerRepository,
            conversationRepository = conversationRepository,
            imageStorage = FakeImageStorage(),
            clock = clock,
        )
        SaveProviderConfigUseCase(providerRepository)(
            provider = providerConfig(ProviderId("provider-1")),
            plaintextApiKey = "test-secret",
            allowInsecureHttp = false,
        )
        val createConversation = CreateConversationUseCase(conversationRepository, clock)
        val normal = createConversation(title = "Normal chat")
        val temporary = createConversation(title = "Temporary chat", isTemporary = true)
        val sensitive = createConversation(title = "Sensitive chat", isSensitive = true)
        conversationRepository.saveMessage(message(normal.id))
        conversationRepository.saveMessage(message(temporary.id).copy(id = MessageId("message-2"), conversationId = temporary.id))
        conversationRepository.saveMessage(message(sensitive.id).copy(id = MessageId("message-3"), conversationId = sensitive.id))

        val json = service.exportJson(includeChats = true)

        assertFalse(json.contains("test-secret"))
        assertFalse(json.contains("apiKeyRef"))
        assertEquals(true, json.contains("Normal chat"))
        assertFalse(json.contains("Temporary chat"))
        assertFalse(json.contains("Sensitive chat"))
    }

    @Test
    fun backupImport_providerRequiresApiKeyToBeEnteredAgain() = runTest {
        val secretStore = FakeSecretStore()
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            secretStore,
            clock,
        )
        val service = AppBackupService(
            database = database,
            providerRepository = providerRepository,
            conversationRepository = RoomConversationRepository(database.conversationDao(), clock),
            imageStorage = FakeImageStorage(),
            clock = clock,
        )
        val backupJson = """
            {
              "version": 1,
              "providers": [
                {
                  "id": "provider-1",
                  "name": "OpenAI",
                  "type": "OpenAI",
                  "baseUrl": "https://api.openai.com/v1",
                  "headers": {},
                  "models": [],
                  "defaultModel": null,
                  "enabled": true,
                  "apiKeyRef": "must-not-import"
                }
              ],
              "prompts": [],
              "modelPreferences": [],
              "conversations": []
            }
        """.trimIndent()

        service.importJson(backupJson)

        assertNull(database.providerConfigDao().getProvider("provider-1")?.apiKeyRef)
        assertNull(providerRepository.getApiKey(ProviderId("provider-1")))
    }

    @Test
    fun backupClearAll_deletesRowsSecretsAndImages() = runTest {
        val secretStore = FakeSecretStore()
        val imageStorage = FakeImageStorage()
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            secretStore,
            clock,
        )
        val promptRepository = RoomPromptPresetRepository(database.promptPresetDao())
        val conversationRepository = RoomConversationRepository(database.conversationDao(), clock)
        val service = AppBackupService(
            database = database,
            providerRepository = providerRepository,
            conversationRepository = conversationRepository,
            imageStorage = imageStorage,
            clock = clock,
        )
        SaveProviderConfigUseCase(providerRepository)(
            provider = providerConfig(ProviderId("provider-1")),
            plaintextApiKey = "test-secret",
            allowInsecureHttp = false,
        )
        SavePromptPresetUseCase(promptRepository)(
            PromptPreset(
                id = PromptPresetId("prompt-1"),
                name = "Writer",
                description = null,
                systemPrompt = "Improve clarity.",
                defaultModel = null,
                defaultToolNames = emptyList(),
                createdAt = clock.instant(),
                updatedAt = clock.instant(),
            ),
        )
        CreateConversationUseCase(conversationRepository, clock)(title = "Chat")
        database.imageGenerationDao().upsertImageGeneration(
            ImageGeneration(
                id = ImageGenerationId("image-1"),
                conversationId = null,
                prompt = "A test image",
                providerId = ProviderId("provider-1"),
                model = "gpt-image-1",
                size = null,
                quality = null,
                count = 1,
                originalPath = "/tmp/original.png",
                thumbnailPath = "/tmp/thumb.png",
                status = ImageGenerationStatus.Completed,
                errorSummary = null,
                createdAt = clock.instant(),
            ).toEntity(),
        )

        service.clearAllData()

        assertEquals(emptyList<ProviderConfig>(), providerRepository.observeProviders().first())
        assertEquals(emptyList<PromptPreset>(), promptRepository.observePromptPresets().first())
        assertEquals(0, conversationRepository.observeConversations(includeArchived = true).first().size)
        assertEquals(0, database.imageGenerationDao().observeImageGenerations().first().size)
        assertEquals(true, imageStorage.deleted)
        assertNull(secretStore.getSecret("provider:provider-1:api-key"))
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
        assertEquals("Unauthorized", states.last().errorSummary)
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
            toolCallId = null,
            parentMessageId = null,
        )

    private fun modelPreference(
        providerId: ProviderId,
        model: String,
        favorite: Boolean,
    ): ModelPreference =
        ModelPreference(
            id = ModelPreferenceId("${providerId.value}:$model"),
            providerId = providerId,
            model = model,
            isFavorite = favorite,
            isDefault = false,
            capability = null,
            updatedAt = clock.instant(),
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
                "X-Trace" to "phase2",
            ),
            models = listOf(ModelConfig("gpt-4.1-mini", "GPT-4.1 mini", capability = null)),
            defaultModel = "gpt-4.1-mini",
            enabled = true,
        )

    private class FakeSecretStore : SecretStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun putSecret(ref: String, value: String) {
            values[ref] = value
        }

        override suspend fun getSecret(ref: String): String? =
            values[ref]

        override suspend fun deleteSecret(ref: String) {
            values.remove(ref)
        }
    }

    private class FakeImageStorage : ImageStorage {
        var deleted: Boolean = false

        override suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths =
            StoredImagePaths(
                originalPath = "original/${id.value}.png",
                thumbnailPath = "thumb/${id.value}.png",
            )

        override suspend fun deleteAllImages() {
            deleted = true
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
