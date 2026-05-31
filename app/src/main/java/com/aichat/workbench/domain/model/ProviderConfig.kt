package com.aichat.workbench.domain.model

data class ProviderConfig(
    val id: ProviderId,
    val name: String,
    val type: ProviderType,
    val baseUrl: String,
    val apiKeyRef: String?,
    val headers: Map<String, String>,
    val models: List<ModelConfig>,
    val defaultModel: String?,
    val enabled: Boolean,
)

enum class ProviderType {
    OpenAI,
    OpenAICompatible,
}

data class ModelConfig(
    val id: String,
    val displayName: String,
    val capability: ModelCapability?,
)

data class ModelCapability(
    val model: String,
    val text: Boolean,
    val vision: Boolean,
    val imageGeneration: Boolean,
    val toolCalling: Boolean,
    val structuredOutput: Boolean,
    val longContext: Boolean,
    val maxContextTokens: Int?,
)
