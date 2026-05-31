package com.aichat.workbench.data.repository

import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.local.dao.ModelPreferenceDao
import com.aichat.workbench.data.local.dao.ProviderConfigDao
import com.aichat.workbench.data.mapper.toDomain
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProviderConfigRepository(
    private val providerDao: ProviderConfigDao,
    private val modelPreferenceDao: ModelPreferenceDao,
    private val secretStore: SecretStore,
    private val clock: Clock,
) : ProviderConfigRepository {
    override fun observeProviders(): Flow<List<ProviderConfig>> =
        providerDao.observeProviders().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getProvider(id: ProviderId): ProviderConfig? =
        providerDao.getProvider(id.value)?.toDomain()

    override suspend fun saveProvider(provider: ProviderConfig, plaintextApiKey: String?) {
        val existing = providerDao.getProvider(provider.id.value)
        val secretRef = when {
            plaintextApiKey != null -> apiKeyRef(provider.id)
            existing?.apiKeyRef != null -> existing.apiKeyRef
            else -> provider.apiKeyRef
        }

        if (plaintextApiKey != null) {
            secretStore.putSecret(requireNotNull(secretRef), plaintextApiKey)
        }

        val now = clock.instant()
        val sanitizedProvider = provider.copy(
            apiKeyRef = secretRef,
            headers = provider.headers.withoutSensitiveHeaders(),
        )
        providerDao.upsertProvider(
            sanitizedProvider.toEntity(
                createdAt = existing?.createdAt?.let(Instant::ofEpochMilli) ?: now,
                updatedAt = now,
            ),
        )

        val defaultModel = sanitizedProvider.defaultModel
        if (!defaultModel.isNullOrBlank()) {
            modelPreferenceDao.setDefault(sanitizedProvider.id.value, defaultModel, now.toEpochMilli())
        }
    }

    override suspend fun getApiKey(providerId: ProviderId): String? {
        val ref = providerDao.getProvider(providerId.value)?.apiKeyRef ?: return null
        return secretStore.getSecret(ref)
    }

    override suspend fun deleteProvider(id: ProviderId) {
        val existing = providerDao.getProvider(id.value)
        providerDao.deleteProvider(id.value)
        modelPreferenceDao.deleteForProvider(id.value)
        existing?.apiKeyRef?.let { secretStore.deleteSecret(it) }
    }

    private fun apiKeyRef(providerId: ProviderId): String =
        "provider:${providerId.value}:api-key"

    private fun Map<String, String>.withoutSensitiveHeaders(): Map<String, String> =
        filterKeys { name -> !name.isSensitiveHeaderName() }
            .filter { (name, value) -> name.isNotBlank() && value.isNotBlank() }

    private fun String.isSensitiveHeaderName(): Boolean =
        lowercase() in SENSITIVE_HEADER_NAMES

    private companion object {
        val SENSITIVE_HEADER_NAMES = setOf(
            "authorization",
            "proxy-authorization",
            "x-api-key",
            "api-key",
            "openai-api-key",
        )
    }
}
