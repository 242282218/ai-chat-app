package com.aichat.workbench.provider.api

import com.aichat.workbench.domain.model.ProviderConfig
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
) {
    suspend fun test(
        provider: ProviderConfig,
        apiKey: String?,
    ): ProviderConnectionResult =
        withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(provider.toModelsRequest(apiKey)).execute().use { response ->
                    if (response.isSuccessful) {
                        ProviderConnectionResult(
                            ok = true,
                            statusCode = response.code,
                            message = "Connection OK",
                        )
                    } else {
                        ProviderConnectionResult(
                            ok = false,
                            statusCode = response.code,
                            message = "Provider returned HTTP ${response.code}",
                        )
                    }
                }
            }.getOrElse { error ->
                ProviderConnectionResult(
                    ok = false,
                    statusCode = null,
                    message = when (error) {
                        is IllegalArgumentException -> "Provider URL is invalid."
                        is IOException -> error.message ?: "Provider connection failed."
                        else -> error.message ?: "Provider connection failed."
                    },
                )
            }
        }

    private fun ProviderConfig.toModelsRequest(apiKey: String?): Request {
        val builder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/models")
            .get()
            .header("Accept", "application/json")

        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) {
                builder.header(name, value)
            }
        }
        apiKey?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return builder.build()
    }
}
