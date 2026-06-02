package com.aichat.workbench.feature.image

import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.StoredImagePaths
import com.aichat.workbench.provider.image.GeneratedImage
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.ImageGenerationProviderRequest
import com.aichat.workbench.provider.image.ImageGenerationProviderResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ImageGenerationViewModelTest {
    @get:Rule
    val mainDispatcherRule = ImageMainDispatcherRule()

    private val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun clearHistoryDeletesImagesAndRows() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeImageGenerationRepository(listOf(imageGeneration()))
        val storage = FakeImageStorage()
        val viewModel = viewModel(repository, storage)
        advanceUntilIdle()

        viewModel.clearHistory()
        advanceUntilIdle()

        assertTrue(storage.deleted)
        assertEquals(emptyList<ImageGeneration>(), repository.generations.value)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun clearHistoryReportsStorageFailureAndKeepsRows() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeImageGenerationRepository(listOf(imageGeneration()))
        val storage = FakeImageStorage(failOnDelete = true)
        val viewModel = viewModel(repository, storage)
        advanceUntilIdle()

        viewModel.clearHistory()
        advanceUntilIdle()

        assertEquals("无法删除图片文件。", viewModel.state.value.error)
        assertEquals(1, repository.generations.value.size)
    }

    @Test
    fun observesOnlyImageCapableProviders() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeImageGenerationRepository(emptyList())
        val storage = FakeImageStorage()
        val openAiProvider = provider("openai", ProviderType.OpenAI)
        val compatibleProvider = provider("compatible", ProviderType.OpenAICompatible)
        val viewModel = viewModel(
            repository = repository,
            storage = storage,
            providerRepository = FakeProviderConfigRepository(listOf(compatibleProvider, openAiProvider)),
        )
        advanceUntilIdle()

        assertEquals(listOf(openAiProvider), viewModel.state.value.providers)
        assertEquals(openAiProvider.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("gpt-image-1", viewModel.state.value.model)
    }

    @Test
    fun generateWithoutImageCapableProviderDoesNotCallImageProvider() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeImageGenerationRepository(emptyList())
        val storage = FakeImageStorage()
        val imageProvider = RecordingImageProvider()
        val viewModel = viewModel(
            repository = repository,
            storage = storage,
            providerRepository = FakeProviderConfigRepository(
                listOf(provider("compatible", ProviderType.OpenAICompatible)),
            ),
            imageProvider = imageProvider,
        )
        advanceUntilIdle()

        viewModel.updatePrompt("Draw a test scene")
        viewModel.generate()
        advanceUntilIdle()

        assertEquals("模型服务未配置。", viewModel.state.value.error)
        assertEquals(emptyList<ImageGeneration>(), repository.generations.value)
        assertEquals(emptyList<ImageGenerationProviderRequest>(), imageProvider.requests)
    }

    @Test
    fun generateWithoutSavedApiKeyDoesNotCallImageProvider() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeImageGenerationRepository(emptyList())
        val imageProvider = RecordingImageProvider()
        val openAiProvider = provider("openai", ProviderType.OpenAI, apiKeyRef = "missing-key-ref")
        val viewModel = viewModel(
            repository = repository,
            storage = FakeImageStorage(),
            providerRepository = FakeProviderConfigRepository(listOf(openAiProvider)),
            imageProvider = imageProvider,
        )
        advanceUntilIdle()

        viewModel.updatePrompt("Draw a test scene")
        viewModel.generate()
        advanceUntilIdle()

        assertEquals("API Key 缺失。", viewModel.state.value.error)
        assertEquals(emptyList<ImageGeneration>(), repository.generations.value)
        assertEquals(0, imageProvider.requests.size)
    }

    @Test
    fun generatePassesSavedApiKeyToImageProvider() = runTest(mainDispatcherRule.testDispatcher) {
        val repository = FakeImageGenerationRepository(emptyList())
        val openAiProvider = provider("openai", ProviderType.OpenAI, apiKeyRef = "key-ref")
        val imageProvider = RecordingImageProvider(
            response = ImageGenerationProviderResponse(
                images = listOf(base64Image(byteArrayOf(1, 2, 3))),
            ),
        )
        val viewModel = viewModel(
            repository = repository,
            storage = FakeImageStorage(),
            providerRepository = FakeProviderConfigRepository(
                initialProviders = listOf(openAiProvider),
                apiKeys = mapOf(openAiProvider.id to "test-key"),
            ),
            imageProvider = imageProvider,
        )
        advanceUntilIdle()

        viewModel.updatePrompt("Draw a test scene")
        viewModel.generate()
        advanceUntilIdle()

        assertNull(viewModel.state.value.error)
        assertEquals("test-key", imageProvider.requests.single().apiKey)
        assertEquals("Draw a test scene", imageProvider.requests.single().prompt)
        assertEquals(ImageGenerationStatus.Completed, repository.generations.value.single().status)
    }

    @Test
    fun switchesToDefaultImageModelWhenImageProviderFallbackChanges() = runTest(mainDispatcherRule.testDispatcher) {
        val providerRepository = FakeProviderConfigRepository(
            listOf(provider("compatible", ProviderType.OpenAICompatible)),
        )
        val openAiProvider = provider("openai", ProviderType.OpenAI)
        val viewModel = viewModel(
            repository = FakeImageGenerationRepository(emptyList()),
            storage = FakeImageStorage(),
            providerRepository = providerRepository,
        )
        advanceUntilIdle()

        viewModel.updateModel("openrouter/text-model")
        providerRepository.replaceProviders(listOf(openAiProvider))
        advanceUntilIdle()

        assertEquals(openAiProvider.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("gpt-image-1", viewModel.state.value.model)
    }

    private fun viewModel(
        repository: ImageGenerationRepository,
        storage: ImageStorage,
        providerRepository: ProviderConfigRepository = FakeProviderConfigRepository(emptyList()),
        imageProvider: ImageGenerationProvider = NoopImageProvider(),
    ): ImageGenerationViewModel =
        ImageGenerationViewModel(
            imageRepository = repository,
            providerRepository = providerRepository,
            imageProvider = imageProvider,
            imageStorage = storage,
            clock = clock,
        )

    private fun imageGeneration(): ImageGeneration =
        ImageGeneration(
            id = ImageGenerationId("image-1"),
            conversationId = null,
            prompt = "A test image",
            providerId = ProviderId("provider-1"),
            model = "gpt-image-1",
            size = "1024x1024",
            quality = "auto",
            count = 1,
            originalPath = "original/image-1.png",
            thumbnailPath = "thumb/image-1.png",
            status = ImageGenerationStatus.Completed,
            errorSummary = null,
            createdAt = clock.instant(),
        )

    private fun provider(
        id: String,
        type: ProviderType,
        apiKeyRef: String? = null,
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId(id),
            name = id,
            type = type,
            baseUrl = "https://example.test/v1",
            apiKeyRef = apiKeyRef,
            headers = emptyMap(),
            models = emptyList(),
            defaultModel = "$id-model",
            enabled = true,
        )

    private fun base64Image(bytes: ByteArray): GeneratedImage =
        GeneratedImage(
            base64 = Base64.getEncoder().encodeToString(bytes),
            url = null,
            revisedPrompt = null,
        )
}

