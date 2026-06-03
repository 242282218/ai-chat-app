package com.aichat.workbench.provider

import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelCapabilitySource
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderType

fun ProviderType.defaultModelCapability(
    model: String,
    source: ModelCapabilitySource = ModelCapabilitySource.BuiltInDefault,
): ModelCapability? {
    val descriptor = ProviderRegistry.builtInDescriptor(this) ?: return null
    return ModelCapability(
        model = model,
        text = descriptor.capabilities.text,
        vision = descriptor.capabilities.vision,
        imageGeneration = descriptor.capabilities.imageGeneration,
        toolCalling = descriptor.capabilities.toolCalling,
        structuredOutput = descriptor.capabilities.structuredOutput,
        longContext = descriptor.capabilities.longContext,
        maxContextTokens = null,
        source = source,
    )
}

fun ProviderType.discoveredModelCapability(model: String): ModelCapability? =
    defaultModelCapability(model, source = ModelCapabilitySource.ProviderDiscovery)

fun ProviderConfig.preferredModel(): String =
    defaultModel ?: models.firstOrNull()?.id.orEmpty()
