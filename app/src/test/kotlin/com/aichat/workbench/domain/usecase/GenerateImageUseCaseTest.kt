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
import com.aichat.workbench.provider.api.ProviderError
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.image.GeneratedImage
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.ImageGenerationProviderRequest
import com.aichat.workbench.provider.image.ImageGenerationProviderResponse
import java.net.SocketTimeoutException
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
import org.junit.Assert.assertNull
import org.junit.Test

class GenerateImageUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-31T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun generateImage_savesImageBytesAndCompletedHistory() = runTest {
        val repository = FakeImageGenerationRepository()
        val storage = FakeImageStorage()
        val imageProvider = FakeImageProvider(
            images = listOf(base64Image(byteArrayOf(1, 2, 3))),
        )
        val useCase = createUseCase(repository, imageProvider, storage)

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
    fun generateImage_trimsProviderRequestAndSavedHistory() = runTest {
        val repository = FakeImageGenerationRepository()
        val imageProvider = FakeImageProvider(
            images = listOf(base64Image(byteArrayOf(4, 5, 6))),
        )
        val useCase = createUseCase(repository, imageProvider)

        val result = useCase(
            GenerateImageRequest(
                conversationId = null,
                provider = providerConfig(),
                apiKey = "test-key",
                model = " gpt-image-1 ",
                prompt = " A small cabin ",
                size = " ",
                quality = " auto ",
                count = 1,
            ),
        )

        val providerRequest = imageProvider.requests.single()
        assertEquals("gpt-image-1", providerRequest.model)
        assertEquals("A small cabin", providerRequest.prompt)
        assertNull(providerRequest.size)
        assertEquals("auto", providerRequest.quality)
        assertEquals("gpt-image-1", result.single().model)
        assertEquals("A small cabin", result.single().prompt)
        assertNull(result.single().size)
        assertEquals("auto", result.single().quality)
    }

    @Test
    fun generateImage_rejectsInvalidPromptBeforeSavingHistory() = runTest {
        val repository = FakeImageGenerationRepository()
        val imageProvider = FakeImageProvider(images = emptyList())
        val useCase = createUseCase(repository, imageProvider)

        val error = assertFailsWith<IllegalArgumentException> {
            useCase(validRequest(prompt = " "))
        }

        assertEquals("图片提示词不能为空。", error.message)
        assertEquals(emptyList<ImageGeneration>(), repository.saved.value)
        assertEquals(0, imageProvider.requests.size)
    }

    @Test
    fun generateImage_rejectsInvalidModelBeforeSavingHistory() = runTest {
        val repository = FakeImageGenerationRepository()
        val imageProvider = FakeImageProvider(images = emptyList())
        val useCase = createUseCase(repository, imageProvider)

        val error = assertFailsWith<IllegalArgumentException> {
            useCase(validRequest(model = " "))
        }

        assertEquals("图片模型不能为空。", error.message)
        assertEquals(emptyList<ImageGeneration>(), repository.saved.value)
        assertEquals(0, imageProvider.requests.size)
    }

    @Test
    fun generateImage_rejectsInvalidCountBeforeSavingHistory() = runTest {
        val repository = FakeImageGenerationRepository()
        val imageProvider = FakeImageProvider(images = emptyList())
        val useCase = createUseCase(repository, imageProvider)

        val error = assertFailsWith<IllegalArgumentException> {
            useCase(validRequest(count = 0))
        }

        assertEquals("图片数量必须在 1 到 4 之间。", error.message)
        assertEquals(emptyList<ImageGeneration>(), repository.saved.value)
        assertEquals(0, imageProvider.requests.size)
    }

    @Test
    fun generateImage_savesFailedHistoryWhenProviderReturnsNoImages() = runTest {
        val repository = FakeImageGenerationRepository()
        val useCase = createUseCase(repository, FakeImageProvider(images = emptyList()))

        val error = assertFailsWith<IllegalArgumentException> {
            useCase(validRequest())
        }

        assertEquals("Provider 未返回图片。", error.message)
        assertEquals(ImageGenerationStatus.Failed, repository.saved.value.single().status)
        assertEquals("Provider 未返回图片。", repository.saved.value.single().errorSummary)
    }

