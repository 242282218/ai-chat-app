package com.aichat.workbench.provider

import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelCapabilitySource
import com.aichat.workbench.domain.model.ModelConfig
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
        ?.let { capability ->
            val imageGenerationModel = model.isLikelyImageGenerationModel()
            capability.copy(
                text = capability.text && !imageGenerationModel,
                vision = capability.vision && !imageGenerationModel,
                imageGeneration = capability.imageGeneration && imageGenerationModel,
                toolCalling = capability.toolCalling && !imageGenerationModel,
                structuredOutput = capability.structuredOutput && !imageGenerationModel,
                longContext = capability.longContext && !imageGenerationModel,
            )
        }

fun ProviderConfig.preferredModel(): String =
    defaultModel
        ?.takeUnless { model -> model.isLikelyImageGenerationModel() }
        ?.takeUnless { model -> models.firstOrNull { it.id == model }?.supportsTextGeneration() == false }
        ?: models.firstOrNull { it.supportsTextGeneration() }?.id
        ?: defaultModel
        ?: models.firstOrNull()?.id.orEmpty()

fun ProviderConfig.supportsTextGeneration(): Boolean =
    when {
        models.isNotEmpty() ->
            defaultModel?.let { model -> models.firstOrNull { it.id == model }?.supportsTextGeneration() } == true ||
                models.any { it.supportsTextGeneration() }
        defaultModel?.isLikelyImageGenerationModel() == true -> false
        else -> true
    }

fun ModelConfig.supportsTextGeneration(): Boolean =
    capability?.text ?: !id.isLikelyImageGenerationModel()

fun ProviderConfig.defaultImageModel(): String =
    models
        .filter { it.supportsImageGeneration() }
        .map { it.id }
        .preferredImageModel()
        ?: defaultModel?.takeIf { isSupportedImageModel(it) }
        ?: if (type.supportsOpenAiCompatibleImageGeneration() || supportsImageGeneration()) {
            DEFAULT_OPENAI_IMAGE_MODEL
        } else {
            ""
        }

fun ProviderConfig.supportsImageGeneration(): Boolean =
    ProviderRegistry.builtInDescriptor(type)?.capabilities?.imageGeneration == true &&
        (
            models.isEmpty() ||
                models.any { it.supportsImageGeneration() } ||
                defaultModel?.isLikelyImageGenerationModel() == true
            )

fun ProviderConfig.isSupportedImageModel(model: String): Boolean {
    if (model.isBlank()) return false
    val modelConfig = models.firstOrNull { it.id == model }
    return modelConfig?.supportsImageGeneration() ?: model.isLikelyImageGenerationModel()
}

fun ModelConfig.supportsImageGeneration(): Boolean =
    capability?.imageGeneration == true || (capability == null && id.isLikelyImageGenerationModel())

fun ProviderType.supportsOpenAiCompatibleImageGeneration(): Boolean =
    this == ProviderType.OpenAI ||
        this == ProviderType.NewApi ||
        this == ProviderType.Sub2Api ||
        this == ProviderType.Custom

fun ProviderConfig.requiresApiKey(): Boolean =
    ProviderRegistry.builtInDescriptor(type)?.requiresApiKey ?: true

fun String.isLikelyImageGenerationModel(): Boolean {
    val normalized = trim().lowercase()
    if (normalized.isBlank()) return false
    return IMAGE_GENERATION_MODEL_MARKERS.any { marker -> normalized.contains(marker) }
}

fun List<String>.preferredImageModel(): String? =
    sortedWith(compareByDescending<String> { it.imageModelScore() }.thenBy { it }).firstOrNull()

private fun String.imageModelScore(): Int {
    val normalized = trim().lowercase()
    return when {
        normalized == "gpt-image-2" -> 100
        normalized.startsWith("gpt-image-2") -> 95
        normalized.startsWith("gpt-image-1.5") -> 85
        normalized.startsWith("gpt-image-1") -> 80
        normalized.startsWith("dall-e-3") -> 70
        normalized.startsWith("dall-e-2") -> 60
        else -> 10
    }
}

private val IMAGE_GENERATION_MODEL_MARKERS = listOf(
    "gpt-image",
    "dall-e",
    "imagen",
    "flux",
    "stable-diffusion",
    "sdxl",
    "midjourney",
    "recraft",
)

const val DEFAULT_OPENAI_IMAGE_MODEL = "gpt-image-1"
