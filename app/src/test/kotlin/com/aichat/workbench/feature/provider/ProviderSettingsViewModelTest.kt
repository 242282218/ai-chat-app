package com.aichat.workbench.feature.provider

import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ModelRolePreferenceId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.domain.usecase.SaveProviderConfigUseCase
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.api.ProviderConnectionTester
import com.aichat.workbench.provider.api.ProviderModelDiscoveryClient
import java.time.Instant
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
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = ProviderSettingsMainDispatcherRule()

    @Test
    fun saveExistingProviderWithBlankApiKeyPreservesStoredKey() = runTest(mainDispatcherRule.testDispatcher) {
        val provider = provider(apiKeyRef = "provider:provider-1:api-key")
        val providerRepository = RecordingProviderConfigRepository(
            initialProviders = listOf(provider),
            apiKeys = mapOf(provider.id to "stored-key"),
        )
        val viewModel = viewModel(providerRepository)
        advanceUntilIdle()

        viewModel.requestLoadProvider(provider)
        viewModel.updateName("Renamed Provider")
        viewModel.updateHeaders("Authorization: Bearer should-not-be-saved")
        viewModel.saveProvider()
        advanceUntilIdle()

        assertEquals("Renamed Provider", providerRepository.savedProvider?.name)
        assertNull(providerRepository.savedPlaintextApiKey)
        assertTrue(providerRepository.savedPreserveExistingApiKey == true)
    }

    private fun viewModel(
        providerRepository: RecordingProviderConfigRepository,
        rolePreferenceRepository: ModelRolePreferenceRepository = RecordingModelRolePreferenceRepository(),
    ): ProviderSettingsViewModel {
        val registry = ProviderRegistry()
        val client = OkHttpClient()
        return ProviderSettingsViewModel(
            providerRepository = providerRepository,
            modelRolePreferenceRepository = rolePreferenceRepository,
            connectionTester = ProviderConnectionTester(client = client, providerRegistry = registry),
            modelDiscoveryClient = ProviderModelDiscoveryClient(client = client, providerRegistry = registry),
            saveProviderConfigUseCase = SaveProviderConfigUseCase(providerRepository),
        )
    }

    private fun provider(apiKeyRef: String?): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = "Existing Provider",
            type = ProviderType("legacy_vendor"),
            baseUrl = "https://example.test/v1",
            apiKeyRef = apiKeyRef,
            headers = emptyMap(),
            models = emptyList(),
            defaultModel = null,
            enabled = true,
        )
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderSettingsMainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class RecordingProviderConfigRepository(
    initialProviders: List<ProviderConfig>,
    private val apiKeys: Map<ProviderId, String> = emptyMap(),
) : ProviderConfigRepository {
    private val providers = MutableStateFlow(initialProviders)
    var savedProvider: ProviderConfig? = null
    var savedPlaintextApiKey: String? = null
    var savedPreserveExistingApiKey: Boolean? = null

    override fun observeProviders(): Flow<List<ProviderConfig>> = providers

    override suspend fun getProvider(id: ProviderId): ProviderConfig? =
        providers.value.firstOrNull { it.id == id }

    override suspend fun saveProvider(
        provider: ProviderConfig,
        plaintextApiKey: String?,
        preserveExistingApiKey: Boolean,
        deleteReplacedApiKey: Boolean,
    ) {
        savedProvider = provider
        savedPlaintextApiKey = plaintextApiKey
        savedPreserveExistingApiKey = preserveExistingApiKey
        providers.value = providers.value.filterNot { it.id == provider.id } + provider
    }

    override suspend fun getApiKey(providerId: ProviderId): String? = apiKeys[providerId]

    override suspend fun deleteApiKeyRef(ref: String) = Unit

    override suspend fun deleteProvider(id: ProviderId) {
        providers.value = providers.value.filterNot { it.id == id }
    }
}

private class RecordingModelRolePreferenceRepository : ModelRolePreferenceRepository {
    private val preferences = MutableStateFlow<List<ModelRolePreference>>(emptyList())

    override fun observeAllRolePreferences(): Flow<List<ModelRolePreference>> = preferences

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
}
