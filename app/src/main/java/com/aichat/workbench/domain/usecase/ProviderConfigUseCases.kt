package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import java.net.URI
import kotlinx.coroutines.flow.Flow

class ObserveProvidersUseCase(
    private val repository: ProviderConfigRepository,
) {
    operator fun invoke(): Flow<List<ProviderConfig>> =
        repository.observeProviders()
}

class SaveProviderConfigUseCase(
    private val repository: ProviderConfigRepository,
) {
    suspend operator fun invoke(
        provider: ProviderConfig,
        plaintextApiKey: String?,
        allowInsecureHttp: Boolean,
    ) {
        val normalizedProvider = provider.normalizedForSave()
        val normalizedApiKey = plaintextApiKey?.trim()?.takeIf { it.isNotBlank() }
        require(normalizedProvider.name.isNotBlank()) { "Provider name must not be blank." }
        require(!normalizedProvider.enabled || normalizedProvider.baseUrl.isValidBaseUrl(allowInsecureHttp)) {
            "Provider base URL must be HTTPS unless HTTP is explicitly allowed."
        }
        require(normalizedProvider.models.all { it.id.isNotBlank() }) { "Model names must not be blank." }
        require(normalizedProvider.models.map { it.id }.distinct().size == normalizedProvider.models.size) {
            "Model names must be unique."
        }
        repository.saveProvider(normalizedProvider, normalizedApiKey)
    }

    private fun ProviderConfig.normalizedForSave(): ProviderConfig =
        copy(
            name = name.trim(),
            baseUrl = baseUrl.trim().trimEnd('/'),
            models = models.map { model ->
                val capability = model.capability
                val modelId = model.id.trim()
                model.copy(
                    id = modelId,
                    displayName = model.displayName.trim().ifBlank { modelId },
                    capability = capability?.copy(model = capability.model.trim()),
                )
            },
            defaultModel = defaultModel?.trim()?.takeIf { it.isNotBlank() },
        )

    private fun String.isValidBaseUrl(allowInsecureHttp: Boolean): Boolean {
        val uri = runCatching { URI(this) }.getOrNull() ?: return false
        return when (uri.scheme?.lowercase()) {
            "https" -> uri.host != null
            "http" -> allowInsecureHttp && uri.host != null
            else -> false
        }
    }
}

class DeleteProviderConfigUseCase(
    private val repository: ProviderConfigRepository,
) {
    suspend operator fun invoke(id: ProviderId) {
        repository.deleteProvider(id)
    }
}

class GetProviderApiKeyUseCase(
    private val repository: ProviderConfigRepository,
) {
    suspend operator fun invoke(id: ProviderId): String? =
        repository.getApiKey(id)
}
