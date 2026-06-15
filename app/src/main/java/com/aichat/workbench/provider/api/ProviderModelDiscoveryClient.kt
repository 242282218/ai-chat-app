package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderModelDiscovery
import com.aichat.workbench.domain.model.ProviderModelDiscoveryFormat
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.discoveredModelCapability
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class ProviderModelDiscoveryResult(
    val ok: Boolean,
    val statusCode: Int?,
    val message: String,
    val models: List<ModelConfig> = emptyList(),
)

class ProviderModelDiscoveryClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val providerRegistry: ProviderRegistry,
) {
    suspend fun discover(
        provider: ProviderConfig,
        apiKey: String?,
    ): ProviderModelDiscoveryResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = providerRegistry.descriptor(provider.type)
                if (!providerRegistry.isRegistered(provider.type)) {
                    return@withContext ProviderModelDiscoveryResult(
                        ok = false,
                        statusCode = null,
                        message = "当前 Provider 暂未接入聊天发送：${descriptor.displayName}。",
                    )
                }
                if (descriptor.requiresApiKey && apiKey.isNullOrBlank()) {
                    return@withContext ProviderModelDiscoveryResult(
                        ok = false,
                        statusCode = null,
                        message = "API Key 缺失。",
                    )
                }
                val discovery = descriptor.modelDiscovery
                    ?: return@withContext ProviderModelDiscoveryResult(
                        ok = false,
                        statusCode = null,
                        message = "当前 Provider 暂无模型发现接口。",
                    )

                client.newCall(provider.toModelsRequest(apiKey, discovery)).execute().use { response ->
                    if (response.isSuccessful) {
                        val models = discovery.modelsOrNull(response.bodyText(), provider.type)
                            ?: return@withContext ProviderModelDiscoveryResult(
                                ok = false,
                                statusCode = response.code,
                                message = "模型发现响应无效。",
                            )
                        ProviderModelDiscoveryResult(
                            ok = true,
                            statusCode = response.code,
                            message = models.size.toDiscoveryMessage(),
                            models = models,
                        )
                    } else {
                        ProviderModelDiscoveryResult(
                            ok = false,
                            statusCode = response.code,
                            message = providerHttpFailureMessage(response.code, response.bodyText()),
                        )
                    }
                }
            }.getOrElse { error ->
                ProviderModelDiscoveryResult(
                    ok = false,
                    statusCode = null,
                    message = providerConnectionFailureMessage(error),
                )
            }
        }

    private fun Response.bodyText(): String =
        body?.readErrorBodySafely().orEmpty()

    private fun ProviderModelDiscovery.modelsOrNull(
        body: String,
        providerType: ProviderType,
    ): List<ModelConfig>? =
        runCatching {
            when (responseFormat) {
                ProviderModelDiscoveryFormat.OpenAiModels ->
                    providerJson.decodeFromString<OpenAiModelsResponse>(body).data?.map { it.id }
                ProviderModelDiscoveryFormat.OllamaTags ->
                    providerJson.decodeFromString<OllamaTagsResponse>(body).models?.map { it.name }
            }?.toDiscoveredModelConfigs(providerType)
        }.getOrNull()

    private fun List<String>.toDiscoveredModelConfigs(providerType: ProviderType): List<ModelConfig> =
        map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { id ->
                ModelConfig(
                    id = id,
                    displayName = id,
                    capability = providerType.discoveredModelCapability(id),
                )
            }

    private fun Int.toDiscoveryMessage(): String =
        if (this == 0) "连接正常，未发现模型" else "连接正常，发现 $this 个模型"

    private fun providerConnectionFailureMessage(error: Throwable): String =
        when (error) {
            is IllegalArgumentException -> "Provider URL 无效。"
            is IOException -> error.message.toProviderFailureMessage("Provider 连接失败")
            else -> error.message.toProviderFailureMessage("Provider 连接测试失败")
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
            builder.header("Authorization", "Bearer $key")
        }
        return builder.build()
    }
}

internal fun providerHttpFailureMessage(statusCode: Int, body: String): String {
    val providerMessage = body.toProviderErrorMessageOrNull() ?: return "Provider 返回 HTTP $statusCode"
    return "Provider HTTP $statusCode：$providerMessage"
}

internal fun String?.toProviderFailureMessage(prefix: String): String {
    val preview = this?.toProviderErrorPreview().orEmpty()
    return if (preview.isBlank()) "$prefix。" else "$prefix：$preview"
}

internal fun String.toProviderErrorMessageOrNull(): String? =
    runCatching {
        providerJson.decodeFromString<ProviderErrorEnvelope>(this).error?.message
    }.getOrNull()?.toProviderErrorPreview()?.takeIf { it.isNotBlank() }

private fun String.toProviderErrorPreview(): String {
    val preview = trim().replace(providerErrorWhitespace, " ")
    val suffix = if (preview.length > MAX_PROVIDER_ERROR_PREVIEW_LENGTH) "..." else ""
    return preview.take(MAX_PROVIDER_ERROR_PREVIEW_LENGTH) + suffix
}

private const val MAX_PROVIDER_ERROR_PREVIEW_LENGTH = 240
private val providerErrorWhitespace = Regex("\\s+")

@Serializable
private data class OpenAiModelsResponse(
    val data: List<OpenAiModelItem>? = null,
)

@Serializable
private data class OpenAiModelItem(
    val id: String,
)

@Serializable
private data class OllamaTagsResponse(
    val models: List<OllamaModelItem>? = null,
)

@Serializable
private data class OllamaModelItem(
    val name: String,
)
