package com.aichat.workbench.feature.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.repository.ImageGenerationPreferences
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.GenerateImageRequest
import com.aichat.workbench.domain.usecase.GenerateImageUseCase
import com.aichat.workbench.provider.DEFAULT_OPENAI_IMAGE_MODEL
import com.aichat.workbench.provider.defaultImageModel
import com.aichat.workbench.provider.requiresApiKey
import com.aichat.workbench.provider.supportsImageGeneration
import com.aichat.workbench.provider.image.ImageGenerationProvider
import java.time.Clock
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageGenerationUiState(
    val generations: List<ImageGeneration> = emptyList(),
    val providers: List<ProviderConfig> = emptyList(),
    val selectedProviderId: String? = null,
    val prompt: String = "",
    val model: String = DEFAULT_OPENAI_IMAGE_MODEL,
    val size: String = "1024x1024",
    val quality: String = "auto",
    val count: String = "1",
    val isGenerating: Boolean = false,
    val error: String? = null,
) {
    val selectedProvider: ProviderConfig?
        get() = selectedProviderId?.let { id -> providers.firstOrNull { it.id.value == id } }

    val selectedModelUnsupported: Boolean
        get() {
            val modelConfig = selectedProvider?.models?.firstOrNull { it.id == model.trim() }
            return modelConfig?.capability?.imageGeneration == false
        }
}

class ImageGenerationViewModel(
    private val imageRepository: ImageGenerationRepository,
    private val providerRepository: ProviderConfigRepository,
    private val preferencesRepository: ImageGenerationPreferencesRepository,
    private val imageProvider: ImageGenerationProvider,
    private val imageStorage: ImageStorage,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(ImageGenerationUiState())
    val state: StateFlow<ImageGenerationUiState> = _state.asStateFlow()
    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            imageRepository.observeImageGenerations().collect { generations ->
                _state.update { it.copy(generations = generations) }
            }
        }
        viewModelScope.launch {
            combine(
                providerRepository.observeProviders(),
                preferencesRepository.observePreferences(),
            ) { providers, preferences -> providers to preferences }
                .collect { (providers, preferences) ->
                    _state.update { current ->
                        current.withImageProviderSelection(providers, preferences)
                    }
                }
        }
    }

    fun selectProvider(id: String) {
        val provider = _state.value.providers.firstOrNull { it.id.value == id }
        val model = provider?.defaultImageModel().orEmpty().ifBlank { _state.value.model }
        _state.update {
            it.copy(
                selectedProviderId = id,
                model = model,
            )
        }
        viewModelScope.launch {
            preferencesRepository.savePreferences(providerId = id, model = model)
        }
    }

    fun updatePrompt(value: String) {
        _state.update { it.copy(prompt = value) }
    }

    fun updateModel(value: String) {
        _state.update { it.copy(model = value) }
        viewModelScope.launch {
            preferencesRepository.savePreferences(
                providerId = _state.value.selectedProviderId,
                model = value,
            )
        }
    }

    fun updateSize(value: String) {
        _state.update { it.copy(size = value) }
    }

    fun updateQuality(value: String) {
        _state.update { it.copy(quality = value) }
    }

    fun updateCount(value: String) {
        _state.update { it.copy(count = value) }
    }

    fun reusePrompt(prompt: String) {
        _state.update { it.copy(prompt = prompt, error = null) }
    }

    fun regenerate(prompt: String) {
        _state.update { it.copy(prompt = prompt, error = null) }
        generate()
    }

    fun generate() {
        if (_state.value.isGenerating) return
        generationJob = viewModelScope.launch {
            val current = _state.value
            val provider = current.selectedProvider
            val imageCount = current.count.trim().toIntOrNull()
            runCatching {
                requireNotNull(provider) { "模型服务未配置。" }
                require(current.prompt.isNotBlank()) { "图片提示词不能为空。" }
                require(provider.supportsImageGeneration()) { "当前模型服务不支持图片生成。" }
                require(!current.selectedModelUnsupported) { "所选模型不支持图片生成。" }
                require(imageCount != null && imageCount in 1..4) { "图片数量必须在 1 到 4 之间。" }
                val apiKey = providerRepository.getApiKey(provider.id)
                if (provider.requiresApiKey()) {
                    require(!apiKey.isNullOrBlank()) { "API Key 缺失。" }
                }

                _state.update { it.copy(isGenerating = true, error = null) }
                GenerateImageUseCase(
                    repository = imageRepository,
                    imageProvider = imageProvider,
                    imageStorage = imageStorage,
                    clock = clock,
                )(
                    GenerateImageRequest(
                        conversationId = null,
                        provider = provider,
                        apiKey = apiKey,
                        model = current.model.trim(),
                        prompt = current.prompt.trim(),
                        size = current.size.trim().ifBlank { null },
                        quality = current.quality.trim().ifBlank { null },
                        count = imageCount,
                    ),
                )
            }.onFailure { error ->
                if (error is CancellationException) {
                    _state.update { it.copy(error = "已停止，提示词和参数已保留，可修改后重新生成。") }
                } else {
                    _state.update { it.copy(error = error.message ?: "图片生成失败。") }
                }
            }
            _state.update { it.copy(isGenerating = false) }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _state.update {
            it.copy(
                isGenerating = false,
                error = "已停止，提示词和参数已保留，可修改后重新生成。",
            )
        }
    }

    fun clearHistory() {
        if (_state.value.isGenerating) return
        viewModelScope.launch {
            runCatching {
                imageStorage.deleteAllImages()
                imageRepository.deleteAllImageGenerations()
            }.onSuccess {
                _state.update { it.copy(error = null) }
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "清空图片历史失败。") }
            }
        }
    }

    private fun ImageGenerationUiState.withImageProviderSelection(
        providers: List<ProviderConfig>,
        preferences: ImageGenerationPreferences,
    ): ImageGenerationUiState {
        val imageProviders = providers.filter { it.supportsImageGeneration() }
        val preferred = preferences.providerId
            ?.let { id -> imageProviders.firstOrNull { it.id.value == id && it.enabled } }
        val selected = selectedProviderId
            ?.let { id -> imageProviders.firstOrNull { it.id.value == id && it.enabled } }
        val fallback = preferred ?: selected ?: imageProviders.firstOrNull { it.enabled }
        val selectedProviderChanged = selectedProviderId != fallback?.id?.value
        return copy(
            providers = imageProviders,
            selectedProviderId = fallback?.id?.value,
            model = selectedImageModel(fallback, selectedProviderChanged, preferences),
        )
    }

    private fun ImageGenerationUiState.selectedImageModel(
        provider: ProviderConfig?,
        selectedProviderChanged: Boolean,
        preferences: ImageGenerationPreferences,
    ): String =
        when {
            provider == null -> model
            preferences.providerId == provider.id.value && !preferences.model.isNullOrBlank() -> preferences.model
            selectedProviderChanged -> provider.defaultImageModel()
            else -> model.ifBlank { provider.defaultImageModel() }
        }
}
