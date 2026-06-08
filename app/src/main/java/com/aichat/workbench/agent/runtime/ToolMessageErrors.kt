package com.aichat.workbench.agent.runtime

import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.tool.model.canonicalToolName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal data class ToolErrorResultSummary(
    val code: String,
    val message: String,
    val statusCode: Int?,
    val retryable: Boolean?,
)

internal fun ToolResult.toolMessageErrorSummary(
    toolName: String,
    toolResult: String,
): String? {
    val error = error ?: return null
    val structuredError = extractToolErrorResult(toolResult) ?: return error.message
    return buildString {
        append(structuredError.message)
        structuredError.statusCode?.let { append("\nHTTP：$it") }
        structuredError.retryable?.let { append("\n可重试：${if (it) "是" else "否"}") }
        append("\n建议：${structuredError.recoveryHint(toolName)}")
    }
}

private fun ToolErrorResultSummary.recoveryHint(toolName: String?): String {
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

private fun extractToolErrorResult(toolResult: String?): ToolErrorResultSummary? {
    if (toolResult.isNullOrBlank()) return null
    return runCatching {
        val output = toolMessageJson.decodeFromString<ParsedToolErrorOutput>(toolResult)
        ToolErrorResultSummary(
            code = output.code.takeIf { it.isNotBlank() } ?: return@runCatching null,
            message = output.message.ifBlank { "工具执行失败。" },
            statusCode = output.statusCode,
            retryable = output.retryable,
        )
    }.getOrNull()
}

private val TOOL_CONFIGURATION_ERROR_CODES = setOf(
    "tool_disabled",
    "unknown_tool",
    "hosted_tool_not_executable_locally",
)

@Serializable
private data class ParsedToolErrorOutput(
    val code: String = "",
    val message: String = "",
    val statusCode: Int? = null,
    val retryable: Boolean? = null,
)

private val toolMessageJson = Json {
    ignoreUnknownKeys = true
}
