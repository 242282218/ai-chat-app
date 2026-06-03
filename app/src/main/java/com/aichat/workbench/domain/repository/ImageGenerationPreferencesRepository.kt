package com.aichat.workbench.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class ImageGenerationPreferences(
    val providerId: String? = null,
    val model: String? = null,
)

interface ImageGenerationPreferencesRepository {
    fun observePreferences(): StateFlow<ImageGenerationPreferences>
    suspend fun savePreferences(providerId: String?, model: String?)
}
