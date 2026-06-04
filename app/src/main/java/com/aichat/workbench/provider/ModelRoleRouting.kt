package com.aichat.workbench.provider

import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderConfig

fun ProviderConfig.rolePreferenceModel(
    preferences: List<ModelRolePreference>,
    role: ModelRole,
): String? =
    preferences.firstOrNull { it.providerId == id && it.role == role }
        ?.model
        ?.trim()
        ?.takeIf { it.isNotBlank() }

fun ProviderConfig.preferredChatModel(preferences: List<ModelRolePreference>): String =
    rolePreferenceModel(preferences, ModelRole.Chat) ?: preferredModel()
