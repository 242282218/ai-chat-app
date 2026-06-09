package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderId
import kotlinx.coroutines.flow.Flow

interface ModelRolePreferenceRepository {
    fun observeAllRolePreferences(): Flow<List<ModelRolePreference>>

    suspend fun setRoleModel(providerId: ProviderId, role: ModelRole, model: String?)
}
