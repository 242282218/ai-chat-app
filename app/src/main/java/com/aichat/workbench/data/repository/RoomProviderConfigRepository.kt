package com.aichat.workbench.data.repository

import com.aichat.workbench.data.crypto.SecretStore
import com.aichat.workbench.data.crypto.SecretStoreException
import com.aichat.workbench.data.local.dao.ModelRolePreferenceDao
import com.aichat.workbench.data.local.dao.ProviderConfigDao
import com.aichat.workbench.data.mapper.toDomain
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.persistableProviderHeaders
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomProviderConfigRepository(
    private val providerDao: ProviderConfigDao,
    private val secretStore: SecretStore,
    private val clock: Clock,
    private val modelRolePreferenceDao: ModelRolePreferenceDao,
) : ProviderConfigRepository {
    override fun observeProviders(): Flow<List<ProviderConfig>> =
        providerDao.observeProviders().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getProvider(id: ProviderId): ProviderConfig? =
        providerDao.getProvider(id.value)?.toDomain()

    override suspend fun saveProvider(
        provider: ProviderConfig,
        plaintextApiKey: String?,
        preserveExistingApiKey: Boolean,
        deleteReplacedApiKey: Boolean,
    ) {
        val existing = providerDao.getProvider(provider.id.value)
        val secretRef = when {
            plaintextApiKey != null -> apiKeyRef(provider.id)
            preserveExistingApiKey && existing?.apiKeyRef != null -> existing.apiKeyRef
            preserveExistingApiKey -> provider.apiKeyRef
            else -> null
        }

        if (plaintextApiKey != null) {
            secretStore.putSecret(requireNotNull(secretRef), plaintextApiKey)
        }

        val now = clock.instant()
        val sanitizedProvider = provider.copy(
            apiKeyRef = secretRef,
            headers = provider.headers.persistableProviderHeaders(),
        )
        providerDao.upsertProvider(
            sanitizedProvider.toEntity(
                createdAt = existing?.createdAt?.let(Instant::ofEpochMilli) ?: now,
                updatedAt = now,
            ),
        )

        if (deleteReplacedApiKey && existing?.apiKeyRef != null && existing.apiKeyRef != secretRef) {
            secretStore.deleteSecret(existing.apiKeyRef)
        }
    }

    override suspend fun getApiKey(providerId: ProviderId): String? {
        val ref = providerDao.getProvider(providerId.value)?.apiKeyRef ?: return null
        return try {
            secretStore.getSecret(ref)
        } catch (error: SecretStoreException) {
            throw SecretStoreException("API Key 解密失败，请重新保存模型连接中的 API Key。", error)
        }
    }

    override suspend fun deleteApiKeyRef(ref: String) {
        secretStore.deleteSecret(ref)
    }

    override suspend fun deleteProvider(id: ProviderId) {
        val existing = providerDao.getProvider(id.value)
        providerDao.deleteProvider(id.value)
        modelRolePreferenceDao.deleteForProvider(id.value)
        existing?.apiKeyRef?.let { secretStore.deleteSecret(it) }
    }

    private fun apiKeyRef(providerId: ProviderId): String =
        "provider:${providerId.value}:api-key"

}
