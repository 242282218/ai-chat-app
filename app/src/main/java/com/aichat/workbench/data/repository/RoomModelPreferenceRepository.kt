package com.aichat.workbench.data.repository

import com.aichat.workbench.data.local.dao.ModelPreferenceDao
import com.aichat.workbench.data.mapper.toDomain
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.domain.model.ModelPreference
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ModelPreferenceRepository
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomModelPreferenceRepository(
    private val dao: ModelPreferenceDao,
    private val clock: Clock,
) : ModelPreferenceRepository {
    override fun observeModelPreferences(providerId: ProviderId): Flow<List<ModelPreference>> =
        dao.observeModelPreferences(providerId.value).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun saveModelPreference(modelPreference: ModelPreference) {
        val existing = dao.getByProviderAndModel(modelPreference.providerId.value, modelPreference.model)
        dao.upsertModelPreference(
            modelPreference.toEntity(
                createdAt = existing?.createdAt?.let(java.time.Instant::ofEpochMilli)
                    ?: modelPreference.updatedAt,
            ),
        )
    }

    override suspend fun setDefaultModel(providerId: ProviderId, model: String) {
        dao.setDefault(providerId.value, model, clock.millis())
    }
}
