package com.aichat.workbench.provider.image

import com.aichat.workbench.provider.api.ProviderError
import com.aichat.workbench.provider.api.ProviderHttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

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
        val json = JSONObject()
            .put("model", model)
            .put("prompt", prompt)
            .put("n", count)
        size?.takeIf { it.isNotBlank() }?.let { json.put("size", it) }
        quality?.takeIf { it.isNotBlank() }?.let { json.put("quality", it) }

        val builder = Request.Builder()
            .url("${provider.baseUrl.trimEnd('/')}/images/generations")
            .post(json.toString().toRequestBody(JSON))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")

        apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
        provider.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun parseResponse(body: String): ImageGenerationProviderResponse {
        val data = JSONObject(body).getJSONArray("data")
        val images = buildList {
            for (index in 0 until data.length()) {
                val item = data.getJSONObject(index)
                add(
                    GeneratedImage(
                        base64 = item.optString("b64_json").takeIf { it.isNotBlank() },
                        url = item.optString("url").takeIf { it.isNotBlank() },
                        revisedPrompt = item.optString("revised_prompt").takeIf { it.isNotBlank() },
                    ),
                )
            }
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
            JSONObject(body).getJSONObject("error").optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Image generation request failed."
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

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
