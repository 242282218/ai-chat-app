package com.aichat.workbench.domain.model

import java.time.Instant

data class ModelPreference(
    val id: ModelPreferenceId,
    val providerId: ProviderId,
    val model: String,
    val isFavorite: Boolean,
    val isDefault: Boolean,
    val capability: ModelCapability?,
    val updatedAt: Instant,
)
