package com.aichat.workbench.tool.runtime

import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.provider.api.ProviderHttpException
import com.aichat.workbench.tool.gateway.GatewayHttpException
import com.aichat.workbench.tool.local.InvalidLocalToolArgumentsException
import com.aichat.workbench.tool.local.LocalToolUnavailableException
import com.aichat.workbench.tool.search.LocalSearchHttpException
import java.time.Instant

internal fun ToolOutput.asModelContent(): String =
    when (this) {
        is ToolOutput.Text -> text
        is ToolOutput.Json -> value
    }

internal fun ToolOutput.rawJsonOrNull(): String? =
    when (this) {
        is ToolOutput.Text -> null
        is ToolOutput.Json -> value
    }

internal fun Instant.durationUntilMs(finishedAt: Instant): Long =
    (finishedAt.toEpochMilli() - toEpochMilli()).coerceAtLeast(0)

internal fun String.toInputSummary(): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    val preview = normalized.take(120)
    return if (normalized.length > preview.length) "$preview..." else preview
}

internal fun Throwable.toToolErrorCode(): String =
    when (this) {
        is ProviderHttpException -> error.code
        is GatewayHttpException -> gatewayCode
        is LocalSearchHttpException -> code
        is GatewaySettingsException -> code
        is InvalidLocalToolArgumentsException -> "invalid_tool_arguments"
        is LocalToolUnavailableException -> "tool_unavailable"
        is InvalidToolArgumentsException -> "invalid_tool_arguments"
        else -> "tool_failed"
    }

internal fun Throwable?.toolErrorStatusCode(): Int? =
    when (this) {
        is ProviderHttpException -> error.statusCode
        is GatewayHttpException -> statusCode
        is LocalSearchHttpException -> statusCode
        else -> null
    }

internal fun Throwable?.toolErrorRetryable(): Boolean? =
    when (this) {
        is ProviderHttpException -> error.retryable
        is GatewayHttpException -> statusCode == 429 || statusCode in 500..599
        is LocalSearchHttpException -> statusCode == 429 || statusCode in 500..599
        else -> null
    }
