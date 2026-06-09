package com.aichat.workbench.data.repository

import com.aichat.workbench.data.local.dao.ModelRolePreferenceDao
import com.aichat.workbench.data.mapper.toDomainOrNull
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import java.time.Clock
import kotlinx.coroutines.flow.map

class RoomModelRolePreferenceRepository(
    private val dao: ModelRolePreferenceDao,
    private val clock: Clock,
) : ModelRolePreferenceRepository {
    override fun observeAllRolePreferences() =
        dao.observeAllRolePreferences().map { entities ->
            entities.mapNotNull { it.toDomainOrNull() }
        }

    override suspend fun setRoleModel(providerId: ProviderId, role: ModelRole, model: String?) {
        val trimmedModel = model?.trim().orEmpty()
        if (trimmedModel.isBlank()) {
            dao.deleteRolePreference(providerId.value, role.name)
            return
        }
        val existing = dao.getByProviderAndRole(providerId.value, role.name)
        val now = clock.millis()
        dao.upsertRolePreference(
            com.aichat.workbench.data.local.entity.ModelRolePreferenceEntity(
                id = existing?.id ?: "${providerId.value}:${role.name}",
                providerId = providerId.value,
                role = role.name,
                model = trimmedModel,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }
}
