package com.aichat.workbench.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.crypto.SecretStoreException
import com.aichat.workbench.data.local.AiChatDatabase
import com.aichat.workbench.data.local.dao.ProviderConfigDao
import com.aichat.workbench.data.local.entity.ProviderConfigEntity
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomProviderConfigRepositoryTest {
    private lateinit var database: AiChatDatabase
    private val clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AiChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun saveProvider_keepsOldApiKeyWhenDatabaseUpdateFails() = runTest {
        val oldRef = "provider:provider-1:api-key"
        val secretStore = RecordingSecretStore(
            initialValues = mutableMapOf(oldRef to "old-secret"),
        )
        val repository = RoomProviderConfigRepository(
            providerDao = FailingUpsertProviderDao(
                existing = providerEntity(apiKeyRef = oldRef),
            ),
            secretStore = secretStore,
            clock = clock,
        )
        val saveProvider = SaveProviderConfigUseCase(repository)
        val providerId = ProviderId("provider-1")

        val error = runCatching {
            saveProvider(
                provider(providerId).copy(name = "Updated"),
                plaintextApiKey = "new-secret",
                allowInsecureHttp = false,
            )
        }.exceptionOrNull()

        require(error != null)
        assertEquals("old-secret", secretStore.getSecret(oldRef))
        assertTrue(secretStore.deleteAttempts.any { it != oldRef })
        assertNull(secretStore.values.values.firstOrNull { it == "new-secret" })
    }

    @Test
    fun observeProviders_ignoresMalformedHeaderAndModelJson() = runTest {
        database.providerConfigDao().upsertProvider(
            ProviderConfigEntity(
                id = "provider-1",
                name = "Broken Provider",
                type = "openai_compatible",
                baseUrl = "https://example.test/v1",
                apiKeyRef = null,
                headersJson = "{bad json",
                modelsJson = "{bad json",
                defaultModel = null,
                enabled = true,
                createdAt = 1L,
                updatedAt = 2L,
            ),
        )
        val repository = RoomProviderConfigRepository(
            providerDao = database.providerConfigDao(),
            secretStore = RecordingSecretStore(),
            clock = clock,
        )

        val saved = repository.observeProviders().first().single()

        assertEquals("Broken Provider", saved.name)
        assertEquals(emptyMap<String, String>(), saved.headers)
        assertEquals(emptyList<ModelConfig>(), saved.models)
    }

    @Test
    fun saveProvider_keepsSuccessfulDatabaseUpdateWhenOldSecretCleanupFails() = runTest {
        val oldRef = "provider:provider-1:api-key"
        val secretStore = RecordingSecretStore(
            initialValues = mutableMapOf(oldRef to "old-secret"),
            failDeleteRefs = setOf(oldRef),
        )
        val repository = RoomProviderConfigRepository(
            providerDao = database.providerConfigDao(),
            secretStore = secretStore,
            clock = clock,
        )
        val saveProvider = SaveProviderConfigUseCase(repository)
        val providerId = ProviderId("provider-1")
        saveProvider(
            provider = provider(providerId),
            plaintextApiKey = "old-secret",
            allowInsecureHttp = false,
        )

        saveProvider(
            provider = provider(providerId).copy(name = "Updated"),
            plaintextApiKey = "new-secret",
            allowInsecureHttp = false,
        )

        val saved = requireNotNull(repository.getProvider(providerId))
        assertEquals("Updated", saved.name)
        assertEquals("new-secret", repository.getApiKey(providerId))
        assertTrue(secretStore.deleteAttempts.contains(oldRef))
    }

    private fun provider(id: ProviderId): ProviderConfig =
        ProviderConfig(
            id = id,
            name = "Provider",
            type = ProviderType.OpenAICompatible,
            baseUrl = "https://example.test/v1",
            apiKeyRef = null,
            headers = emptyMap(),
            models = listOf(ModelConfig("model-a", "Model A", capability = null)),
            defaultModel = "model-a",
            enabled = true,
        )

    private fun providerEntity(apiKeyRef: String?): ProviderConfigEntity =
        ProviderConfigEntity(
            id = "provider-1",
            name = "Provider",
            type = "openai_compatible",
            baseUrl = "https://example.test/v1",
            apiKeyRef = apiKeyRef,
            headersJson = "{}",
            modelsJson = """[{"id":"model-a","displayName":"Model A"}]""",
            defaultModel = "model-a",
            enabled = true,
            createdAt = 1L,
            updatedAt = 2L,
        )

    private class FailingUpsertProviderDao(
        private val existing: ProviderConfigEntity,
    ) : ProviderConfigDao {
        override fun observeProviders(): Flow<List<ProviderConfigEntity>> = flowOf(listOf(existing))

        override suspend fun getProvider(id: String): ProviderConfigEntity? = existing.takeIf { it.id == id }

        override suspend fun upsertProvider(provider: ProviderConfigEntity) {
            error("database unavailable")
        }

        override suspend fun deleteProvider(id: String) = Unit

        override suspend fun deleteAllProviders() = Unit
    }

    private class RecordingSecretStore(
        val values: MutableMap<String, String> = mutableMapOf(),
        initialValues: MutableMap<String, String> = mutableMapOf(),
        private val failDeleteRefs: Set<String> = emptySet(),
    ) : SecretStore {
        val deleteAttempts = mutableListOf<String>()

        init {
            values.putAll(initialValues)
        }

        override suspend fun putSecret(ref: String, value: String) {
            values[ref] = value
        }

        override suspend fun getSecret(ref: String): String? = values[ref]

        override suspend fun deleteSecret(ref: String) {
            deleteAttempts += ref
            if (ref in failDeleteRefs) throw SecretStoreException("delete failed")
            values.remove(ref) ?: throw SecretStoreException("missing")
        }
    }
}
