package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import kotlinx.coroutines.flow.Flow

class ObserveModelRolePreferencesUseCase(
    private val repository: ModelRolePreferenceRepository,
) {
    operator fun invoke(providerId: ProviderId): Flow<List<ModelRolePreference>> =
        repository.observeRolePreferences(providerId)
}

class SetModelRolePreferenceUseCase(
    private val repository: ModelRolePreferenceRepository,
) {
    suspend operator fun invoke(providerId: ProviderId, role: ModelRole, model: String?) {
        repository.setRoleModel(providerId, role, model)
    }
}
