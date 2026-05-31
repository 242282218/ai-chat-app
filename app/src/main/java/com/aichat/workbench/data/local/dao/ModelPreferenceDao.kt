package com.aichat.workbench.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.aichat.workbench.data.local.entity.ModelPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelPreferenceDao {
    @Query("SELECT * FROM model_preferences ORDER BY updated_at DESC")
    suspend fun getAllModelPreferences(): List<ModelPreferenceEntity>

    @Query(
        """
        SELECT * FROM model_preferences
        WHERE provider_id = :providerId
        ORDER BY is_default DESC, is_favorite DESC, updated_at DESC
        """,
    )
    fun observeModelPreferences(providerId: String): Flow<List<ModelPreferenceEntity>>

    @Query("SELECT * FROM model_preferences WHERE provider_id = :providerId AND model = :model")
    suspend fun getByProviderAndModel(providerId: String, model: String): ModelPreferenceEntity?

    @Upsert
    suspend fun upsertModelPreference(modelPreference: ModelPreferenceEntity)

    @Query("UPDATE model_preferences SET is_default = 0, updated_at = :updatedAt WHERE provider_id = :providerId")
    suspend fun clearDefault(providerId: String, updatedAt: Long)

    @Query("DELETE FROM model_preferences WHERE provider_id = :providerId")
    suspend fun deleteForProvider(providerId: String)

    @Query("DELETE FROM model_preferences")
    suspend fun deleteAllModelPreferences()

    @Transaction
    suspend fun setDefault(providerId: String, model: String, updatedAt: Long) {
        clearDefault(providerId, updatedAt)
        val existing = getByProviderAndModel(providerId, model)
        if (existing == null) {
            upsertModelPreference(
                ModelPreferenceEntity(
                    id = "$providerId:$model",
                    providerId = providerId,
                    model = model,
                    isFavorite = false,
                    isDefault = true,
                    capabilityJson = null,
                    createdAt = updatedAt,
                    updatedAt = updatedAt,
                ),
            )
        } else {
            upsertModelPreference(existing.copy(isDefault = true, updatedAt = updatedAt))
        }
    }
}
