package com.aichat.workbench.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.data.backup.AppBackupService
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.data.repository.RoomConversationRepository
import com.aichat.workbench.data.repository.RoomPromptPresetRepository
import com.aichat.workbench.data.repository.RoomProviderConfigRepository
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
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.StoredImagePaths
import com.aichat.workbench.domain.usecase.CreateConversationUseCase
import com.aichat.workbench.domain.usecase.SavePromptPresetUseCase
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiChatBackupServiceTest {
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
        database.imageGenerationDao().upsertImageGeneration(imageGeneration().toEntity())

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

        override suspend fun deleteImage(id: ImageGenerationId) = Unit

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
}
