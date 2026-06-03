package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.provider.ProviderRegistry
import okhttp3.OkHttpClient

data class ProviderConnectionResult(
    val ok: Boolean,
    val statusCode: Int?,
    val message: String,
)

class ProviderConnectionTester(
    client: OkHttpClient = OkHttpClient(),
    providerRegistry: ProviderRegistry,
) {
    private val discoveryClient = ProviderModelDiscoveryClient(client, providerRegistry)

    suspend fun test(
        provider: ProviderConfig,
        apiKey: String?,
    ): ProviderConnectionResult {
        val result = discoveryClient.discover(provider, apiKey)
        return ProviderConnectionResult(
            ok = result.ok,
            statusCode = result.statusCode,
            message = result.message,
        )
    }
}
