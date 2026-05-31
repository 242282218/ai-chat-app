package com.aichat.workbench.data.repository

import com.aichat.workbench.data.local.dao.PromptPresetDao
import com.aichat.workbench.data.mapper.toDomain
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.repository.PromptPresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPromptPresetRepository(
    private val dao: PromptPresetDao,
) : PromptPresetRepository {
    override fun observePromptPresets(): Flow<List<PromptPreset>> =
        dao.observePromptPresets().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getPromptPreset(id: PromptPresetId): PromptPreset? =
        dao.getPromptPreset(id.value)?.toDomain()

    override suspend fun savePromptPreset(promptPreset: PromptPreset) {
        dao.upsertPromptPreset(promptPreset.toEntity())
    }

    override suspend fun deletePromptPreset(id: PromptPresetId) {
        dao.deletePromptPreset(id.value)
    }
}
