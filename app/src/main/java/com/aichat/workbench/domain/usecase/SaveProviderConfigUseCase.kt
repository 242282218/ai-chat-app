package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.isValidProviderBaseUrl
import com.aichat.workbench.domain.repository.ProviderConfigRepository

class SaveProviderConfigUseCase(
    private val repository: ProviderConfigRepository,
) {
    suspend operator fun invoke(
        provider: ProviderConfig,
        plaintextApiKey: String?,
        allowInsecureHttp: Boolean,
        preserveExistingApiKey: Boolean = true,
    ) {
        val normalizedProvider = provider.normalizedForSave()
        val normalizedApiKey = plaintextApiKey?.trim()?.takeIf { it.isNotBlank() }
        require(normalizedProvider.name.isNotBlank()) { "Provider name must not be blank." }
        require(!normalizedProvider.enabled || normalizedProvider.baseUrl.isValidBaseUrl(allowInsecureHttp)) {
            "Provider base URL must be HTTPS, except local HTTP when explicitly allowed."
        }
        require(normalizedProvider.models.all { it.id.isNotBlank() }) { "Model names must not be blank." }
        require(normalizedProvider.models.map { it.id }.distinct().size == normalizedProvider.models.size) {
            "Model names must be unique."
        }
        require(
            normalizedProvider.defaultModel == null ||
                normalizedProvider.models.any { it.id == normalizedProvider.defaultModel },
        ) {
            "Default model must exist in model list."
        }
        repository.saveProvider(
            provider = normalizedProvider,
            plaintextApiKey = normalizedApiKey,
            preserveExistingApiKey = preserveExistingApiKey,
            deleteReplacedApiKey = normalizedApiKey != null,
        )
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

    private fun String.isValidBaseUrl(allowInsecureHttp: Boolean): Boolean =
        isValidProviderBaseUrl(allowInsecureHttp)
}
