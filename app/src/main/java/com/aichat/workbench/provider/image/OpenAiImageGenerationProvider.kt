package com.aichat.workbench.provider.image

import com.aichat.workbench.provider.api.ProviderError
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.openAiApiBaseUrl
import com.aichat.workbench.provider.api.parseOpenAiHttpError
import com.aichat.workbench.provider.api.providerJson
import com.aichat.workbench.provider.api.readBodyWithLimit
import com.aichat.workbench.provider.api.readErrorBodySafely
import com.aichat.workbench.provider.http.awaitResponse
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OpenAiImageGenerationProvider(
    private val client: OkHttpClient,
) : ImageGenerationProvider {
    override suspend fun generate(
        request: ImageGenerationProviderRequest,
    ): ImageGenerationProviderResponse =
        withContext(Dispatchers.IO) {
            val response = client.newCall(request.toHttpRequest()).awaitResponse()
            response.use {
                it.requireSuccessful()
                val body = requireNotNull(it.body) { "Provider 未返回图片响应。" }
                parseResponse(providerJson.decodeFromString(body.readBodyWithLimit(MAX_IMAGE_RESPONSE_BODY_BYTES)))
            }
        }

    private fun ImageGenerationProviderRequest.toHttpRequest(): Request {
        val builder = Request.Builder()
            .url("${provider.openAiApiBaseUrl()}/images/generations")
            .post(providerJson.encodeToString(toApiBody()).toRequestBody(JSON))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

        provider.headers.forEach { (name, value) ->
            if (name.lowercase() !in FORBIDDEN_HEADERS) {
                builder.header(name, value)
            }
        }
        apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        return builder.build()
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        val FORBIDDEN_HEADERS = setOf("authorization", "x-api-key", "api-key")
        const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024
        const val MAX_IMAGE_RESPONSE_BODY_BYTES = 64 * 1024 * 1024
        const val MAX_BASE64_IMAGE_CHARS = ((MAX_IMAGE_SIZE_BYTES + 2) / 3) * 4 + 128
    }

    private fun ImageGenerationProviderRequest.toApiBody(): OpenAiImageRequest =
        OpenAiImageRequest(
            model = model,
            prompt = prompt,
            count = count,
            size = size?.takeIf { it.isNotBlank() },
            quality = quality?.takeIf { it.isNotBlank() },
            responseFormat = "b64_json",
        )

    private suspend fun parseResponse(response: OpenAiImageResponse): ImageGenerationProviderResponse {
        require(response.data.isNotEmpty()) {
            "Provider 返回空图片列表，可能是模型不支持或参数无效。"
        }
        val images = buildList {
            response.data.forEach { item ->
                add(
                    GeneratedImage(
                        base64 = item.base64?.takeIf { it.isNotBlank() }?.boundedBase64()
                            ?: item.url?.takeIf { it.isNotBlank() }?.let { downloadImageAsBase64(it) },
                        url = item.url?.takeIf { it.isNotBlank() },
                        revisedPrompt = item.revisedPrompt?.takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        return ImageGenerationProviderResponse(images = images)
    }

    private fun okhttp3.Response.requireSuccessful() {
        if (isSuccessful) return
        throw ProviderHttpException(
            parseOpenAiHttpError(
                statusCode = code,
                body = body.readErrorBodySafely(),
                fallbackMessage = "图片生成请求失败：HTTP $code。",
            ),
        )
    }

    private suspend fun downloadImageAsBase64(url: String): String {
        // Validate URL scheme to prevent file:// or other unexpected protocols
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "只支持 HTTP/HTTPS 图片 URL，拒绝: $url"
        }

        val response = client.newCall(
            Request.Builder()
                .url(url)
                .get()
                .header("Accept", "image/*")
                .build(),
        ).awaitResponse()
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

            // Check Content-Type to ensure it's an image
            val contentType = it.body?.contentType()
            require(contentType?.type == "image") {
                "响应非图片类型：$contentType"
            }

            // Check Content-Length before reading to prevent OOM
            val contentLength = it.body?.contentLength() ?: -1
            require(contentLength <= MAX_IMAGE_SIZE_BYTES) {
                "图片大小超过限制：$contentLength bytes (最大 $MAX_IMAGE_SIZE_BYTES bytes)"
            }

            // Read with size limit protection
            val bytes = it.body?.byteStream()?.use { stream ->
                stream.readAtMost(MAX_IMAGE_SIZE_BYTES + 1)
            } ?: ByteArray(0)

            require(bytes.isNotEmpty()) { "图片 URL 下载结果为空。" }
            require(bytes.size <= MAX_IMAGE_SIZE_BYTES) {
                "图片实际大小超过限制：${bytes.size} bytes"
            }

            return kotlin.io.encoding.Base64.Default.encode(bytes).boundedBase64()
        }
    }

    private fun String.boundedBase64(): String {
        require(length <= MAX_BASE64_IMAGE_CHARS) {
            "图片数据超过限制：$length chars"
        }
        return this
    }

    private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        val output = ByteArrayOutputStream(maxBytes.coerceAtMost(DEFAULT_BUFFER_SIZE))
        var remaining = maxBytes
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size, remaining))
            if (read == -1) break
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

}

@Serializable
private data class OpenAiImageRequest(
    val model: String,
    val prompt: String,
    @SerialName("n") val count: Int,
    val size: String? = null,
    val quality: String? = null,
    @SerialName("response_format") val responseFormat: String? = null,
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
