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
        require(provider.name.isNotBlank()) { "Provider name must not be blank." }
        require(provider.baseUrl.isValidBaseUrl(allowInsecureHttp)) {
            "Provider base URL must be HTTPS unless HTTP is explicitly allowed."
        }
        require(provider.models.all { it.id.isNotBlank() }) { "Model names must not be blank." }
        repository.saveProvider(provider, plaintextApiKey?.takeIf { it.isNotBlank() })
    }

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
