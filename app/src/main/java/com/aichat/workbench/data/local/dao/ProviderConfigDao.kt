package com.aichat.workbench.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aichat.workbench.data.local.entity.ProviderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs ORDER BY enabled DESC, updated_at DESC")
    fun observeProviders(): Flow<List<ProviderConfigEntity>>

    @Query("SELECT * FROM provider_configs WHERE id = :id")
    suspend fun getProvider(id: String): ProviderConfigEntity?

    @Upsert
    suspend fun upsertProvider(provider: ProviderConfigEntity)

    @Query("DELETE FROM provider_configs WHERE id = :id")
    suspend fun deleteProvider(id: String)

    @Query("DELETE FROM provider_configs")
    suspend fun deleteAllProviders()
}
