package com.aichat.workbench.feature.chat

import com.aichat.workbench.tool.model.canonicalToolName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class SearchCitation(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: String?,
)

internal fun extractSearchCitations(toolName: String, toolResult: String?): List<SearchCitation> {
    if (toolName.canonicalToolName() !in SEARCH_TOOL_NAMES || toolResult.isNullOrBlank()) return emptyList()
    return runCatching {
        chatToolResultJson.decodeFromString(SearchToolOutput.serializer(), toolResult)
            .results
            .filter { it.url.isNotBlank() }
            .map { result ->
                SearchCitation(
                    title = result.title.ifBlank { result.url },
                    summary = result.summary,
                    url = result.url,
                    source = result.source.ifBlank { result.url },
                    publishedAt = result.publishedAt,
                )
            }
    }.getOrDefault(emptyList())
}

internal data class ImageResultActionState(
    val prompt: String?,
    val imagePath: String?,
) {
    val hasPromptActions: Boolean = prompt != null
    val hasFileActions: Boolean = imagePath != null
    val hasAnyActions: Boolean = hasPromptActions || hasFileActions
}

internal fun extractImagePrompt(toolResult: String?): String? {
    if (toolResult.isNullOrBlank()) return null
    return runCatching {
        chatToolResultJson.parseToJsonElement(toolResult)
            .jsonObject["prompt"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

internal fun imageResultActionState(
    toolResult: String?,
    fallbackContent: String,
    imagePath: String?,
): ImageResultActionState {
    val prompt = extractImagePrompt(toolResult)
        ?: fallbackContent.trim().takeIf { it.isNotBlank() }
    return ImageResultActionState(
        prompt = prompt,
        imagePath = imagePath,
    )
}

internal data class SearchResultSummary(
    val query: String,
    val resultCount: Int,
)

internal fun SearchResultSummary.recoveryReason(): String =
    if (resultCount == 0) {
        "没有搜索结果，需要换关键词、降低限制条件，或检查搜索 Provider 配置。"
    } else {
        "搜索结果需要调整，请换关键词、调整搜索深度，或补充需要核验的来源。"
    }

internal fun extractSearchResultSummary(toolName: String, toolResult: String?): SearchResultSummary? {
    if (toolName.canonicalToolName() !in SEARCH_TOOL_NAMES || toolResult.isNullOrBlank()) return null
    return runCatching {
        val output = chatToolResultJson.decodeFromString(SearchToolOutput.serializer(), toolResult)
        if (output.code.isNotBlank()) return@runCatching null
        if (output.query.isBlank() && output.results.isEmpty()) return@runCatching null
        SearchResultSummary(
            query = output.query.takeIf { it.isNotBlank() } ?: "(未记录查询)",
            resultCount = output.results.size,
        )
    }.getOrNull()
}

internal data class LocalJsResultSummary(
    val output: String,
    val durationMs: Long,
    val timedOut: Boolean,
    val truncated: Boolean,
)

internal fun extractLocalJsResult(toolName: String, toolResult: String?): LocalJsResultSummary? {
    if (toolName.canonicalToolName() != "local_js" || toolResult.isNullOrBlank()) return null
    return runCatching {
        val output = chatToolResultJson.decodeFromString(LocalJsToolOutput.serializer(), toolResult)
        LocalJsResultSummary(
            output = output.output,
            durationMs = output.durationMs,
            timedOut = output.timedOut,
            truncated = output.truncated,
        )
    }.getOrNull()
}

internal data class FileReadResultSummary(
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val status: String,
    val preview: String?,
    val truncated: Boolean,
    val unsupportedReason: String?,
    val sentToModel: Boolean,
)

internal enum class FileReadModelContextTone {
    Success,
    Warning,
    Neutral,
}

internal fun FileReadResultSummary.modelContextLabel(): String =
    when {
        sentToModel -> "已发送模型"
        preview != null -> "仅预览，未发送全文"
        else -> "未发送内容"
    }

internal fun FileReadResultSummary.modelContextTone(): FileReadModelContextTone =
    when {
        sentToModel -> FileReadModelContextTone.Success
        preview != null -> FileReadModelContextTone.Warning
        else -> FileReadModelContextTone.Neutral
    }

internal fun extractFileReadResult(toolName: String, toolResult: String?): FileReadResultSummary? {
    if (toolName.canonicalToolName() != "file_read" || toolResult.isNullOrBlank()) return null
    return runCatching {
        val output = chatToolResultJson.decodeFromString(FileReadToolOutput.serializer(), toolResult)
        FileReadResultSummary(
            fileName = output.fileName?.takeIf { it.isNotBlank() } ?: output.uri,
            mimeType = output.mimeType?.takeIf { it.isNotBlank() },
            sizeBytes = output.sizeBytes,
            status = output.status,
            preview = output.preview?.takeIf { it.isNotBlank() },
            truncated = output.truncated,
            unsupportedReason = output.unsupportedReason?.takeIf { it.isNotBlank() },
            sentToModel = output.sentToModel,
        )
    }.getOrNull()
}

internal data class TextTransformResultSummary(
    val operation: String,
    val inputLength: Int,
    val output: String?,
    val matches: List<String>,
    val validJson: Boolean?,
    val truncated: Boolean,
)

internal fun extractTextTransformResult(toolName: String, toolResult: String?): TextTransformResultSummary? {
    if (toolName.canonicalToolName() != "text_transform" || toolResult.isNullOrBlank()) return null
    return runCatching {
        val output = chatToolResultJson.decodeFromString(TextTransformToolOutput.serializer(), toolResult)
        TextTransformResultSummary(
            operation = output.operation,
            inputLength = output.inputLength,
            output = output.output?.takeIf { it.isNotBlank() },
            matches = output.matches,
            validJson = output.validJson,
            truncated = output.truncated,
        )
    }.getOrNull()
}

internal data class CodeDiffPreviewResultSummary(
    val fileName: String,
    val additions: Int,
    val deletions: Int,
    val diff: String,
)

internal fun extractCodeDiffPreviewResult(toolName: String, toolResult: String?): CodeDiffPreviewResultSummary? {
    if (toolName.canonicalToolName() != "code_diff_preview" || toolResult.isNullOrBlank()) return null
    return runCatching {
        val output = chatToolResultJson.decodeFromString(CodeDiffPreviewToolOutput.serializer(), toolResult)
        CodeDiffPreviewResultSummary(
            fileName = output.fileName.ifBlank { "snippet" },
            additions = output.additions,
            deletions = output.deletions,
            diff = output.diff.ifBlank { "No changes." },
        )
    }.getOrNull()
}

internal data class ProviderConnectionTestResultSummary(
    val providerName: String,
    val providerType: String,
    val enabled: Boolean,
    val defaultModel: String?,
    val ok: Boolean,
    val statusCode: Int?,
    val message: String,
)

internal fun ProviderConnectionTestResultSummary.diagnosticText(): String =
    buildString {
        appendLine("Provider：$providerName")
        appendLine("类型：${providerType.ifBlank { "(未知)" }}")
        appendLine("启用：${if (enabled) "是" else "否"}")
        appendLine("模型：${defaultModel ?: "(未设置)"}")
        appendLine("结果：${if (ok) "连接成功" else "连接失败"}")
        statusCode?.let { appendLine("HTTP：$it") }
        append("消息：$message")
    }

internal fun extractProviderConnectionTestResult(
    toolName: String,
    toolResult: String?,
): ProviderConnectionTestResultSummary? {
    if (toolName.canonicalToolName() != "provider_connection_test" || toolResult.isNullOrBlank()) return null
    return runCatching {
        val output = chatToolResultJson.decodeFromString(ProviderConnectionTestToolOutput.serializer(), toolResult)
        ProviderConnectionTestResultSummary(
            providerName = output.providerName.ifBlank { output.providerId },
            providerType = output.providerType,
            enabled = output.enabled,
            defaultModel = output.defaultModel?.takeIf { it.isNotBlank() },
            ok = output.ok,
            statusCode = output.statusCode,
            message = output.message.ifBlank { if (output.ok) "连接成功。" else "连接失败。" },
        )
    }.getOrNull()
}

internal data class ToolErrorResultSummary(
    val code: String,
    val message: String,
    val statusCode: Int?,
    val retryable: Boolean?,
)

internal fun ToolErrorResultSummary.recoveryHint(): String =
    legacyRecoveryHint(statusCode = statusCode, retryable = retryable)

internal fun ToolErrorResultSummary.recoveryHint(toolName: String?): String {
    val canonicalName = toolName?.canonicalToolName()
    return when {
        code == "tool_cancelled" ->
            "工具已取消，参数和日志已保留；如需继续，请调整参数后重新发起。"
        code == "tool_denied" ->
            "工具已被拒绝执行；如需继续，请确认风险和参数后重新发起。"
        code in TOOL_CONFIGURATION_ERROR_CODES ->
            "请打开工具中心检查工具是否启用、名称是否正确，或改用当前 App 支持的本地工具。"
        statusCode == 401 -> when (canonicalName) {
            "provider_connection_test",
            "image_generation",
            -> "检查 Provider API Key、Base URL 和模型配置后重试。"
            "web_search",
            "web_search_local",
            -> "检查搜索 API Key、Provider URL 或网关鉴权后重试。"
            else -> "检查 API Key、Provider 配置或网关鉴权后重试。"
        }
        statusCode == 429 -> when (canonicalName) {
            "image_generation" -> "图片生成请求被限流，稍后重试，或切换图片模型/Provider。"
            "provider_connection_test" -> "Provider 测试被限流，稍后重试，或切换 Provider/模型。"
            "web_search",
            "web_search_local",
            -> "搜索请求被限流，稍后重试，或切换搜索 Provider。"
            else -> "请求被限流，稍后重试，或切换相关 Provider。"
        }
        statusCode != null && statusCode in 500..599 -> when (canonicalName) {
            "image_generation" -> "图片服务端异常，稍后重试，或切换图片模型/Provider。"
            "web_search",
            "web_search_local",
            -> "搜索服务端异常，稍后重试，或切换搜索 Provider。"
            else -> "服务端异常，稍后重试，或切换 Provider。"
        }
        canonicalName == "image_upload_to_model" ->
            "请通过聊天输入栏选择图片，并在发送前确认图片会作为多模态内容发送给当前模型。"
        retryable == true -> "这是可重试错误，可以稍后重试或调整参数后重跑。"
        retryable == false -> "这通常不是自动重试能解决的问题，请先检查配置或参数。"
        else -> "检查工具参数和相关配置后重试。"
    }
}

internal fun ToolErrorResultSummary.diagnosticText(): String =
    buildString {
        appendLine("错误码：$code")
        statusCode?.let { appendLine("HTTP：$it") }
        retryable?.let { appendLine("可重试：${if (it) "是" else "否"}") }
        appendLine("建议：${recoveryHint()}")
        append("消息：$message")
    }

internal fun ToolErrorResultSummary.diagnosticText(toolName: String?): String =
    buildString {
        appendLine("错误码：$code")
        statusCode?.let { appendLine("HTTP：$it") }
        retryable?.let { appendLine("可重试：${if (it) "是" else "否"}") }
        appendLine("建议：${recoveryHint(toolName)}")
        append("消息：$message")
    }

private fun legacyRecoveryHint(
    statusCode: Int?,
    retryable: Boolean?,
): String =
    when {
        statusCode == 401 -> "检查 API Key、Provider 配置或网关鉴权后重试。"
        statusCode == 429 -> "请求被限流，稍后重试，或切换搜索 Provider。"
        statusCode != null && statusCode in 500..599 -> "服务端异常，稍后重试，或切换 Provider。"
        retryable == true -> "这是可重试错误，可以稍后重试或调整参数后重跑。"
        retryable == false -> "这通常不是自动重试能解决的问题，请先检查配置或参数。"
        else -> "检查工具参数和相关配置后重试。"
    }

internal val TOOL_CONFIGURATION_ERROR_CODES = setOf(
    "tool_disabled",
    "unknown_tool",
    "hosted_tool_not_executable_locally",
)

internal fun extractToolErrorResult(toolResult: String?): ToolErrorResultSummary? {
    if (toolResult.isNullOrBlank()) return null
    return runCatching {
        val output = chatToolResultJson.decodeFromString(ParsedToolErrorOutput.serializer(), toolResult)
        ToolErrorResultSummary(
            code = output.code.takeIf { it.isNotBlank() } ?: return@runCatching null,
            message = output.message.ifBlank { "工具执行失败。" },
            statusCode = output.statusCode,
            retryable = output.retryable,
        )
    }.getOrNull()
}

@Serializable
private data class SearchToolOutput(
    val query: String = "",
    val code: String = "",
    val results: List<SearchToolResult> = emptyList(),
)

@Serializable
private data class SearchToolResult(
    val title: String = "",
    val summary: String = "",
    val url: String = "",
    val source: String = "",
    val publishedAt: String? = null,
)

@Serializable
private data class LocalJsToolOutput(
    val output: String = "",
    val durationMs: Long = 0,
    val timedOut: Boolean = false,
    val truncated: Boolean = false,
)

@Serializable
private data class FileReadToolOutput(
    val uri: String = "",
    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val status: String = "",
    val preview: String? = null,
    val truncated: Boolean = false,
    val unsupportedReason: String? = null,
    val sentToModel: Boolean = false,
)

@Serializable
private data class TextTransformToolOutput(
    val operation: String = "",
    val inputLength: Int = 0,
    val output: String? = null,
    val matches: List<String> = emptyList(),
    val validJson: Boolean? = null,
    val truncated: Boolean = false,
)

@Serializable
private data class CodeDiffPreviewToolOutput(
    val fileName: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val diff: String = "",
)

@Serializable
private data class ProviderConnectionTestToolOutput(
    val providerId: String = "",
    val providerName: String = "",
    val providerType: String = "",
    val enabled: Boolean = false,
    val defaultModel: String? = null,
    val ok: Boolean = false,
    val statusCode: Int? = null,
    val message: String = "",
)

@Serializable
private data class ParsedToolErrorOutput(
    val code: String = "",
    val message: String = "",
    val statusCode: Int? = null,
    val retryable: Boolean? = null,
)

private val SEARCH_TOOL_NAMES = setOf("web_search", "web_search_local")

private val chatToolResultJson = Json {
    ignoreUnknownKeys = true
}
