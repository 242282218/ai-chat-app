package com.aichat.workbench.tool.runtime

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.GenerateImageRequest
import com.aichat.workbench.domain.usecase.GenerateImageUseCase
import com.aichat.workbench.provider.defaultImageModel
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.requiresApiKey
import com.aichat.workbench.provider.rolePreferenceModel
import com.aichat.workbench.provider.supportsImageGeneration
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

internal class ImageGenerationToolRunner(
    private val providerRepository: ProviderConfigRepository,
    private val preferencesRepository: ImageGenerationPreferencesRepository,
    private val modelRolePreferenceRepository: ModelRolePreferenceRepository,
    private val imageGenerationRepository: ImageGenerationRepository,
    private val imageProvider: ImageGenerationProvider,
    private val imageStorage: ImageStorage,
    private val clock: Clock,
) {
    suspend fun run(conversationId: ConversationId, arguments: String): ExecutedToolOutput {
        val args = decodeToolArguments<ImageGenerationArguments>(arguments)
        val prompt = args.prompt.trim()
        if (prompt.isBlank()) {
            throw InvalidToolArgumentsException("图片提示词不能为空。")
        }
        val provider = selectImageProvider()
        val rolePreferences = modelRolePreferenceRepository.observeAllRolePreferences().first()
        val apiKey = providerRepository.getApiKey(provider.id)
        if (provider.requiresApiKey()) {
            require(!apiKey.isNullOrBlank()) { "API Key 缺失。" }
        }
        val model = args.model?.trim()?.takeIf { it.isNotBlank() }
            ?: provider.rolePreferenceModel(rolePreferences, ModelRole.Image)
            ?: imagePreferencesModel(provider)
            ?: provider.defaultImageModel()
        if (model.isBlank()) {
            throw InvalidToolArgumentsException("图片 Model 不能为空。")
        }
        val count = args.count ?: 1
        if (count !in 1..4) {
            throw InvalidToolArgumentsException("图片数量必须在 1 到 4 之间。")
        }
        val images = GenerateImageUseCase(
            repository = imageGenerationRepository,
            imageProvider = imageProvider,
            imageStorage = imageStorage,
            clock = clock,
        )(
            GenerateImageRequest(
                conversationId = conversationId,
                provider = provider,
                apiKey = apiKey,
                model = model,
                prompt = prompt,
                size = args.size?.trim()?.ifBlank { null },
                quality = args.quality?.trim()?.ifBlank { null },
                count = count,
            ),
        )
        val output = ImageGenerationOutput(
            prompt = prompt,
            providerId = provider.id.value,
            model = model,
            count = images.size,
            images = images.map { it.toOutput() },
            markdown = images.toMarkdown(),
        )
        return ExecutedToolOutput(
            output = ToolOutput.Json(toolJson.encodeToString(output)),
            contentParts = images.toMessageParts(),
        )
    }

    private suspend fun selectImageProvider(): ProviderConfig {
        val preferences = preferencesRepository.observePreferences().value
        val providers = providerRepository.observeProviders().first()
            .filter { it.enabled && it.supportsImageGeneration() }
        val preferred = preferences.providerId
            ?.let { id -> providers.firstOrNull { it.id.value == id } }
        return preferred ?: providers.firstOrNull() ?: error("模型服务未配置。")
    }

    private fun imagePreferencesModel(provider: ProviderConfig): String? {
        val preferences = preferencesRepository.observePreferences().value
        return preferences.model
            ?.takeIf { preferences.providerId == provider.id.value }
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun ImageGeneration.toOutput(): ImageGenerationResultOutput =
        ImageGenerationResultOutput(
            id = id.value,
            originalPath = originalPath,
            thumbnailPath = thumbnailPath,
            status = status.name.lowercase(),
            errorSummary = errorSummary,
        )

    private fun List<ImageGeneration>.toMarkdown(): String =
        mapNotNull { image ->
            image.originalPath?.let { path -> "![generated image]($path)" }
        }.joinToString("\n")

    private fun List<ImageGeneration>.toMessageParts(): List<MessagePart> =
        mapNotNull { image ->
            image.originalPath?.let { path -> MessagePart.Image(uri = path, mimeType = "image/png") }
        }
}

@Serializable
private data class ImageGenerationArguments(
    val prompt: String = "",
    val model: String? = null,
    val size: String? = null,
    val quality: String? = null,
    val count: Int? = null,
)

@Serializable
private data class ImageGenerationOutput(
    val prompt: String,
    val providerId: String,
    val model: String,
    val count: Int,
    val images: List<ImageGenerationResultOutput>,
    val markdown: String,
)

@Serializable
private data class ImageGenerationResultOutput(
    val id: String,
    val originalPath: String?,
    val thumbnailPath: String?,
    val status: String,
    val errorSummary: String? = null,
)
