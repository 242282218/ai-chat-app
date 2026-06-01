package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.StoredImagePaths
import com.aichat.workbench.provider.image.GeneratedImage
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.ImageGenerationProviderRequest
import com.aichat.workbench.provider.image.ImageGenerationProviderResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Test

class GenerateImageUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun generateImage_savesImageBytesAndCompletedHistory() = runTest {
        val repository = FakeImageGenerationRepository()
        val storage = FakeImageStorage()
        val useCase = GenerateImageUseCase(
            repository = repository,
            imageProvider = FakeImageProvider(
                Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)),
            ),
            imageStorage = storage,
            clock = clock,
        )

        val result = useCase(
            GenerateImageRequest(
                conversationId = null,
                provider = providerConfig(),
                apiKey = "test-key",
                model = "gpt-image-1",
                prompt = "A small cabin",
                size = "1024x1024",
                quality = "auto",
                count = 1,
            ),
        )

        assertEquals(ImageGenerationStatus.Completed, result.single().status)
        assertEquals("original/${result.single().id.value}.png", result.single().originalPath)
        assertEquals(byteArrayOf(1, 2, 3).toList(), storage.savedBytes.single().toList())
        assertEquals(result.single(), repository.saved.value.single())
    }

    @Test
    fun generateImage_savesCancelledHistoryWhenProviderIsCancelled() = runTest {
        val repository = FakeImageGenerationRepository()
        val useCase = GenerateImageUseCase(
            repository = repository,
            imageProvider = CancellingImageProvider(),
            imageStorage = FakeImageStorage(),
            clock = clock,
        )

        assertFailsWith<CancellationException> {
            useCase(
                GenerateImageRequest(
                    conversationId = null,
                    provider = providerConfig(),
                    apiKey = "test-key",
                    model = "gpt-image-1",
                    prompt = "A small cabin",
                    size = "1024x1024",
                    quality = "auto",
                    count = 1,
                ),
            )
        }

        assertEquals(ImageGenerationStatus.Cancelled, repository.saved.value.single().status)
        assertEquals("已停止，Prompt 和参数已保留。", repository.saved.value.single().errorSummary)
    }

    private fun providerConfig(): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = "OpenAI",
            type = ProviderType.OpenAI,
            baseUrl = "https://api.openai.com/v1",
            apiKeyRef = null,
            headers = emptyMap(),
            models = emptyList(),
            defaultModel = null,
            enabled = true,
        )

    private class FakeImageGenerationRepository : ImageGenerationRepository {
        val saved = MutableStateFlow<List<ImageGeneration>>(emptyList())

        override fun observeImageGenerations(): Flow<List<ImageGeneration>> = saved

        override suspend fun getImageGeneration(id: ImageGenerationId): ImageGeneration? =
            saved.value.firstOrNull { it.id == id }

        override suspend fun saveImageGeneration(imageGeneration: ImageGeneration) {
            saved.value = saved.value.filterNot { it.id == imageGeneration.id } + imageGeneration
        }

        override suspend fun deleteImageGeneration(id: ImageGenerationId) {
            saved.value = saved.value.filterNot { it.id == id }
        }

        override suspend fun deleteAllImageGenerations() {
            saved.value = emptyList()
        }
    }

    private class FakeImageProvider(
        private val base64: String,
    ) : ImageGenerationProvider {
        override suspend fun generate(
            request: ImageGenerationProviderRequest,
        ): ImageGenerationProviderResponse =
            ImageGenerationProviderResponse(
                images = listOf(
                    GeneratedImage(base64 = base64, url = null, revisedPrompt = null),
                ),
            )
    }

    private class FakeImageStorage : ImageStorage {
        val savedBytes = mutableListOf<ByteArray>()

        override suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths {
            savedBytes += bytes
            return StoredImagePaths(
                originalPath = "original/${id.value}.png",
                thumbnailPath = "thumbnail/${id.value}.png",
            )
        }

        override suspend fun deleteAllImages() = Unit
    }

    private class CancellingImageProvider : ImageGenerationProvider {
        override suspend fun generate(
            request: ImageGenerationProviderRequest,
        ): ImageGenerationProviderResponse {
            throw CancellationException("cancelled")
        }
    }
}
