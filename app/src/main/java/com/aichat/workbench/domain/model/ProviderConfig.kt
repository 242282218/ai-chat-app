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

data class ProviderType(val value: String) : java.io.Serializable {
    companion object {
        val OpenAI = ProviderType("openai")
        val OpenAICompatible = ProviderType("openai_compatible")
        val OpenRouter = ProviderType("openrouter")
        val Anthropic = ProviderType("anthropic")
        val Gemini = ProviderType("gemini")
        val Ollama = ProviderType("ollama")

        fun fromStorage(value: String): ProviderType =
            when (value) {
                "OpenAI", OpenAI.value -> OpenAI
                "OpenAICompatible", OpenAICompatible.value -> OpenAICompatible
                OpenRouter.value -> OpenRouter
                Anthropic.value -> Anthropic
                Gemini.value -> Gemini
                Ollama.value -> Ollama
                else -> ProviderType(value)
            }
    }
}

data class ProviderDescriptor(
    val type: ProviderType,
    val displayName: String,
    val authMode: ProviderAuthMode,
    val supportsCustomBaseUrl: Boolean,
    val defaultBaseUrl: String?,
    val modelDiscovery: ProviderModelDiscovery?,
    val protocol: ProviderProtocol,
    val capabilities: ProviderCapabilities,
) {
    val requiresApiKey: Boolean
        get() = authMode == ProviderAuthMode.ApiKey
}

enum class ProviderAuthMode {
    ApiKey,
    None,
    CustomHeader,
}

data class ProviderModelDiscovery(
    val path: String,
    val responseFormat: ProviderModelDiscoveryFormat,
)

enum class ProviderModelDiscoveryFormat {
    OpenAiModels,
    OllamaTags,
}

enum class ProviderProtocol {
    OpenAiResponses,
    OpenAiChatCompletions,
    AnthropicMessages,
    GeminiGenerateContent,
    OllamaOpenAiCompatible,
}

data class ProviderCapabilities(
    val text: Boolean,
    val vision: Boolean,
    val imageGeneration: Boolean,
    val toolCalling: Boolean,
    val structuredOutput: Boolean,
    val longContext: Boolean,
)

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
    val source: ModelCapabilitySource = ModelCapabilitySource.UserOverride,
)

enum class ModelCapabilitySource {
    BuiltInDefault,
    ProviderDiscovery,
    UserOverride,
}