    @Test
    fun generateImage_savesFailedHistoryWhenProviderReturnsUrlOnly() = runTest {
        val repository = FakeImageGenerationRepository()
        val useCase = createUseCase(
            repository = repository,
            imageProvider = FakeImageProvider(
                images = listOf(
                    GeneratedImage(
                        base64 = null,
                        url = "https://example.test/image.png",
                        revisedPrompt = null,
                    ),
                ),
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            useCase(validRequest())
        }

        assertEquals("Provider 返回的是图片 URL；本地保存需要 base64 图片数据。", error.message)
        assertEquals(ImageGenerationStatus.Failed, repository.saved.value.single().status)
        assertEquals(
            "Provider 返回的是图片 URL；本地保存需要 base64 图片数据。",
            repository.saved.value.single().errorSummary,
        )
    }

    @Test
    fun generateImage_savesProviderHttpRecoverySummaryInFailedHistory() = runTest {
        val repository = FakeImageGenerationRepository()
        val useCase = createUseCase(
            repository = repository,
            imageProvider = ThrowingImageProvider(
                ProviderHttpException(
                    ProviderError(
                        code = "rate_limited",
                        message = "too many image requests",
                        statusCode = 429,
                        retryable = true,
                    ),
                ),
            ),
        )

        assertFailsWith<ProviderHttpException> {
            useCase(validRequest())
        }

        assertEquals(ImageGenerationStatus.Failed, repository.saved.value.single().status)
        assertEquals(
            "too many image requests（code: rate_limited，HTTP 429，可重试） 请求被限流，请稍后重试或切换模型/Provider。",
            repository.saved.value.single().errorSummary,
        )
    }

    @Test
    fun generateImage_savesProviderTimeoutRecoverySummaryInFailedHistory() = runTest {
        val repository = FakeImageGenerationRepository()
        val useCase = createUseCase(
            repository = repository,
            imageProvider = ThrowingImageProvider(SocketTimeoutException("timeout")),
        )

        assertFailsWith<SocketTimeoutException> {
            useCase(validRequest())
        }

        assertEquals(ImageGenerationStatus.Failed, repository.saved.value.single().status)
        assertEquals(
            "Provider 请求超时。请稍后重试，或切换网络、模型/Provider。",
            repository.saved.value.single().errorSummary,
        )
    }

    @Test
    fun generateImage_savesCancelledHistoryWhenProviderIsCancelled() = runTest {
        val repository = FakeImageGenerationRepository()
        val useCase = createUseCase(repository, CancellingImageProvider())

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
        assertEquals("已停止，提示词和参数已保留。", repository.saved.value.single().errorSummary)
    }

    private fun validRequest(
        model: String = "gpt-image-1",
        prompt: String = "A small cabin",
        count: Int = 1,
    ): GenerateImageRequest =
        GenerateImageRequest(
            conversationId = null,
            provider = providerConfig(),
            apiKey = "test-key",
            model = model,
            prompt = prompt,
            size = "1024x1024",
            quality = "auto",
            count = count,
        )

    private fun createUseCase(
        repository: FakeImageGenerationRepository,
        imageProvider: ImageGenerationProvider,
        imageStorage: ImageStorage = FakeImageStorage(),
    ): GenerateImageUseCase =
        GenerateImageUseCase(
            repository = repository,
            imageProvider = imageProvider,
            imageStorage = imageStorage,
            clock = clock,
        )

    private fun base64Image(bytes: ByteArray): GeneratedImage =
        GeneratedImage(
            base64 = Base64.getEncoder().encodeToString(bytes),
            url = null,
            revisedPrompt = null,
        )

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
        private val images: List<GeneratedImage>,
    ) : ImageGenerationProvider {
        val requests = mutableListOf<ImageGenerationProviderRequest>()

        override suspend fun generate(
            request: ImageGenerationProviderRequest,
        ): ImageGenerationProviderResponse {
            requests += request
            return ImageGenerationProviderResponse(images = images)
        }
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

        override suspend fun deleteImage(id: ImageGenerationId) = Unit

        override suspend fun deleteAllImages() = Unit
    }

    private class CancellingImageProvider : ImageGenerationProvider {
        override suspend fun generate(
            request: ImageGenerationProviderRequest,
        ): ImageGenerationProviderResponse {
            throw CancellationException("cancelled")
        }
    }

    private class ThrowingImageProvider(
        private val error: Throwable,
    ) : ImageGenerationProvider {
        override suspend fun generate(
            request: ImageGenerationProviderRequest,
        ): ImageGenerationProviderResponse {
            throw error
        }
    }
}
