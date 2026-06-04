package com.aichat.workbench.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aichat.workbench.data.local.entity.ModelRolePreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelRolePreferenceDao {
    @Query("SELECT * FROM model_role_preferences ORDER BY provider_id ASC, role ASC")
    fun observeAllRolePreferences(): Flow<List<ModelRolePreferenceEntity>>

    @Query(
        """
        SELECT * FROM model_role_preferences
        WHERE provider_id = :providerId
        ORDER BY role ASC
        """,
    )
    fun observeRolePreferences(providerId: String): Flow<List<ModelRolePreferenceEntity>>

    @Query("SELECT * FROM model_role_preferences WHERE provider_id = :providerId AND role = :role")
    suspend fun getByProviderAndRole(providerId: String, role: String): ModelRolePreferenceEntity?

    @Upsert
    suspend fun upsertRolePreference(preference: ModelRolePreferenceEntity)

    @Query("DELETE FROM model_role_preferences WHERE provider_id = :providerId AND role = :role")
    suspend fun deleteRolePreference(providerId: String, role: String)

    @Query("DELETE FROM model_role_preferences WHERE provider_id = :providerId")
    suspend fun deleteForProvider(providerId: String)

    @Query("DELETE FROM model_role_preferences")
    suspend fun deleteAllRolePreferences()
}
