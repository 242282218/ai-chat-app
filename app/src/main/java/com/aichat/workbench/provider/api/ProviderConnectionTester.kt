package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderModelDiscovery
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.ProviderRegistry
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProviderConnectionResult(
    val ok: Boolean,
    val statusCode: Int?,
    val message: String,
)

class ProviderConnectionTester(
    private val client: OkHttpClient = OkHttpClient(),
    private val providerRegistry: ProviderRegistry = ProviderRegistry(),
) {
    suspend fun test(
        provider: ProviderConfig,
        apiKey: String?,
    ): ProviderConnectionResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = providerRegistry.descriptor(provider.type)
                if (descriptor.requiresApiKey && apiKey.isNullOrBlank()) {
                    return@withContext ProviderConnectionResult(
                        ok = false,
                        statusCode = null,
                        message = "API Key 缺失。",
                    )
                }
                val discovery = descriptor.modelDiscovery
                    ?: return@withContext ProviderConnectionResult(
                        ok = false,
                        statusCode = null,
                        message = "当前 Provider 暂无模型发现接口。",
                    )
                client.newCall(provider.toModelsRequest(apiKey, discovery)).execute().use { response ->
                    if (response.isSuccessful) {
                        ProviderConnectionResult(
                            ok = true,
                            statusCode = response.code,
                            message = "连接正常",
                        )
                    } else {
                        ProviderConnectionResult(
                            ok = false,
                            statusCode = response.code,
                            message = "Provider 返回 HTTP ${response.code}",
                        )
                    }
                }
            }.getOrElse { error ->
                ProviderConnectionResult(
                    ok = false,
                    statusCode = null,
                    message = when (error) {
                        is IllegalArgumentException -> "Provider URL 无效。"
                        is IOException -> error.message ?: "Provider 连接失败。"
                        else -> error.message ?: "Provider 连接失败。"
                    },
                )
            }
        }

    private fun ProviderConfig.toModelsRequest(
        apiKey: String?,
        discovery: ProviderModelDiscovery,
    ): Request {
        val builder = Request.Builder()
            .url("${modelDiscoveryBaseUrl()}/${discovery.path.trimStart('/')}")
            .get()
            .header("Accept", "application/json")

        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                builder.header(name, value)
            }
        }
        apiKey?.takeIf { it.isNotBlank() }?.let { key ->
            when (type) {
                ProviderType.Gemini -> builder.header("x-goog-api-key", key)
                ProviderType.Anthropic -> builder.header("x-api-key", key)
                else -> builder.header("Authorization", "Bearer $key")
            }
        }
        return builder.build()
    }

    private fun ProviderConfig.modelDiscoveryBaseUrl(): String {
        val trimmed = baseUrl.trimEnd('/')
        return if (type == ProviderType.Ollama && trimmed.endsWith("/v1")) {
            trimmed.removeSuffix("/v1")
        } else {
            trimmed
        }
    }
}
