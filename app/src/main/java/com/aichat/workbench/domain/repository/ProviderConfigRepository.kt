package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import kotlinx.coroutines.flow.Flow

interface ProviderConfigRepository {
    fun observeProviders(): Flow<List<ProviderConfig>>

    suspend fun getProvider(id: ProviderId): ProviderConfig?

    suspend fun saveProvider(
        provider: ProviderConfig,
        plaintextApiKey: String?,
        preserveExistingApiKey: Boolean = true,
        deleteReplacedApiKey: Boolean = true,
    )

    suspend fun getApiKey(providerId: ProviderId): String?

    suspend fun deleteApiKeyRef(ref: String)

    suspend fun deleteProvider(id: ProviderId)
}
