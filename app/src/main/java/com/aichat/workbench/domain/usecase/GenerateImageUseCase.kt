package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.ImageGenerationProviderRequest
import java.time.Clock
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException

data class GenerateImageRequest(
    val conversationId: ConversationId?,
    val provider: ProviderConfig,
    val apiKey: String?,
    val model: String,
    val prompt: String,
    val size: String?,
    val quality: String?,
    val count: Int,
)

class GenerateImageUseCase(
    private val repository: ImageGenerationRepository,
    private val imageProvider: ImageGenerationProvider,
    private val imageStorage: ImageStorage,
    private val clock: Clock,
) {
    suspend operator fun invoke(request: GenerateImageRequest): List<ImageGeneration> {
        require(request.prompt.isNotBlank()) { "Image prompt must not be blank." }
        require(request.model.isNotBlank()) { "Image model must not be blank." }
        require(request.count in 1..4) { "Image count must be between 1 and 4." }

        val pending = request.pendingGeneration()
        repository.saveImageGeneration(pending)

        return try {
            val response = imageProvider.generate(request.toProviderRequest())
            require(response.images.isNotEmpty()) { "Provider returned no images." }
            response.images.mapIndexed { index, generated ->
                val base64 = generated.base64
                    ?: error("Provider returned image URL; local persistence requires base64 image data.")
                val id = if (index == 0) pending.id else ImageGenerationId(UUID.randomUUID().toString())
                val paths = imageStorage.savePng(id, Base64.getDecoder().decode(base64))
                pending.copy(
                    id = id,
                    count = response.images.size,
                    originalPath = paths.originalPath,
                    thumbnailPath = paths.thumbnailPath,
                    status = ImageGenerationStatus.Completed,
                    errorSummary = null,
                ).also { repository.saveImageGeneration(it) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val failed = pending.copy(
                status = ImageGenerationStatus.Failed,
                errorSummary = error.summary(),
            )
            repository.saveImageGeneration(failed)
            throw error
        }
    }

    private fun GenerateImageRequest.pendingGeneration(): ImageGeneration =
        ImageGeneration(
            id = ImageGenerationId(UUID.randomUUID().toString()),
            conversationId = conversationId,
            prompt = prompt.trim(),
            providerId = provider.id,
            model = model.trim(),
            size = size?.trim()?.ifBlank { null },
            quality = quality?.trim()?.ifBlank { null },
            count = count,
            originalPath = null,
            thumbnailPath = null,
            status = ImageGenerationStatus.Pending,
            errorSummary = null,
            createdAt = clock.instant(),
        )

    private fun GenerateImageRequest.toProviderRequest(): ImageGenerationProviderRequest =
        ImageGenerationProviderRequest(
            provider = provider,
            apiKey = apiKey,
            model = model.trim(),
            prompt = prompt.trim(),
            size = size?.trim()?.ifBlank { null },
            quality = quality?.trim()?.ifBlank { null },
            count = count,
        )

    private fun Throwable.summary(): String =
        when (this) {
            is ProviderHttpException -> error.message
            else -> message ?: "Image generation failed."
        }
}
