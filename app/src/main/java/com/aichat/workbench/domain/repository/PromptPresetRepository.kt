package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import kotlinx.coroutines.flow.Flow

interface PromptPresetRepository {
    fun observePromptPresets(): Flow<List<PromptPreset>>

    suspend fun getPromptPreset(id: PromptPresetId): PromptPreset?

    suspend fun savePromptPreset(promptPreset: PromptPreset)

    suspend fun deletePromptPreset(id: PromptPresetId)
}
