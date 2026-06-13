package com.aichat.workbench.provider.api

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun ProviderError.recoverySummary(): String =
    buildString {
        append(message)
        append("（")
        append("code: ")
        append(code)
        statusCode?.let { append("，HTTP $it") }
        append(if (retryable) "，可重试" else "，需检查配置")
        append("）")
        recoveryHint()?.let { hint ->
            append(" ")
            append(hint)
        }
    }

private fun ProviderError.recoveryHint(): String? =
    when {
        statusCode == 401 -> "请检查 Provider、Base URL、模型和 API Key。"
        statusCode == 429 -> "请求被限流，请稍后重试或切换模型/Provider。"
        statusCode?.let { it in 500..599 } == true -> "Provider 服务端异常，请稍后重试或切换 Provider。"
        retryable -> "请稍后重试。"
        else -> null
    }

fun Throwable.providerFailureSummary(fallbackMessage: String): String =
    when (this) {
        is ProviderHttpException -> error.recoverySummary()
        is UnknownHostException -> "Provider 网络不可达，无法解析服务地址。请检查网络连接、Base URL 或 DNS 后重试。"
        is SocketTimeoutException -> "Provider 请求超时。请稍后重试，或切换网络、模型/Provider。"
        is ConnectException -> "Provider 连接失败。请检查网络连接、Base URL、代理或服务可用性后重试。"
        is IOException -> {
            val detail = message?.takeIf { it.isNotBlank() }?.let { "：$it" }.orEmpty()
            "Provider 网络请求失败$detail。请检查网络连接后重试。"
        }
        else -> message ?: fallbackMessage
    }
