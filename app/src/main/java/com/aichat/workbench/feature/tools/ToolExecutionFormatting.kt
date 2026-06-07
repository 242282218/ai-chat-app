package com.aichat.workbench.feature.tools

import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.tool.gateway.GatewayHttpException
import com.aichat.workbench.tool.gateway.SandboxRunResponse
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.search.LocalSearchHttpException
import com.aichat.workbench.tool.search.SearchConfig
import com.aichat.workbench.tool.search.SearchResponse
import com.aichat.workbench.tool.search.SearchResult
import java.time.Instant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal fun Throwable.toSearchToolError(): ToolError =
    toToolError(
        fallbackCode = "search_failed",
        fallbackMessage = "网络搜索失败。",
    )

internal fun gatewayTokenRequiredError(): ToolError =
    ToolError(
        code = "gateway_token_required",
        message = "Gateway API token 未配置。",
    )

internal fun localSearchKeyRequiredError(): ToolError =
    ToolError(
        code = "local_search_key_required",
        message = "搜索 API Key 未配置。",
    )

internal fun Throwable.toToolError(
    fallbackCode: String,
    fallbackMessage: String,
): ToolError =
    when (this) {
        is GatewayHttpException -> ToolError(
            code = gatewayCode,
            message = message ?: fallbackMessage,
            statusCode = statusCode,
            retryable = statusCode == 429 || statusCode in 500..599,
        )
        is LocalSearchHttpException -> ToolError(
            code = code,
            message = message ?: fallbackMessage,
            statusCode = statusCode,
            retryable = statusCode == 429 || statusCode in 500..599,
        )
        else -> ToolError(
            code = fallbackCode,
            message = message ?: fallbackMessage,
        )
    }

internal fun String.toInputSummary(label: String): String {
    val normalized = replace(Regex("\\s+"), " ").trim()
    val preview = normalized.take(120)
    return if (normalized.length > preview.length) {
        "$label: $preview..."
    } else {
        "$label: $preview"
    }
}

internal fun Instant.durationUntilMs(finishedAt: Instant): Long =
    (finishedAt.toEpochMilli() - toEpochMilli()).coerceAtLeast(0)

internal fun emptySearchOutputJson(query: String, fetchedAt: Instant): String =
    toolsJson.encodeToString(
        SearchOutputJson(
            query = query,
            fetchedAt = fetchedAt.toString(),
            results = emptyList(),
        ),
    )

internal fun emptySandboxOutputJson(): String =
    toolsJson.encodeToString(
        SandboxOutputJson(
            language = "python",
            stdout = "",
            stderr = "",
            exitCode = -1,
            durationMs = 0,
            timedOut = false,
            truncated = false,
        ),
    )

internal fun SandboxRunResponse.toJson(): String =
    toolsJson.encodeToString(toSandboxOutputJson())

internal fun SearchResponse.toJson(): String =
    toolsJson.encodeToString(
        SearchOutputJson(
            query = query,
            fetchedAt = fetchedAt.toString(),
            results = results.map { result -> result.toSearchResultOutputJson() },
        ),
    )

internal fun ToolsUiState.toLocalSearchConfig(apiKey: String): SearchConfig =
    SearchConfig(
        enabled = localSearchEnabled,
        provider = localSearchProvider,
        baseUrl = localSearchBaseUrlDraft,
        apiKey = apiKey,
        maxResults = localSearchMaxResultsOrDefault(),
        searchDepth = localSearchDepthDraft,
        topic = localSearchTopicDraft,
    )

internal fun ToolsUiState.localSearchMaxResultsOrDefault(): Int =
    localSearchMaxResultsOrNull() ?: 5

internal fun ToolsUiState.localSearchMaxResultsOrNull(): Int? =
    localSearchMaxResultsDraft.toIntOrNull()?.takeIf { it in 1..20 }

internal fun ToolDescriptor.fixedPermissionPolicyStatus(policy: ToolPermissionPolicy): String =
    when (policy) {
        ToolPermissionPolicy.AskEveryTime -> "$displayName 必须每次确认。"
        ToolPermissionPolicy.AllowWithoutPrompt -> "$displayName 固定为免确认。"
    }

internal fun ToolResult.extractSearchQuery(): String? =
    rawInputJson?.let { rawInput ->
        runCatching { toolsJson.decodeFromString<SearchInputJson>(rawInput).query }.getOrNull()
    } ?: inputSummary.substringAfter("query:", "").trim().takeIf(String::isNotBlank)

internal fun ToolResult.extractSandboxCode(): String? =
    rawInputJson?.let { rawInput ->
        runCatching { toolsJson.decodeFromString<SandboxInputJson>(rawInput).code }.getOrNull()
    } ?: inputSummary.substringAfter("python:", "").trim().takeIf(String::isNotBlank)

private fun SearchResult.toSearchResultOutputJson(): SearchResultOutputJson =
    SearchResultOutputJson(
        title = title,
        summary = summary,
        url = url,
        source = source,
        publishedAt = publishedAt?.toString(),
    )

private fun SandboxRunResponse.toSandboxOutputJson(): SandboxOutputJson =
    SandboxOutputJson(
        language = language,
        stdout = stdout,
        stderr = stderr,
        exitCode = exitCode,
        durationMs = durationMs,
        timedOut = timedOut,
        truncated = truncated,
    )
