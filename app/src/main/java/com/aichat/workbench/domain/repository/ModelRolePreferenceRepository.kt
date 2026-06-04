package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderId
import kotlinx.coroutines.flow.Flow

interface ModelRolePreferenceRepository {
    fun observeRolePreferences(providerId: ProviderId): Flow<List<ModelRolePreference>>

    fun observeAllRolePreferences(): Flow<List<ModelRolePreference>>

    suspend fun getRoleModel(providerId: ProviderId, role: ModelRole): String?

    suspend fun setRoleModel(providerId: ProviderId, role: ModelRole, model: String?)

    suspend fun deleteForProvider(providerId: ProviderId)
}
