package com.aichat.workbench.provider.image

import com.aichat.workbench.provider.api.ProviderError
import com.aichat.workbench.provider.api.ProviderErrorEnvelope
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.openAiApiBaseUrl
import com.aichat.workbench.provider.api.providerJson
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST

class OpenAiImageGenerationProvider(
    private val client: OkHttpClient = OkHttpClient(),
) : ImageGenerationProvider {
    override suspend fun generate(
        request: ImageGenerationProviderRequest,
    ): ImageGenerationProviderResponse =
        withContext(Dispatchers.IO) {
            val response = request.api().generateImages(
                headers = request.headers(),
                body = request.toApiBody(),
            )
            response.requireSuccessful()
            parseResponse(requireNotNull(response.body()) { "Provider 未返回图片响应。" })
        }

    @OptIn(ExperimentalSerializationApi::class)
    private fun ImageGenerationProviderRequest.api(): OpenAiImageApi =
        Retrofit.Builder()
            .baseUrl("${provider.openAiApiBaseUrl()}/")
            .client(client)
            .addConverterFactory(providerJson.asConverterFactory(JSON))
            .build()
            .create(OpenAiImageApi::class.java)

    private fun ImageGenerationProviderRequest.headers(): Map<String, String> =
        buildMap {
            put("Accept", "application/json")
            apiKey?.takeIf { it.isNotBlank() }?.let { put("Authorization", "Bearer $it") }
            provider.headers.forEach { (name, value) -> put(name, value) }
        }

    private fun ImageGenerationProviderRequest.toApiBody(): OpenAiImageRequest =
        OpenAiImageRequest(
            model = model,
            prompt = prompt,
            count = count,
            size = size?.takeIf { it.isNotBlank() },
            quality = quality?.takeIf { it.isNotBlank() },
        )

    private fun parseResponse(response: OpenAiImageResponse): ImageGenerationProviderResponse {
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

    private fun Response<OpenAiImageResponse>.requireSuccessful() {
        if (isSuccessful) return
        throw ProviderHttpException(parseHttpError(code(), errorBody()?.string().orEmpty()))
    }

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
            val bytes = it.body?.bytes() ?: ByteArray(0)
            require(bytes.isNotEmpty()) { "图片 URL 下载结果为空。" }
            return Base64.getEncoder().encodeToString(bytes)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

private interface OpenAiImageApi {
    @POST("images/generations")
    suspend fun generateImages(
        @HeaderMap headers: Map<String, String>,
        @Body body: OpenAiImageRequest,
    ): Response<OpenAiImageResponse>
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
