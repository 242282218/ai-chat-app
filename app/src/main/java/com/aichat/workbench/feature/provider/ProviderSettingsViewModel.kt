package com.aichat.workbench.feature.provider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import com.aichat.workbench.provider.DEFAULT_OPENAI_BASE_URL
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.rolePreferenceModel
import com.aichat.workbench.provider.api.ProviderConnectionTester
import com.aichat.workbench.provider.api.ProviderModelDiscoveryClient
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ProviderSettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val modelRolePreferences: List<ModelRolePreference> = emptyList(),
    val editingId: String? = null,
    val name: String = "OpenAI",
    val type: ProviderType = ProviderType.OpenAI,
    val baseUrl: String = DEFAULT_OPENAI_BASE_URL,
    val model: String = "",
    val imageModel: String = "",
    val models: List<ModelConfig> = emptyList(),
    val apiKey: String = "",
    val hasStoredKey: Boolean = false,
    val headers: String = "",
    val enabled: Boolean = true,
    val allowHttp: Boolean = false,
    val message: String? = null,
    val isSaving: Boolean = false,
    val isTestingConnection: Boolean = false,
    val isRefreshingModels: Boolean = false,
    val showProviderEditor: Boolean = false,
    val pendingResetForm: Boolean = false,
    val pendingLoadProviderId: String? = null,
    val pendingDeleteProviderId: String? = null,
) {
    val hasProviderDraft: Boolean get() = editingId != null ||
        name != "OpenAI" || type != ProviderType.OpenAI ||
        baseUrl != DEFAULT_OPENAI_BASE_URL || model.isNotBlank() ||
        imageModel.isNotBlank() || models.isNotEmpty() || apiKey.isNotBlank() ||
        headers.isNotBlank() || !enabled || allowHttp || hasStoredKey

    val pendingLoadProvider: ProviderConfig? get() =
        pendingLoadProviderId?.let { id -> providers.firstOrNull { it.id.value == id } }

    val pendingDeleteProvider: ProviderConfig? get() =
        pendingDeleteProviderId?.let { id -> providers.firstOrNull { it.id.value == id } }

    val saveStatus: ProviderActionStatus get() = providerSaveStatus(
        name = name,
        type = type,
        baseUrl = baseUrl,
        apiKey = apiKey,
        hasStoredKey = hasStoredKey,
        headers = headers,
        enabled = enabled,
        allowHttp = allowHttp,
    )

    val testStatus: ProviderActionStatus get() = providerTestStatus(
        type = type,
        baseUrl = baseUrl,
        apiKey = apiKey,
        hasStoredKey = hasStoredKey,
        headers = headers,
        allowHttp = allowHttp,
    )

    val hasActiveProviderOperation: Boolean get() = isSaving || isTestingConnection || isRefreshingModels
}

