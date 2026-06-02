package com.aichat.workbench.feature.image

import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.repository.StoredImagePaths
import com.aichat.workbench.provider.image.ImageGenerationProvider
import com.aichat.workbench.provider.image.ImageGenerationProviderRequest
import com.aichat.workbench.provider.image.ImageGenerationProviderResponse
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

    private fun viewModel(
        repository: ImageGenerationRepository,
        storage: ImageStorage,
    ): ImageGenerationViewModel =
        ImageGenerationViewModel(
            imageRepository = repository,
            providerRepository = EmptyProviderConfigRepository(),
            imageProvider = NoopImageProvider(),
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
}

@OptIn(ExperimentalCoroutinesApi::class)
private class ImageMainDispatcherRule(
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

private class EmptyProviderConfigRepository : ProviderConfigRepository {
    override fun observeProviders(): Flow<List<ProviderConfig>> = flowOf(emptyList())

    override suspend fun getProvider(id: ProviderId): ProviderConfig? = null

    override suspend fun saveProvider(provider: ProviderConfig, plaintextApiKey: String?) = Unit

    override suspend fun getApiKey(providerId: ProviderId): String? = null

    override suspend fun deleteProvider(id: ProviderId) = Unit
}

private class NoopImageProvider : ImageGenerationProvider {
    override suspend fun generate(
        request: ImageGenerationProviderRequest,
    ): ImageGenerationProviderResponse =
        error("Image generation should not run in this test.")
}