@OptIn(ExperimentalCoroutinesApi::class)
class ImageMainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeImageGenerationRepository(
    initialGenerations: List<ImageGeneration>,
) : ImageGenerationRepository {
    val generations = MutableStateFlow(initialGenerations)

    override fun observeImageGenerations(): Flow<List<ImageGeneration>> = generations

    override suspend fun getImageGeneration(id: ImageGenerationId): ImageGeneration? =
        generations.value.firstOrNull { it.id == id }

    override suspend fun saveImageGeneration(imageGeneration: ImageGeneration) {
        generations.value = generations.value.filterNot { it.id == imageGeneration.id } + imageGeneration
    }

    override suspend fun deleteImageGeneration(id: ImageGenerationId) {
        generations.value = generations.value.filterNot { it.id == id }
    }

    override suspend fun deleteAllImageGenerations() {
        generations.value = emptyList()
    }
}

private class FakeImageStorage(
    private val failOnDelete: Boolean = false,
) : ImageStorage {
    var deleted: Boolean = false

    override suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths =
        StoredImagePaths(
            originalPath = "original/${id.value}.png",
            thumbnailPath = "thumb/${id.value}.png",
        )

    override suspend fun deleteAllImages() {
        if (failOnDelete) {
            throw IllegalStateException("无法删除图片文件。")
        }
        deleted = true
    }
}

private class FakeProviderConfigRepository(
    initialProviders: List<ProviderConfig>,
    private val apiKeys: Map<ProviderId, String> = emptyMap(),
) : ProviderConfigRepository {
    private val providers = MutableStateFlow(initialProviders)

    override fun observeProviders(): Flow<List<ProviderConfig>> = providers

    fun replaceProviders(nextProviders: List<ProviderConfig>) {
        providers.value = nextProviders
    }

    override suspend fun getProvider(id: ProviderId): ProviderConfig? = null

    override suspend fun saveProvider(
        provider: ProviderConfig,
        plaintextApiKey: String?,
        preserveExistingApiKey: Boolean,
        deleteReplacedApiKey: Boolean,
    ) {
        providers.value = providers.value.filterNot { it.id == provider.id } + provider
    }

    override suspend fun getApiKey(providerId: ProviderId): String? = apiKeys[providerId]

    override suspend fun deleteApiKeyRef(ref: String) = Unit

    override suspend fun deleteProvider(id: ProviderId) {
        providers.value = providers.value.filterNot { it.id == id }
    }
}

private class NoopImageProvider : ImageGenerationProvider {
    override suspend fun generate(
        request: ImageGenerationProviderRequest,
    ): ImageGenerationProviderResponse =
        error("Image generation should not run in this test.")
}

private class RecordingImageProvider(
    private val response: ImageGenerationProviderResponse = ImageGenerationProviderResponse(emptyList()),
) : ImageGenerationProvider {
    val requests = mutableListOf<ImageGenerationProviderRequest>()

    override suspend fun generate(
        request: ImageGenerationProviderRequest,
    ): ImageGenerationProviderResponse {
        requests += request
        return response
    }
}
