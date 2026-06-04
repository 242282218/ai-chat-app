package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

object EmptyModelRolePreferenceRepository : ModelRolePreferenceRepository {
    override fun observeRolePreferences(providerId: ProviderId): Flow<List<ModelRolePreference>> =
        flowOf(emptyList())

    override fun observeAllRolePreferences(): Flow<List<ModelRolePreference>> =
        flowOf(emptyList())

    override suspend fun getRoleModel(providerId: ProviderId, role: ModelRole): String? =
        null

    override suspend fun setRoleModel(providerId: ProviderId, role: ModelRole, model: String?) = Unit

    override suspend fun deleteForProvider(providerId: ProviderId) = Unit
}
