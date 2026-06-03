package com.aichat.workbench.provider.image

import com.aichat.workbench.provider.api.ProviderError
import com.aichat.workbench.provider.api.ProviderErrorEnvelope
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.openAiApiBaseUrl
import com.aichat.workbench.provider.api.providerJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class OpenAiImageGenerationProvider(
    private val client: OkHttpClient = OkHttpClient(),
) : ImageGenerationProvider {
    override suspend fun generate(
        request: ImageGenerationProviderRequest,
    ): ImageGenerationProviderResponse =
        withContext(Dispatchers.IO) {
            val response = client.newCall(request.toHttpRequest()).execute()
            response.use {
                it.requireSuccessful()
                parseResponse(it.bodyText())
            }
    }

    private fun ImageGenerationProviderRequest.toHttpRequest(): Request {
        val body = OpenAiImageRequest(
            model = model,
            prompt = prompt,
            count = count,
            size = size?.takeIf { it.isNotBlank() },
            quality = quality?.takeIf { it.isNotBlank() },
        )

        val builder = Request.Builder()
            .url("${provider.openAiApiBaseUrl()}/images/generations")
            .post(providerJson.encodeToString(body).toRequestBody(JSON))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

        apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        provider.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun parseResponse(body: String): ImageGenerationProviderResponse {
        val response = providerJson.decodeFromString<OpenAiImageResponse>(body)
        val images = response.data.map { item ->
            GeneratedImage(
                base64 = item.base64?.takeIf { it.isNotBlank() }
                    ?: item.url?.takeIf { it.isNotBlank() }?.let(::downloadImageAsBase64),
                url = item.url?.takeIf { it.isNotBlank() },
                revisedPrompt = item.revisedPrompt?.takeIf { it.isNotBlank() },
            )
        }
        return ImageGenerationProviderResponse(images = images)
    }

    private fun Response.requireSuccessful() {
        if (isSuccessful) return
        throw ProviderHttpException(parseHttpError(code, bodyText()))
    }

    private fun Response.bodyText(): String =
        body?.string().orEmpty()

    private fun parseHttpError(statusCode: Int, body: String): ProviderError {
        val message = runCatching {
            providerJson.decodeFromString<ProviderErrorEnvelope>(body).error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "图片生成请求失败：HTTP $statusCode。"
        val code = when (statusCode) {
            401 -> "authentication_failed"
            429 -> "rate_limited"
            in 500..599 -> "provider_unavailable"
            else -> "provider_error"
        }
        return ProviderError(
            code = code,
            message = message,
            statusCode = statusCode,
            retryable = statusCode == 429 || statusCode in 500..599,
        )
    }

    private fun downloadImageAsBase64(url: String): String {
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .get()
                .header("Accept", "image/*")
                .build(),
        ).execute()
        response.use {
            if (!it.isSuccessful) {
                throw ProviderHttpException(
                    ProviderError(
                        code = if (it.code in 500..599) "provider_unavailable" else "provider_error",
                        message = "图片 URL 下载失败：HTTP ${it.code}。",
                        statusCode = it.code,
                        retryable = it.code == 429 || it.code in 500..599,
                    ),
                )
            }
            val bytes = it.body?.bytes().orEmpty()
            require(bytes.isNotEmpty()) { "图片 URL 下载结果为空。" }
            return Base64.getEncoder().encodeToString(bytes)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class OpenAiImageRequest(
    val model: String,
    val prompt: String,
    @SerialName("n") val count: Int,
    val size: String? = null,
    val quality: String? = null,
)

@Serializable
private data class OpenAiImageResponse(
    val data: List<OpenAiImageItem> = emptyList(),
)

@Serializable
private data class OpenAiImageItem(
    @SerialName("b64_json") val base64: String? = null,
    val url: String? = null,
    @SerialName("revised_prompt") val revisedPrompt: String? = null,
)
