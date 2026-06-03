package com.aichat.workbench.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.aichat.workbench.data.backup.AppBackupService
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.data.repository.RoomConversationRepository
import com.aichat.workbench.data.repository.RoomModelPreferenceRepository
import com.aichat.workbench.data.repository.RoomPromptPresetRepository
import com.aichat.workbench.data.repository.RoomProviderConfigRepository
import com.aichat.workbench.data.repository.RoomToolInvocationRepository
import com.aichat.workbench.domain.model.Conversation
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
import com.aichat.workbench.domain.repository.ConversationRepository
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
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiChatDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AiChatDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

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
    fun searchMessages_returnsMatchingMessagesWithConversation() = runTest {
        val repository = RoomConversationRepository(database.conversationDao(), clock)
        val first = CreateConversationUseCase(repository, clock)(title = "Research")
        val second = CreateConversationUseCase(repository, clock)(title = "Notes")
        repository.saveMessage(message(first.id).copy(id = MessageId("message-1"), content = "AI search needle"))
        repository.saveMessage(message(second.id).copy(id = MessageId("message-2"), content = "unrelated text"))

        val results = repository.searchMessages("needle").first()

        assertEquals(1, results.size)
        assertEquals(first.id, results.single().conversation.id)
        assertEquals("AI search needle", results.single().message.content)
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
        assertFalse(entity.headersJson.contains("anthropic-api-key", ignoreCase = true))
        assertFalse(entity.headersJson.contains("x-goog-api-key", ignoreCase = true))
        assertFalse(entity.headersJson.contains("cookie", ignoreCase = true))
        assertFalse(entity.headersJson.contains("x-auth-token", ignoreCase = true))
        assertTrue(entity.headersJson.contains("X-Trace"))
        assertEquals(listOf("gpt-4.1-mini"), savedProvider.models.map { it.id })

        deleteProvider(providerId)

        assertNull(database.providerConfigDao().getProvider(providerId.value))
        assertNull(providerRepository.getApiKey(providerId))
        assertEquals(emptyList<ModelPreference>(), modelRepository.observeModelPreferences(providerId).first())
    }

    @Test
    fun providerConfigs_normalizeWhitespaceBeforePersisting() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
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
        assertFalse(json.contains("anthropic-secret"))
        assertFalse(json.contains("goog-secret"))
        assertFalse(json.contains("cookie-secret"))
        assertFalse(json.contains("auth-token-secret"))
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
    fun backupImport_replacesExistingProviderWithoutReusingStoredApiKey() = runTest {
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
        val providerId = ProviderId("provider-1")
        SaveProviderConfigUseCase(providerRepository)(
            provider = providerConfig(providerId),
            plaintextApiKey = "test-secret",
            allowInsecureHttp = false,
        )
        val backupJson = """
            {
              "version": 1,
              "providers": [
                {
                  "id": "provider-1",
                  "name": "Imported Provider",
                  "type": "openai",
                  "baseUrl": "https://imported.example/v1",
                  "headers": {},
                  "models": [],
                  "enabled": true
                }
              ],
              "prompts": [],
              "modelPreferences": [],
              "conversations": []
            }
        """.trimIndent()

        service.importJson(backupJson)

        val imported = requireNotNull(database.providerConfigDao().getProvider("provider-1"))
        assertEquals("https://imported.example/v1", imported.baseUrl)
        assertNull(imported.apiKeyRef)
        assertNull(providerRepository.getApiKey(providerId))
        assertNull(secretStore.getSecret("provider:provider-1:api-key"))
    }

    @Test
    fun backupImport_rejectsInvalidProviderBaseUrlBeforeWritingRows() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            FakeSecretStore(),
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
                  "name": "Invalid Provider",
                  "type": "openai",
                  "baseUrl": "file:///tmp/provider",
                  "headers": {},
                  "models": [],
                  "enabled": true
                }
              ],
              "prompts": [],
              "modelPreferences": [],
              "conversations": []
            }
        """.trimIndent()

        val error = assertFailsWith<IllegalArgumentException> {
            service.importJson(backupJson)
        }

        assertTrue(error.message.orEmpty().contains("模型连接 URL 无效"))
        assertNull(database.providerConfigDao().getProvider("provider-1"))
        assertEquals(emptyList<ProviderConfig>(), providerRepository.observeProviders().first())
    }

    @Test
    fun backupImport_rejectsDuplicateProviderModelsBeforeWritingRows() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            FakeSecretStore(),
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
                  "type": "openai",
                  "baseUrl": "https://api.openai.com/v1",
                  "headers": {},
                  "models": [
                    {"id": " gpt-test ", "displayName": "GPT test"},
                    {"id": "gpt-test", "displayName": "GPT duplicate"}
                  ],
                  "defaultModel": "gpt-test",
                  "enabled": true
                }
              ],
              "prompts": [],
              "modelPreferences": [],
              "conversations": []
            }
        """.trimIndent()

        val error = assertFailsWith<IllegalArgumentException> {
            service.importJson(backupJson)
        }

        assertTrue(error.message.orEmpty().contains("模型名称 重复"))
        assertNull(database.providerConfigDao().getProvider("provider-1"))
    }

    @Test
    fun backupImport_rejectsMissingProviderDefaultModelBeforeWritingRows() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            FakeSecretStore(),
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
                  "type": "openai",
                  "baseUrl": "https://api.openai.com/v1",
                  "headers": {},
                  "models": [
                    {"id": "gpt-test", "displayName": "GPT test"}
                  ],
                  "defaultModel": "missing-model",
                  "enabled": true
                }
              ],
              "prompts": [],
              "modelPreferences": [],
              "conversations": []
            }
        """.trimIndent()

        val error = assertFailsWith<IllegalArgumentException> {
            service.importJson(backupJson)
        }

        assertTrue(error.message.orEmpty().contains("默认模型不在模型列表中"))
        assertNull(database.providerConfigDao().getProvider("provider-1"))
    }

    @Test
    fun backupImport_normalizesProviderBaseUrl() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            FakeSecretStore(),
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
                  "name": "Ollama",
                  "type": "ollama",
                  "baseUrl": " http://10.0.2.2:11434/ ",
                  "headers": {},
                  "models": [],
                  "enabled": true
                }
              ],
              "prompts": [],
              "modelPreferences": [],
              "conversations": []
            }
        """.trimIndent()

        service.importJson(backupJson)

        assertEquals("http://10.0.2.2:11434", database.providerConfigDao().getProvider("provider-1")?.baseUrl)
    }

    @Test
    fun backupImport_rollsBackProviderRowsWhenConversationWriteFails() = runTest {
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
            conversationRepository = FailingConversationRepository(),
            imageStorage = FakeImageStorage(),
            clock = clock,
        )
        SaveProviderConfigUseCase(providerRepository)(
            provider = providerConfig(ProviderId("provider-1")),
            plaintextApiKey = "test-secret",
            allowInsecureHttp = false,
        )
        val backupJson = """
            {
              "version": 1,
              "providers": [
                {
                  "id": "provider-1",
                  "name": "Imported Provider",
                  "type": "openai",
                  "baseUrl": "https://imported.example/v1",
                  "headers": {},
                  "models": [],
                  "enabled": true
                },
                {
                  "id": "provider-2",
                  "name": "New Provider",
                  "type": "openai",
                  "baseUrl": "https://new.example/v1",
                  "headers": {},
                  "models": [],
                  "enabled": true
                }
              ],
              "prompts": [],
              "modelPreferences": [],
              "conversations": [
                {
                  "id": "conversation-1",
                  "title": "Imported chat",
                  "messages": []
                }
              ]
            }
        """.trimIndent()

        val error = assertFailsWith<IllegalStateException> {
            service.importJson(backupJson)
        }

        assertTrue(error.message.orEmpty().contains("conversation import failed"))
        val existing = requireNotNull(database.providerConfigDao().getProvider("provider-1"))
        assertEquals("https://api.openai.com/v1", existing.baseUrl)
        assertEquals("provider:provider-1:api-key", existing.apiKeyRef)
        assertEquals("test-secret", providerRepository.getApiKey(ProviderId("provider-1")))
        assertNull(database.providerConfigDao().getProvider("provider-2"))
    }

    @Test
    fun backupImport_rejectsOversizedProviderHeaderBeforeWritingRows() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            FakeSecretStore(),
            clock,
        )
        val service = AppBackupService(
            database = database,
            providerRepository = providerRepository,
            conversationRepository = RoomConversationRepository(database.conversationDao(), clock),
            imageStorage = FakeImageStorage(),
            clock = clock,
        )
        val headerValue = "x".repeat(513)
        val backupJson = """
            {
              "version": 1,
              "providers": [
                {
                  "id": "provider-1",
                  "name": "OpenAI",
                  "type": "OpenAI",
                  "baseUrl": "https://api.openai.com/v1",
                  "headers": {"X-Trace": "$headerValue"},
                  "models": [],
                  "enabled": true
                }
              ],
              "prompts": [],
              "modelPreferences": [],
              "conversations": []
            }
        """.trimIndent()

        val error = assertFailsWith<IllegalArgumentException> {
            service.importJson(backupJson)
        }

        assertTrue(error.message.orEmpty().contains("Header 值 超过"))
        assertNull(database.providerConfigDao().getProvider("provider-1"))
    }

    @Test
    fun backupImportPreview_reportsCountsWithoutWritingRows() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            FakeSecretStore(),
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
                  "enabled": true
                }
              ],
              "prompts": [
                {
                  "id": "prompt-1",
                  "name": "Writer",
                  "systemPrompt": "Improve clarity."
                }
              ],
              "modelPreferences": [],
              "conversations": [
                {
                  "id": "conversation-1",
                  "title": "Imported chat",
                  "messages": [
                    {
                      "id": "message-1",
                      "role": "User",
                      "content": "Hello",
                      "status": "Completed"
                    },
                    {
                      "id": "message-2",
                      "role": "Assistant",
                      "content": "Hi",
                      "status": "Completed"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val summary = service.previewImportJson(backupJson)

        assertEquals(1, summary.providers)
        assertEquals(1, summary.prompts)
        assertEquals(0, summary.modelPreferences)
        assertEquals(1, summary.conversations)
        assertEquals(2, summary.messages)
        assertEquals(emptyList<ProviderConfig>(), providerRepository.observeProviders().first())
    }

    @Test
    fun backupImport_rejectsDuplicateModelPreferenceBeforeWritingRows() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            FakeSecretStore(),
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
                  "type": "openai",
                  "baseUrl": "https://api.openai.com/v1",
                  "headers": {},
                  "models": [],
                  "enabled": true
                }
              ],
              "prompts": [],
              "modelPreferences": [
                {
                  "id": "preference-1",
                  "providerId": "provider-1",
                  "model": "gpt-4.1-mini"
                },
                {
                  "id": "preference-2",
                  "providerId": "provider-1",
                  "model": "gpt-4.1-mini"
                }
              ],
              "conversations": []
            }
        """.trimIndent()

        val error = assertFailsWith<IllegalArgumentException> {
            service.importJson(backupJson)
        }

        assertTrue(error.message.orEmpty().contains("模型偏好 Provider/模型 重复"))
        assertNull(database.providerConfigDao().getProvider("provider-1"))
        assertEquals(emptyList<ProviderConfig>(), providerRepository.observeProviders().first())
    }

    @Test
    fun backupImport_rejectsDuplicateMessageIdsBeforeWritingRows() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            FakeSecretStore(),
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
        val backupJson = """
            {
              "version": 1,
              "providers": [],
              "prompts": [],
              "modelPreferences": [],
              "conversations": [
                {
                  "id": "conversation-1",
                  "title": "Imported chat 1",
                  "messages": [
                    {
                      "id": "message-1",
                      "role": "User",
                      "content": "Hello",
                      "status": "Completed"
                    }
                  ]
                },
                {
                  "id": "conversation-2",
                  "title": "Imported chat 2",
                  "messages": [
                    {
                      "id": "message-1",
                      "role": "Assistant",
                      "content": "Hi",
                      "status": "Completed"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val error = assertFailsWith<IllegalArgumentException> {
            service.importJson(backupJson)
        }

        assertTrue(error.message.orEmpty().contains("消息 ID 重复"))
        assertEquals(0, conversationRepository.observeConversations(includeArchived = true).first().size)
    }

    @Test
    fun backupImport_rejectsOversizedMessageContentBeforeWritingRows() = runTest {
        val providerRepository = RoomProviderConfigRepository(
            database.providerConfigDao(),
            database.modelPreferenceDao(),
            FakeSecretStore(),
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
        val messageContent = "x".repeat(120_001)
        val backupJson = """
            {
              "version": 1,
              "providers": [],
              "prompts": [],
              "modelPreferences": [],
              "conversations": [
                {
                  "id": "conversation-1",
                  "title": "Imported chat",
                  "messages": [
                    {
                      "id": "message-1",
                      "role": "User",
                      "content": "$messageContent",
                      "status": "Completed"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val error = assertFailsWith<IllegalArgumentException> {
            service.importJson(backupJson)
        }

        assertTrue(error.message.orEmpty().contains("消息内容 超过"))
        assertEquals(0, conversationRepository.observeConversations(includeArchived = true).first().size)
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
    fun backupClearPromptsModelsAndImages_keepsRowsWhenImageDeleteFails() = runTest {
        val imageStorage = FakeImageStorage(failOnDelete = true)
        val promptRepository = RoomPromptPresetRepository(database.promptPresetDao())
        val service = AppBackupService(
            database = database,
            providerRepository = RoomProviderConfigRepository(
                database.providerConfigDao(),
                database.modelPreferenceDao(),
                FakeSecretStore(),
                clock,
            ),
            conversationRepository = RoomConversationRepository(database.conversationDao(), clock),
            imageStorage = imageStorage,
            clock = clock,
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
        database.modelPreferenceDao().upsertModelPreference(
            modelPreference(ProviderId("provider-1"), "gpt-4.1-mini", favorite = true).toEntity(),
        )
        database.imageGenerationDao().upsertImageGeneration(imageGeneration().toEntity())

        val error = assertFailsWith<IllegalStateException> {
            service.clearPromptsModelsAndImages()
        }

        assertTrue(error.message.orEmpty().contains("image delete failed"))
        assertEquals(1, promptRepository.observePromptPresets().first().size)
        assertEquals(1, database.modelPreferenceDao().getAllModelPreferences().size)
        assertEquals(1, database.imageGenerationDao().observeImageGenerations().first().size)
    }

    @Test
    fun backupClearAll_keepsRowsSecretsAndImagesWhenImageDeleteFails() = runTest {
        val secretStore = FakeSecretStore()
        val imageStorage = FakeImageStorage(failOnDelete = true)
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
        database.imageGenerationDao().upsertImageGeneration(imageGeneration().toEntity())

        val error = assertFailsWith<IllegalStateException> {
            service.clearAllData()
        }

        assertTrue(error.message.orEmpty().contains("image delete failed"))
        assertEquals(1, providerRepository.observeProviders().first().size)
        assertEquals("test-secret", providerRepository.getApiKey(ProviderId("provider-1")))
        assertEquals(1, promptRepository.observePromptPresets().first().size)
        assertEquals(1, conversationRepository.observeConversations(includeArchived = true).first().size)
        assertEquals(1, database.imageGenerationDao().observeImageGenerations().first().size)
    }

    @Test
    fun migration4To5_convertsProviderTypeNamesToStableValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("provider-type-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 4).apply {
            execSQL(
                """
                INSERT INTO provider_configs (
                    id, name, type, base_url, api_key_ref, headers_json, models_json,
                    default_model, enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "openai",
                    "OpenAI",
                    "OpenAI",
                    "https://api.openai.com/v1",
                    null,
                    "{}",
                    "[]",
                    null,
                    1,
                    1L,
                    1L,
                ),
            )
            execSQL(
                """
                INSERT INTO provider_configs (
                    id, name, type, base_url, api_key_ref, headers_json, models_json,
                    default_model, enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "compatible",
                    "Compatible",
                    "OpenAICompatible",
                    "https://example.test/v1",
                    null,
                    "{}",
                    "[]",
                    null,
                    1,
                    1L,
                    1L,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            5,
            true,
            AiChatDatabase.MIGRATION_4_5,
        )
        val values = mutableMapOf<String, String>()
        val cursor = migrated.query("SELECT id, type FROM provider_configs")
        cursor.use {
            while (it.moveToNext()) {
                values[it.getString(0)] = it.getString(1)
            }
        }
        migrated.close()

        assertEquals("openai", values["openai"])
        assertEquals("openai_compatible", values["compatible"])
    }

    @Test
    fun migration5To6_addsToolCallColumnsWithDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("message-tool-call-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary, is_sensitive, archived_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Chat",
                    1L,
                    1L,
                    null,
                    null,
                    "{}",
                    null,
                    0,
                    0,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, tool_call_id, parent_message_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "User",
                    "Hello",
                    """[{"type":"text","text":"Hello"}]""",
                    null,
                    null,
                    "Completed",
                    null,
                    1L,
                    1L,
                    null,
                    null,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            AiChatDatabase.MIGRATION_5_6,
        )
        val cursor = migrated.query("SELECT tool_calls, tool_result FROM messages WHERE id = 'message-1'")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("[]", it.getString(0))
            assertTrue(it.isNull(1))
        }
        migrated.close()
    }

    @Test
    fun migration6To7_createsMessageFtsIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = context.getDatabasePath("message-fts-migration").absolutePath
        migrationHelper.createDatabase(databaseName, 6).apply {
            execSQL(
                """
                INSERT INTO conversations (
                    id, title, created_at, updated_at, default_provider_id, default_model,
                    model_parameters_json, system_prompt, is_temporary, is_sensitive, archived_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "conversation-1",
                    "Chat",
                    1L,
                    1L,
                    null,
                    null,
                    "{}",
                    null,
                    0,
                    0,
                    null,
                ),
            )
            execSQL(
                """
                INSERT INTO messages (
                    id, conversation_id, role, content, content_parts_json, provider_id, model,
                    status, error_summary, created_at, updated_at, tool_call_id, parent_message_id,
                    tool_calls, tool_result
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "message-1",
                    "conversation-1",
                    "User",
                    "needle content",
                    """[{"type":"text","text":"needle content"}]""",
                    null,
                    null,
                    "Completed",
                    null,
                    1L,
                    1L,
                    null,
                    null,
                    "[]",
                    null,
                ),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            databaseName,
            7,
            true,
            AiChatDatabase.MIGRATION_6_7,
        )
        val cursor = migrated.query(
            """
            SELECT m.content FROM messages m
            JOIN messages_fts fts ON fts.rowid = m.rowid
            WHERE messages_fts MATCH 'needle'
            """.trimIndent(),
        )
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals("needle content", it.getString(0))
        }
        migrated.close()
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
                "anthropic-api-key" to "anthropic-secret",
                "x-goog-api-key" to "goog-secret",
                "cookie" to "cookie-secret",
                "x-auth-token" to "auth-token-secret",
                "X-Trace" to "phase2",
            ),
            models = listOf(ModelConfig("gpt-4.1-mini", "GPT-4.1 mini", capability = null)),
            defaultModel = "gpt-4.1-mini",
            enabled = true,
        )

    private fun imageGeneration(): ImageGeneration =
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

    private class FakeImageStorage(
        private val failOnDelete: Boolean = false,
    ) : ImageStorage {
        var deleted: Boolean = false

        override suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths =
            StoredImagePaths(
                originalPath = "original/${id.value}.png",
                thumbnailPath = "thumb/${id.value}.png",
            )

        override suspend fun deleteAllImages() {
            if (failOnDelete) {
                throw IllegalStateException("image delete failed")
            }
            deleted = true
        }
    }

    private class FailingConversationRepository : ConversationRepository {
        override fun observeConversations(includeArchived: Boolean): Flow<List<Conversation>> =
            flowOf(emptyList())

        override suspend fun getConversation(id: ConversationId): Conversation? =
            null

        override suspend fun saveConversation(conversation: Conversation) {
            throw IllegalStateException("conversation import failed")
        }

        override suspend fun renameConversation(id: ConversationId, title: String) = Unit

        override suspend fun archiveConversation(id: ConversationId) = Unit

        override suspend fun deleteConversation(id: ConversationId) = Unit

        override fun observeMessages(conversationId: ConversationId): Flow<List<Message>> =
            flowOf(emptyList())

        override suspend fun getMessages(conversationId: ConversationId): List<Message> =
            emptyList()

        override suspend fun saveMessage(message: Message) = Unit

        override suspend fun deleteMessages(conversationId: ConversationId) = Unit
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
