package com.aichat.workbench.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aichat.workbench.data.local.entity.PromptPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptPresetDao {
    @Query("SELECT * FROM prompt_presets ORDER BY updated_at DESC")
    fun observePromptPresets(): Flow<List<PromptPresetEntity>>

    @Query("SELECT * FROM prompt_presets WHERE id = :id")
    suspend fun getPromptPreset(id: String): PromptPresetEntity?

    @Upsert
    suspend fun upsertPromptPreset(promptPreset: PromptPresetEntity)

    @Query("DELETE FROM prompt_presets WHERE id = :id")
    suspend fun deletePromptPreset(id: String)

    @Query("DELETE FROM prompt_presets")
    suspend fun deleteAllPromptPresets()
}
