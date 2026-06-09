package com.aichat.workbench.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class ImageGenerationPreferences(
    val providerId: String? = null,
)

interface ImageGenerationPreferencesRepository {
    fun observePreferences(): StateFlow<ImageGenerationPreferences>
    suspend fun saveSelectedProvider(providerId: String?)
}
