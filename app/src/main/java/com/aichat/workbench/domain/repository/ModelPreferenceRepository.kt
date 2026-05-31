package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.ModelPreference
import com.aichat.workbench.domain.model.ProviderId
import kotlinx.coroutines.flow.Flow

interface ModelPreferenceRepository {
    fun observeModelPreferences(providerId: ProviderId): Flow<List<ModelPreference>>

    suspend fun saveModelPreference(modelPreference: ModelPreference)

    suspend fun setDefaultModel(providerId: ProviderId, model: String)
}
