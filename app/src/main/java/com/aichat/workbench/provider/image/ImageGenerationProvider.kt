package com.aichat.workbench.provider.image

import com.aichat.workbench.domain.model.ProviderConfig

interface ImageGenerationProvider {
    suspend fun generate(request: ImageGenerationProviderRequest): ImageGenerationProviderResponse
}

data class ImageGenerationProviderRequest(
    val provider: ProviderConfig,
    val apiKey: String?,
    val model: String,
    val prompt: String,
    val size: String?,
    val quality: String?,
    val count: Int,
)

data class ImageGenerationProviderResponse(
    val images: List<GeneratedImage>,
)

data class GeneratedImage(
    val base64: String?,
    val url: String?,
    val revisedPrompt: String?,
)
