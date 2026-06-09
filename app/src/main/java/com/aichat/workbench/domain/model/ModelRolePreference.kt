package com.aichat.workbench.domain.model

import java.time.Instant

data class ModelRolePreference(
    val id: ModelRolePreferenceId,
    val providerId: ProviderId,
    val role: ModelRole,
    val model: String,
    val updatedAt: Instant,
)

enum class ModelRole {
    Chat,
    Image,
}
