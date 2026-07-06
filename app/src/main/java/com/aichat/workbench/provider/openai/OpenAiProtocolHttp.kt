package com.aichat.workbench.provider.openai

import com.aichat.workbench.domain.model.providerRequestHeaders
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.provider.api.openAiApiHttpUrl
import com.aichat.workbench.provider.api.parseOpenAiHttpError
import com.aichat.workbench.provider.api.readErrorBodySafely
import com.aichat.workbench.provider.api.readJsonBodySafely
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

internal val OPEN_AI_JSON = "application/json; charset=utf-8".toMediaType()

internal fun ChatProviderRequest.openAiPostJson(
    path: String,
    body: String,
    stream: Boolean,
): Request {
    val builder = Request.Builder()
        .url(provider.openAiApiHttpUrl().newBuilder().addPathSegments(path.trim('/')).build())
        .post(body.toRequestBody(OPEN_AI_JSON))
        .header("Accept", if (stream) "text/event-stream" else "application/json")
        .header("Content-Type", "application/json")

    provider.headers.providerRequestHeaders().forEach { (name, value) ->
        builder.header(name, value)
    }
    apiKey?.takeIf { it.isNotBlank() }?.let { builder.header("Authorization", "Bearer $it") }
    return builder.build()
}

internal fun Response.requireSuccessfulProviderResponse() {
    if (isSuccessful) return
    val bodyText = body?.readErrorBodySafely().orEmpty()
    throw ProviderHttpException(parseOpenAiHttpError(code, bodyText))
}

internal fun Response.bodyText(): String =
    requireBody().readJsonBodySafely()

internal fun Response.requireBody() =
    requireNotNull(body) { "Provider 响应 body 为空。" }
