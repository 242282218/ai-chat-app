package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.provider.api.providerFailureSummary
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
        require(request.prompt.isNotBlank()) { "图片 Prompt 不能为空。" }
        require(request.model.isNotBlank()) { "图片 Model 不能为空。" }
        require(request.count in 1..4) { "图片数量必须在 1 到 4 之间。" }

        val pending = request.pendingGeneration()
        repository.saveImageGeneration(pending)

        return try {
            val response = imageProvider.generate(request.toProviderRequest())
            require(response.images.isNotEmpty()) { "Provider 未返回图片。" }
            response.images.mapIndexed { index, generated ->
                val base64 = generated.base64
                    ?: error("Provider 返回的是图片 URL；本地保存需要 base64 图片数据。")
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
            repository.saveImageGeneration(
                pending.copy(
                    status = ImageGenerationStatus.Cancelled,
                    errorSummary = "已停止，Prompt 和参数已保留。",
                ),
            )
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
        providerFailureSummary("图片生成失败。")
}
