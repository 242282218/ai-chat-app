package com.aichat.workbench.feature.image

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.repository.ImageGenerationPreferences
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.GenerateImageRequest
import com.aichat.workbench.domain.usecase.GenerateImageUseCase
import com.aichat.workbench.provider.DEFAULT_OPENAI_IMAGE_MODEL
import com.aichat.workbench.provider.defaultImageModel
import com.aichat.workbench.provider.rolePreferenceModel
import com.aichat.workbench.provider.requiresApiKey
import com.aichat.workbench.provider.supportsImageGeneration
import com.aichat.workbench.provider.api.ProviderConnectionTestClient
import com.aichat.workbench.provider.api.providerFailureSummary
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
    val modelRolePreferences: List<ModelRolePreference> = emptyList(),
    val providerApiKeyAvailable: Map<String, Boolean> = emptyMap(),
    val selectedProviderId: String? = null,
    val prompt: String = "",
    val model: String = DEFAULT_OPENAI_IMAGE_MODEL,
    val size: String = "1024x1024",
    val quality: String = "auto",
    val count: String = "1",
    val isGenerating: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionTestMessage: String? = null,
    val connectionTestDiagnostic: String? = null,
    val connectionTestOk: Boolean? = null,
    val error: String? = null,
) {
    val selectedProvider: ProviderConfig?
        get() = selectedProviderId?.let { id -> providers.firstOrNull { it.id.value == id && it.enabled } }

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
    private val modelRolePreferenceRepository: ModelRolePreferenceRepository,
    private val imageProvider: ImageGenerationProvider,
    private val imageStorage: ImageStorage,
    private val connectionTester: ProviderConnectionTestClient,
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
                modelRolePreferenceRepository.observeAllRolePreferences(),
            ) { providers, preferences, rolePreferences ->
                Triple(providers, preferences, rolePreferences)
            }.collect { (providers, preferences, rolePreferences) ->
                val nextState = _state.value.withImageProviderSelection(
                    providers = providers,
                    preferences = preferences,
                    rolePreferences = rolePreferences,
                )
                _state.value = nextState
                refreshProviderApiKeyAvailability(nextState.providers)
            }
        }
    }

    fun selectProvider(id: String) {
        val provider = _state.value.providers.firstOrNull { it.id.value == id && it.enabled } ?: return
        val model = provider
            .rolePreferenceModel(_state.value.modelRolePreferences, ModelRole.Image)
            ?: provider.defaultImageModel().ifBlank { _state.value.model }
        _state.update {
            it.copy(
                selectedProviderId = id,
                model = model,
            ).withDraftFeedbackCleared(clearConnectionTest = true)
        }
        viewModelScope.launch {
            preferencesRepository.saveSelectedProvider(id)
            modelRolePreferenceRepository.setRoleModel(provider.id, ModelRole.Image, model)
        }
    }

    fun updatePrompt(value: String) {
        _state.update { it.copy(prompt = value).withDraftFeedbackCleared() }
    }

    fun updateModel(value: String) {
        _state.update { it.copy(model = value).withDraftFeedbackCleared(clearConnectionTest = true) }
        viewModelScope.launch {
            val provider = _state.value.selectedProvider
            preferencesRepository.saveSelectedProvider(provider?.id?.value)
            provider?.let {
                modelRolePreferenceRepository.setRoleModel(it.id, ModelRole.Image, value)
            }
        }
    }

    fun updateSize(value: String) {
        _state.update { it.copy(size = value).withDraftFeedbackCleared() }
    }

    fun updateQuality(value: String) {
        _state.update { it.copy(quality = value).withDraftFeedbackCleared() }
    }

    fun updateCount(value: String) {
        _state.update { it.copy(count = value).withDraftFeedbackCleared() }
    }

    fun reusePrompt(prompt: String) {
        _state.update { it.copy(prompt = prompt, error = null) }
    }

    fun regenerate(prompt: String) {
        _state.update { it.copy(prompt = prompt, error = null) }
        generate()
    }

    fun regenerate(generation: ImageGeneration) {
        _state.update {
            val historyProviderAvailable = generation.providerId?.value
                ?.let { id -> it.providers.any { provider -> provider.id.value == id && provider.enabled } } == true
            val providerId = generation.providerId?.value
                ?.takeIf { historyProviderAvailable }
                ?: it.selectedProviderId
            it.copy(
                selectedProviderId = providerId,
                prompt = generation.prompt,
                model = if (historyProviderAvailable) generation.model.orEmpty().ifBlank { it.model } else it.model,
                size = generation.size.orEmpty().ifBlank { it.size },
                quality = generation.quality.orEmpty().ifBlank { it.quality },
                count = generation.count.coerceIn(1, 4).toString(),
            ).withDraftFeedbackCleared(clearConnectionTest = true)
        }
        generate()
    }

    fun generate() {
        if (_state.value.isGenerating) return
        generationJob = viewModelScope.launch {
            val current = _state.value
            val provider = current.selectedProvider
            val imageCount = current.count.trim().toIntOrNull() ?: 1
            runCatching {
                requireNotNull(provider) { "模型服务未配置。" }
                require(current.prompt.isNotBlank()) { "图片提示词不能为空。" }
                require(provider.supportsImageGeneration()) { "当前模型服务不支持图片生成。" }
                require(!current.selectedModelUnsupported) { "所选模型不支持图片生成。" }
                require(imageCount in 1..4) { "图片数量必须在 1 到 4 之间。" }
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
                    throw error
                } else {
                    _state.update { it.copy(error = error.providerFailureSummary("图片生成失败。")) }
                }
            }
            _state.update { it.copy(isGenerating = false) }
        }
    }

    fun testConnection() {
        if (_state.value.isTestingConnection) return
        viewModelScope.launch {
            val provider = _state.value.selectedProvider
            runCatching {
                requireNotNull(provider) { "模型服务未配置。" }
                require(provider.supportsImageGeneration()) { "当前模型服务不支持图片生成。" }
                val apiKey = providerRepository.getApiKey(provider.id)
                if (provider.requiresApiKey()) {
                    require(!apiKey.isNullOrBlank()) { "API Key 缺失。" }
                }
                _state.update {
                    it.copy(
                        isTestingConnection = true,
                        connectionTestMessage = "测试中...",
                        connectionTestDiagnostic = null,
                        connectionTestOk = null,
                        error = null,
                    )
                }
                provider to connectionTester.test(provider, apiKey)
            }.onSuccess { (testedProvider, result) ->
                _state.update {
                    it.copy(
                        isTestingConnection = false,
                        connectionTestMessage = result.message,
                        connectionTestDiagnostic = testedProvider.imageConnectionDiagnostic(
                            model = _state.value.model,
                            ok = result.ok,
                            statusCode = result.statusCode,
                            message = result.message,
                        ),
                        connectionTestOk = result.ok,
                    )
                }
            }.onFailure { error ->
                val message = error.providerFailureSummary("模型连接测试失败。")
                _state.update {
                    it.copy(
                        isTestingConnection = false,
                        connectionTestMessage = message,
                        connectionTestDiagnostic = provider?.imageConnectionDiagnostic(
                            model = _state.value.model,
                            ok = false,
                            statusCode = null,
                            message = message,
                        ),
                        connectionTestOk = false,
                    )
                }
            }
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
                _state.update { it.copy(error = error.message ?: "清空图片历史失败，请重试。") }
            }
        }
    }

    private fun ImageGenerationUiState.withImageProviderSelection(
        providers: List<ProviderConfig>,
        preferences: ImageGenerationPreferences,
        rolePreferences: List<ModelRolePreference>,
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
            modelRolePreferences = rolePreferences,
            selectedProviderId = fallback?.id?.value,
            model = selectedImageModel(fallback, selectedProviderChanged, preferences, rolePreferences),
        )
    }

    private suspend fun refreshProviderApiKeyAvailability(providers: List<ProviderConfig>) {
        val availability = providers.associate { provider ->
            provider.id.value to provider.hasUsableApiKey()
        }
        _state.update { current ->
            current.copy(providerApiKeyAvailable = availability)
        }
    }

    private suspend fun ProviderConfig.hasUsableApiKey(): Boolean =
        !requiresApiKey() || runCatching {
            providerRepository.getApiKey(id)?.isNotBlank() == true
        }.getOrDefault(false)

    private fun ImageGenerationUiState.selectedImageModel(
        provider: ProviderConfig?,
        selectedProviderChanged: Boolean,
        preferences: ImageGenerationPreferences,
        rolePreferences: List<ModelRolePreference>,
    ): String {
        val roleModel = provider?.rolePreferenceModel(rolePreferences, ModelRole.Image)
        return when {
            provider == null -> model
            roleModel != null -> roleModel
            preferences.providerId == provider.id.value && !selectedProviderChanged ->
                model.ifBlank { provider.defaultImageModel() }
            selectedProviderChanged -> provider.defaultImageModel()
            else -> model.ifBlank { provider.defaultImageModel() }
        }
    }
}

private fun ImageGenerationUiState.withDraftFeedbackCleared(
    clearConnectionTest: Boolean = false,
): ImageGenerationUiState {
    val state = copy(error = null)
    if (!clearConnectionTest) return state
    return state.copy(
        connectionTestMessage = null,
        connectionTestDiagnostic = null,
        connectionTestOk = null,
    )
}

internal fun ProviderConfig.imageConnectionDiagnostic(
    model: String,
    ok: Boolean,
    statusCode: Int?,
    message: String,
): String =
    buildString {
        appendLine("图片模型连接测试")
        appendLine("Provider：$name")
        appendLine("类型：$type")
        appendLine("Base URL：${baseUrl.ifBlank { "(默认)" }}")
        appendLine("模型：${model.ifBlank { "(未设置)" }}")
        appendLine("结果：${if (ok) "连接成功" else "连接失败"}")
        statusCode?.let { appendLine("HTTP：$it") }
        append("消息：${message.ifBlank { if (ok) "连接成功" else "连接失败" }}")
    }
