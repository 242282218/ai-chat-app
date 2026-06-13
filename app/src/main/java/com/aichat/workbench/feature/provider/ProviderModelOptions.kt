package com.aichat.workbench.feature.provider

import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelCapabilitySource
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.defaultModelCapability
import com.aichat.workbench.provider.preferredImageModel
import com.aichat.workbench.provider.supportsImageGeneration
import com.aichat.workbench.provider.supportsTextGeneration

internal fun List<ModelConfig>.withManualModel(type: ProviderType, model: String): List<ModelConfig> {
    val trimmedModel = model.trim()
    val normalizedModels = map { item ->
        val id = item.id.trim()
        item.copy(
            id = id,
            displayName = item.displayName.trim().ifBlank { id },
            capability = item.capability?.copy(model = item.capability.model.trim()),
        )
    }.filter { it.id.isNotBlank() }

    if (trimmedModel.isBlank() || normalizedModels.any { it.id == trimmedModel }) {
        return normalizedModels.distinctBy { it.id }
    }

    return (
        normalizedModels +
            ModelConfig(
                id = trimmedModel,
                displayName = trimmedModel,
                capability = type.defaultModelCapability(trimmedModel),
            )
        ).distinctBy { it.id }
}

internal fun List<ModelConfig>.availableChatModels(): List<ModelConfig> =
    filter { it.supportsTextGeneration() }

internal fun List<ModelConfig>.preferredDiscoveredChatModel(): String =
    availableChatModels().firstOrNull()?.id.orEmpty()

internal fun List<ModelConfig>.withManualImageModel(model: String): List<ModelConfig> =
    withManualRoleModel(model, ::imageGenerationCapability)

internal fun List<ModelConfig>.explicitImageModel(): String =
    filter { it.supportsImageGeneration() }
        .map { it.id }
        .preferredImageModel()
        .orEmpty()

private fun List<ModelConfig>.withManualRoleModel(
    model: String,
    capabilityFor: (String) -> ModelCapability,
): List<ModelConfig> {
    val trimmedModel = model.trim()
    val normalizedModels = map { item -> item.normalizedForRoleModel(trimmedModel, capabilityFor) }
        .filter { it.id.isNotBlank() }
    if (trimmedModel.isBlank() || normalizedModels.any { it.id == trimmedModel }) {
        return normalizedModels.distinctBy { it.id }
    }

    return (
        normalizedModels +
            ModelConfig(
                id = trimmedModel,
                displayName = trimmedModel,
                capability = capabilityFor(trimmedModel),
            )
        ).distinctBy { it.id }
}

private fun ModelConfig.normalizedForRoleModel(
    roleModel: String,
    capabilityFor: (String) -> ModelCapability,
): ModelConfig {
    val normalizedId = id.trim()
    return copy(
        id = normalizedId,
        displayName = displayName.trim().ifBlank { normalizedId },
        capability = if (normalizedId == roleModel) {
            capabilityFor(normalizedId)
        } else {
            capability?.copy(model = capability.model.trim())
        },
    )
}

private fun imageGenerationCapability(model: String): ModelCapability =
    ModelCapability(
        model = model,
        text = false,
        vision = false,
        imageGeneration = true,
        maxContextTokens = null,
        source = ModelCapabilitySource.UserOverride,
    )
