package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ModelPreference
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ModelPreferenceRepository

class SaveModelPreferenceUseCase(
    private val repository: ModelPreferenceRepository,
) {
    suspend operator fun invoke(modelPreference: ModelPreference) {
        require(modelPreference.model.isNotBlank()) { "Model name must not be blank." }
        repository.saveModelPreference(modelPreference)
    }
}

class SetDefaultModelUseCase(
    private val repository: ModelPreferenceRepository,
) {
    suspend operator fun invoke(providerId: ProviderId, model: String) {
        require(model.isNotBlank()) { "Default model must not be blank." }
        repository.setDefaultModel(providerId, model)
    }
}
