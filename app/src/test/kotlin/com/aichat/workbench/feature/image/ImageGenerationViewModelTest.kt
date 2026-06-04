package com.aichat.workbench.feature.image

import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ModelRolePreferenceId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ImageGenerationPreferences
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
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

    @Test
    fun loadsSavedImageProviderAndModel() = runTest(mainDispatcherRule.testDispatcher) {
        val openAiProvider = provider("openai", ProviderType.OpenAI)
        val newApiProvider = provider("new-api", ProviderType.NewApi)
        val preferencesRepository = FakeImageGenerationPreferencesRepository(
            ImageGenerationPreferences(providerId = newApiProvider.id.value, model = "dall-e-3"),
        )
        val viewModel = viewModel(
            repository = FakeImageGenerationRepository(emptyList()),
            storage = FakeImageStorage(),
            providerRepository = FakeProviderConfigRepository(listOf(openAiProvider, newApiProvider)),
            preferencesRepository = preferencesRepository,
        )
        advanceUntilIdle()

        assertEquals(newApiProvider.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("dall-e-3", viewModel.state.value.model)
    }

    @Test
    fun loadsImageRoleModelBeforeSavedImagePreferences() = runTest(mainDispatcherRule.testDispatcher) {
        val openAiProvider = provider(
            id = "openai",
            type = ProviderType.OpenAI,
            models = listOf(
                model("gpt-image-1", text = false, imageGeneration = true),
                model("role-image-model", text = false, imageGeneration = true),
            ),
        )
        val preferencesRepository = FakeImageGenerationPreferencesRepository(
            ImageGenerationPreferences(providerId = openAiProvider.id.value, model = "gpt-image-1"),
        )
        val roleRepository = FakeModelRolePreferenceRepository(
            listOf(
                ModelRolePreference(
                    id = ModelRolePreferenceId("openai:Image"),
                    providerId = openAiProvider.id,
                    role = ModelRole.Image,
                    model = "role-image-model",
                    updatedAt = clock.instant(),
                ),
            ),
        )
        val viewModel = viewModel(
            repository = FakeImageGenerationRepository(emptyList()),
            storage = FakeImageStorage(),
            providerRepository = FakeProviderConfigRepository(listOf(openAiProvider)),
            preferencesRepository = preferencesRepository,
            modelRolePreferenceRepository = roleRepository,
        )
        advanceUntilIdle()

        assertEquals(openAiProvider.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("role-image-model", viewModel.state.value.model)
    }

    @Test
    fun imageProviderDefaultsToImageModelInsteadOfChatDefaultModel() = runTest(mainDispatcherRule.testDispatcher) {
        val newApiProvider = provider("new-api", ProviderType.NewApi, defaultModel = "codex-auto-review")
        val viewModel = viewModel(
            repository = FakeImageGenerationRepository(emptyList()),
            storage = FakeImageStorage(),
            providerRepository = FakeProviderConfigRepository(listOf(newApiProvider)),
        )
        advanceUntilIdle()

        assertEquals(newApiProvider.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("gpt-image-1", viewModel.state.value.model)
    }

    @Test
    fun imageProviderSelectionSkipsDiscoveredTextOnlyModels() = runTest(mainDispatcherRule.testDispatcher) {
        val chatOnlyProvider = provider(
            id = "chat-new-api",
            type = ProviderType.NewApi,
            defaultModel = "gpt-5.4",
            models = listOf(model("gpt-5.4", text = true, imageGeneration = false)),
        )
        val imageProvider = provider(
            id = "image-new-api",
            type = ProviderType.NewApi,
            defaultModel = "gpt-image-2",
            models = listOf(
                model("gpt-image-1.5", text = false, imageGeneration = true),
                model("gpt-image-2", text = false, imageGeneration = true),
            ),
        )
        val viewModel = viewModel(
            repository = FakeImageGenerationRepository(emptyList()),
            storage = FakeImageStorage(),
            providerRepository = FakeProviderConfigRepository(listOf(chatOnlyProvider, imageProvider)),
        )
        advanceUntilIdle()

        assertEquals(listOf(imageProvider), viewModel.state.value.providers)
        assertEquals(imageProvider.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("gpt-image-2", viewModel.state.value.model)
    }

    @Test
    fun selectProviderPersistsImageProviderAndDefaultImageModel() = runTest(mainDispatcherRule.testDispatcher) {
        val openAiProvider = provider("openai", ProviderType.OpenAI)
        val newApiProvider = provider("new-api", ProviderType.NewApi)
        val preferencesRepository = FakeImageGenerationPreferencesRepository()
        val viewModel = viewModel(
            repository = FakeImageGenerationRepository(emptyList()),
            storage = FakeImageStorage(),
            providerRepository = FakeProviderConfigRepository(listOf(openAiProvider, newApiProvider)),
            preferencesRepository = preferencesRepository,
        )
        advanceUntilIdle()

        viewModel.selectProvider(newApiProvider.id.value)
        advanceUntilIdle()

        assertEquals(newApiProvider.id.value, preferencesRepository.preferences.value.providerId)
        assertEquals("gpt-image-1", preferencesRepository.preferences.value.model)
    }

    @Test
    fun updateModelPersistsImageProviderAndModel() = runTest(mainDispatcherRule.testDispatcher) {
        val openAiProvider = provider("openai", ProviderType.OpenAI)
        val preferencesRepository = FakeImageGenerationPreferencesRepository()
        val viewModel = viewModel(
            repository = FakeImageGenerationRepository(emptyList()),
            storage = FakeImageStorage(),
            providerRepository = FakeProviderConfigRepository(listOf(openAiProvider)),
            preferencesRepository = preferencesRepository,
        )
        advanceUntilIdle()

        viewModel.updateModel("gpt-image-custom")
        advanceUntilIdle()

        assertEquals(openAiProvider.id.value, preferencesRepository.preferences.value.providerId)
        assertEquals("gpt-image-custom", preferencesRepository.preferences.value.model)
    }

    @Test
    fun fallsBackWhenSavedImageProviderIsUnavailable() = runTest(mainDispatcherRule.testDispatcher) {
        val openAiProvider = provider("openai", ProviderType.OpenAI)
        val preferencesRepository = FakeImageGenerationPreferencesRepository(
            ImageGenerationPreferences(providerId = "deleted-provider", model = "deleted-model"),
        )
        val viewModel = viewModel(
            repository = FakeImageGenerationRepository(emptyList()),
            storage = FakeImageStorage(),
            providerRepository = FakeProviderConfigRepository(listOf(openAiProvider)),
            preferencesRepository = preferencesRepository,
        )
        advanceUntilIdle()

        assertEquals(openAiProvider.id.value, viewModel.state.value.selectedProviderId)
        assertEquals("gpt-image-1", viewModel.state.value.model)
    }

    private fun viewModel(
        repository: ImageGenerationRepository,
        storage: ImageStorage,
        providerRepository: ProviderConfigRepository = FakeProviderConfigRepository(emptyList()),
        preferencesRepository: ImageGenerationPreferencesRepository = FakeImageGenerationPreferencesRepository(),
        modelRolePreferenceRepository: ModelRolePreferenceRepository = FakeModelRolePreferenceRepository(),
        imageProvider: ImageGenerationProvider = NoopImageProvider(),
    ): ImageGenerationViewModel =
        ImageGenerationViewModel(
            imageRepository = repository,
            providerRepository = providerRepository,
            preferencesRepository = preferencesRepository,
            modelRolePreferenceRepository = modelRolePreferenceRepository,
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
        defaultModel: String = "$id-model",
        models: List<ModelConfig> = emptyList(),
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId(id),
            name = id,
            type = type,
            baseUrl = "https://example.test/v1",
            apiKeyRef = apiKeyRef,
            headers = emptyMap(),
            models = models,
            defaultModel = defaultModel,
            enabled = true,
        )

    private fun model(
        id: String,
        text: Boolean,
        imageGeneration: Boolean,
    ): ModelConfig =
        ModelConfig(
            id = id,
            displayName = id,
            capability = ModelCapability(
                model = id,
                text = text,
                vision = text,
                imageGeneration = imageGeneration,
                toolCalling = text,
                structuredOutput = text,
                longContext = text,
                maxContextTokens = null,
            ),
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

private class FakeImageGenerationPreferencesRepository(
    initialPreferences: ImageGenerationPreferences = ImageGenerationPreferences(),
) : ImageGenerationPreferencesRepository {
    val preferences = MutableStateFlow(initialPreferences)

    override fun observePreferences(): MutableStateFlow<ImageGenerationPreferences> = preferences

    override suspend fun savePreferences(providerId: String?, model: String?) {
        preferences.value = ImageGenerationPreferences(
            providerId = providerId?.takeIf { it.isNotBlank() },
            model = model?.takeIf { it.isNotBlank() },
        )
    }
}

private class FakeModelRolePreferenceRepository(
    initialPreferences: List<ModelRolePreference> = emptyList(),
) : ModelRolePreferenceRepository {
    private val preferences = MutableStateFlow(initialPreferences)

    override fun observeRolePreferences(providerId: ProviderId): Flow<List<ModelRolePreference>> =
        preferences

    override fun observeAllRolePreferences(): Flow<List<ModelRolePreference>> = preferences

    override suspend fun getRoleModel(providerId: ProviderId, role: ModelRole): String? =
        preferences.value.firstOrNull { it.providerId == providerId && it.role == role }?.model

    override suspend fun setRoleModel(providerId: ProviderId, role: ModelRole, model: String?) {
        preferences.value = preferences.value.filterNot { it.providerId == providerId && it.role == role } +
            listOfNotNull(
                model?.trim()?.takeIf { it.isNotBlank() }?.let {
                    ModelRolePreference(
                        id = ModelRolePreferenceId("${providerId.value}:${role.name}"),
                        providerId = providerId,
                        role = role,
                        model = it,
                        updatedAt = Instant.parse("2026-06-01T00:00:00Z"),
                    )
                },
            )
    }

    override suspend fun deleteForProvider(providerId: ProviderId) {
        preferences.value = preferences.value.filterNot { it.providerId == providerId }
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