internal class ProviderSettingsViewModel(
    private val providerRepository: ProviderConfigRepository,
    private val modelRolePreferenceRepository: ModelRolePreferenceRepository,
    private val connectionTester: ProviderConnectionTester,
    private val modelDiscoveryClient: ProviderModelDiscoveryClient,
    private val saveProviderConfigUseCase: SaveProviderConfigUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(ProviderSettingsUiState())
    val state: StateFlow<ProviderSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                providerRepository.observeProviders(),
                modelRolePreferenceRepository.observeAllRolePreferences(),
            ) { providers, rolePreferences ->
                providers to rolePreferences
            }.collect { (providers, rolePreferences) ->
                _state.update { it.copy(providers = providers, modelRolePreferences = rolePreferences) }
            }
        }
    }

    fun openNewProviderEditor() {
        resetForm()
        _state.update { it.copy(showProviderEditor = true) }
    }

    fun requestLoadProvider(provider: ProviderConfig) {
        if (_state.value.hasProviderDraft) {
            _state.update { it.copy(pendingLoadProviderId = provider.id.value) }
        } else {
            loadProvider(provider)
            _state.update { it.copy(showProviderEditor = true) }
        }
    }

    fun confirmLoadProvider() {
        val pendingId = _state.value.pendingLoadProviderId ?: return
        val provider = _state.value.providers.firstOrNull { it.id.value == pendingId } ?: return
        loadProvider(provider)
        _state.update { it.copy(pendingLoadProviderId = null, showProviderEditor = true) }
    }

    fun dismissLoadProvider() {
        _state.update { it.copy(pendingLoadProviderId = null) }
    }

    fun requestResetForm() {
        if (_state.value.hasProviderDraft) {
            _state.update { it.copy(pendingResetForm = true) }
        } else {
            resetForm()
            _state.update { it.copy(showProviderEditor = true) }
        }
    }

    fun confirmResetForm() {
        resetForm()
        _state.update { it.copy(pendingResetForm = false, showProviderEditor = true) }
    }

    fun dismissResetForm() {
        _state.update { it.copy(pendingResetForm = false) }
    }

    fun requestDeleteProvider(id: String) {
        _state.update { it.copy(pendingDeleteProviderId = id) }
    }

    fun dismissDeleteProvider() {
        _state.update { it.copy(pendingDeleteProviderId = null) }
    }

    fun confirmDeleteProvider() {
        val pendingId = _state.value.pendingDeleteProviderId ?: return
        val provider = _state.value.providers.firstOrNull { it.id.value == pendingId } ?: return
        viewModelScope.launch {
            providerRepository.deleteProvider(provider.id)
            if (_state.value.editingId == provider.id.value) {
                resetForm()
            }
        }
        _state.update { it.copy(pendingDeleteProviderId = null) }
    }

    fun dismissEditor() {
        _state.update { it.copy(showProviderEditor = false) }
    }

    fun updateName(value: String) = _state.update { it.copy(name = value) }

    fun updateType(value: ProviderType) = _state.update { it.copy(type = value) }

    fun updateBaseUrl(value: String) = _state.update { it.copy(baseUrl = value) }

    fun updateModel(value: String) = _state.update { it.copy(model = value) }

    fun updateImageModel(value: String) = _state.update { it.copy(imageModel = value) }

    fun updateApiKey(value: String) = _state.update { it.copy(apiKey = value) }

    fun updateHeaders(value: String) = _state.update { it.copy(headers = value) }

    fun updateEnabled(value: Boolean) = _state.update { it.copy(enabled = value) }

    fun updateAllowHttp(value: Boolean) = _state.update { it.copy(allowHttp = value) }

    fun applyProviderType(nextType: ProviderType) {
        val descriptor = ProviderRegistry.builtInDescriptor(nextType)
        _state.update { current ->
            val newName = if (current.editingId == null) {
                descriptor?.displayName ?: nextType.providerTypeLabel()
            } else {
                current.name
            }
            val newBaseUrl = if (current.editingId == null) {
                descriptor?.defaultBaseUrl.orEmpty()
            } else {
                current.baseUrl
            }
            val newAllowHttp = if (current.editingId == null) {
                descriptor?.defaultBaseUrl?.startsWith("http://") == true
            } else {
                current.allowHttp
            }
            current.copy(
                type = nextType,
                name = newName,
                baseUrl = newBaseUrl,
                allowHttp = newAllowHttp,
                models = emptyList(),
                model = "",
                imageModel = "",
            )
        }
    }

    fun saveProvider() {
        val current = _state.value
        if (current.hasActiveProviderOperation) return
        val provider = currentProvider()
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                val discoveredModels = if (current.testStatus.isReady) {
                    discoverModelsFor(provider)
                } else {
                    null
                }
                val providerToSave = if (discoveredModels == null) {
                    provider
                } else {
                    val defaultModel = current.model.trim().ifBlank {
                        discoveredModels.preferredDiscoveredChatModel()
                    }
                    provider.copy(
                        models = discoveredModels
                            .withManualModel(current.type, defaultModel)
                            .withManualImageModel(current.imageModel),
                        defaultModel = defaultModel.ifBlank { null },
                    )
                }
                saveProviderConfigUseCase(providerToSave, current.apiKey.trim(), current.allowHttp)
                saveRolePreferences(providerToSave.id)
            }.onSuccess {
                resetForm()
                _state.update { it.copy(showProviderEditor = false, isSaving = false) }
            }.onFailure { error ->
                _state.update { it.copy(message = error.message ?: "保存失败", isSaving = false) }
            }
        }
    }

    fun testConnection() {
        val current = _state.value
        if (current.hasActiveProviderOperation) return
        val provider = currentProvider()
        if (provider.baseUrl.startsWith("http://", ignoreCase = true) && !current.allowHttp) {
            _state.update { it.copy(message = "测试此 URL 前请先允许 HTTP。") }
            return
        }
        _state.update { it.copy(isTestingConnection = true, message = "测试中...") }
        viewModelScope.launch {
            try {
                runCatching {
                    val storedKey = if (current.apiKey.isBlank()) {
                        providerRepository.getApiKey(provider.id)
                    } else {
                        null
                    }
                    connectionTester.test(
                        provider = provider,
                        apiKey = current.apiKey.trim().ifBlank { storedKey.orEmpty() },
                    )
                }.onSuccess { result ->
                    _state.update {
                        it.copy(
                            message = if (result.ok) {
                                "${result.message} (${result.statusCode})"
                            } else {
                                result.message
                            },
                        )
                    }
                }.onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "模型连接测试失败。") }
                }
            } finally {
                _state.update { it.copy(isTestingConnection = false) }
            }
        }
    }

    fun refreshModels() {
        val current = _state.value
        if (current.hasActiveProviderOperation) return
        val provider = currentProvider()
        if (provider.baseUrl.startsWith("http://", ignoreCase = true) && !current.allowHttp) {
            _state.update { it.copy(message = "刷新模型前请先允许 HTTP。") }
            return
        }
        _state.update { it.copy(isRefreshingModels = true, message = "刷新模型中...") }
        viewModelScope.launch {
            try {
                runCatching {
                    discoverModelsFor(provider)
                }.onSuccess { discoveredModels ->
                    discoveredModels?.let {
                        _state.update { state ->
                            state.copy(
                                models = it,
                                model = if (state.model.isBlank()) {
                                    it.preferredDiscoveredChatModel()
                                } else {
                                    state.model
                                },
                            )
                        }
                    }
                }.onFailure { error ->
                    _state.update { it.copy(message = error.message ?: "刷新模型失败。") }
                }
            } finally {
                _state.update { it.copy(isRefreshingModels = false) }
            }
        }
    }

    private fun loadProvider(provider: ProviderConfig) {
        _state.update { current ->
            current.copy(
                editingId = provider.id.value,
                name = provider.name,
                type = provider.type,
                baseUrl = provider.baseUrl,
                model = provider.rolePreferenceModel(current.modelRolePreferences, ModelRole.Chat)
                    .orEmpty()
                    .ifBlank { provider.defaultModel.orEmpty() },
                imageModel = provider.rolePreferenceModel(current.modelRolePreferences, ModelRole.Image)
                    .orEmpty()
                    .ifBlank { provider.models.explicitImageModel() },
                models = provider.models,
                apiKey = "",
                hasStoredKey = provider.apiKeyRef != null,
                headers = provider.headers.entries.joinToString("\n") { (key, value) -> "$key: $value" },
                enabled = provider.enabled,
                allowHttp = provider.baseUrl.startsWith("http://", ignoreCase = true),
                message = null,
                isSaving = false,
                isTestingConnection = false,
                isRefreshingModels = false,
            )
        }
    }

    private fun resetForm() {
        _state.update {
            it.copy(
                editingId = null,
                name = "OpenAI",
                type = ProviderType.OpenAI,
                baseUrl = DEFAULT_OPENAI_BASE_URL,
                model = "",
                imageModel = "",
                models = emptyList(),
                apiKey = "",
                hasStoredKey = false,
                headers = "",
                enabled = true,
                allowHttp = false,
                message = null,
                isSaving = false,
                isTestingConnection = false,
                isRefreshingModels = false,
            )
        }
    }

    private fun currentProvider(): ProviderConfig {
        val current = _state.value
        val providerId = ProviderId(current.editingId ?: UUID.randomUUID().toString())
        val trimmedModel = current.model.trim()
        val trimmedImageModel = current.imageModel.trim()
        val normalizedModels = current.models
            .withManualModel(current.type, trimmedModel)
            .withManualImageModel(trimmedImageModel)
        return ProviderConfig(
            id = providerId,
            name = current.name.trim(),
            type = current.type,
            baseUrl = current.baseUrl.trim().trimEnd('/'),
            apiKeyRef = null,
            headers = parseHeaderLines(current.headers),
            models = normalizedModels,
            defaultModel = trimmedModel.ifBlank { null },
            enabled = current.enabled,
        )
    }

    private suspend fun saveRolePreferences(providerId: ProviderId) {
        val current = _state.value
        modelRolePreferenceRepository.setRoleModel(providerId, ModelRole.Chat, current.model)
        modelRolePreferenceRepository.setRoleModel(providerId, ModelRole.Image, current.imageModel)
    }

    private suspend fun discoverModelsFor(provider: ProviderConfig): List<ModelConfig>? {
        val current = _state.value
        val storedKey = if (current.apiKey.isBlank()) providerRepository.getApiKey(provider.id) else null
        val result = modelDiscoveryClient.discover(
            provider = provider,
            apiKey = current.apiKey.trim().ifBlank { storedKey.orEmpty() },
        )
        _state.update {
            it.copy(
                message = if (result.ok) {
                    "${result.message}，保存后可用于聊天。"
                } else {
                    result.message
                },
            )
        }
        return result.models.takeIf { result.ok }
    }
}
