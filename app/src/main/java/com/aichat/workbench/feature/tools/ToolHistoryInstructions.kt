package com.aichat.workbench.feature.tools

import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.tool.model.canonicalToolName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal val toolsJson = Json {
    explicitNulls = false
    encodeDefaults = true
}

internal val RERUNNABLE_STATUSES = setOf(ToolStatus.Completed, ToolStatus.Failed)
internal val REFILLABLE_STATUSES = setOf(ToolStatus.Completed, ToolStatus.Failed)
internal val CHAT_REPLAY_STATUSES = setOf(ToolStatus.Completed, ToolStatus.Failed)

internal val RERUNNABLE_TOOL_NAMES = setOf(
    "web_search_local",
    "web_search",
    "code_sandbox",
    "text_transform",
    "code_diff_preview",
    "provider_connection_test",
    "local_js",
    "file_read",
    "image_generation",
)

internal val CHAT_REPLAY_TOOL_NAMES = setOf(
    "text_transform",
    "code_diff_preview",
    "provider_connection_test",
    "local_js",
    "file_read",
    "image_generation",
    "image_upload_to_model",
)

internal val REFILLABLE_TOOL_NAMES = setOf(
    "time",
    "text_transform",
    "code_diff_preview",
    "provider_connection_test",
    "local_js",
    "file_read",
    "image_generation",
    "image_upload_to_model",
)

internal fun chatInstructionForToolInput(
    toolName: String,
    rawInput: String,
): String {
    val canonicalName = toolName.canonicalToolName()
    return when (canonicalName) {
        "file_read" ->
            """
            请先确认这条文件读取任务仍来自用户通过系统文件选择器授权的 content:// URI。
            不要手写本地路径，不要扫描文件夹，不要自动上传图片或文件内容；如需多模态分析，先征得确认。
            工具：file_read
            参数：$rawInput
            """.trimIndent()
        "local_js" ->
            """
            请先审查这段本地 JavaScript 是否只做纯计算或文本处理，再准备工具调用。
            不要请求网络、文件系统、系统命令或 Android Context；执行前列出超时和输出截断设置。
            工具：local_js
            参数：$rawInput
            """.trimIndent()
        "web_search_local", "web_search" ->
            """
            请搜索这个主题并保留来源链接。
            回答中的关键结论必须标注对应来源 URL；如果没有结果，请明确说明没有可引用来源。
            工具：$canonicalName
            参数：$rawInput
            """.trimIndent()
        "text_transform" ->
            """
            请把这条任务作为本地文本转换处理，不调用 Provider，不上传文本。
            先确认转换类型、输入长度和输出截断设置，再准备工具调用。
            工具：text_transform
            参数：$rawInput
            """.trimIndent()
        "code_diff_preview" ->
            """
            请只用 code_diff_preview 生成 Diff 预览，不写入文件、不修改本机项目。
            执行前确认 original 和 modified 的差异，并在结果里只展示 diff。
            工具：code_diff_preview
            参数：$rawInput
            """.trimIndent()
        "image_generation" ->
            """
            请把这条图片生成任务作为需要用户确认的付费/联网调用来准备。
            执行前确认 Provider、模型、数量和尺寸；如果参数里包含 providerId，请先核对是否仍要使用该 Provider。不要自动上传本地图片。
            工具：image_generation
            参数：$rawInput
            """.trimIndent()
        "image_upload_to_model" ->
            """
            请先让我通过聊天输入栏选择图片，并在发送前说明图片会作为多模态内容发送给当前模型。
            不要手写本地路径，不要自动读取或上传本地图片；必须等待用户二次确认。
            工具：image_upload_to_model
            参数模板：$rawInput
            """.trimIndent()
        "provider_connection_test" ->
            """
            请准备一次 Provider 连接测试，并说明会使用已保存的 Provider 配置，不要输出或索要 API Key 明文。
            工具：provider_connection_test
            参数：$rawInput
            """.trimIndent()
        else ->
            """
            请准备执行这条工具任务，并基于工具结果继续回答。
            工具：$canonicalName
            参数：$rawInput
            """.trimIndent()
    }
}

internal fun ToolResult.failureContextForChat(): String? {
    val error = error ?: return null
    val lines = buildList {
        add("上次执行失败：${error.code}: ${error.message}")
        error.statusCode?.let { add("HTTP 状态：$it") }
        error.retryable?.let { add("是否可重试：${if (it) "是" else "否"}") }
    }
    return lines.joinToString(separator = "\n")
}

internal fun ToolResult.recoveryHintForHistory(): String? {
    val error = error ?: return null
    val canonicalName = toolName.canonicalToolName()
    return when {
        error.code in TOOL_HISTORY_CONFIGURATION_ERROR_CODES ->
            "请打开工具中心检查工具是否启用、名称是否正确，或改用当前 App 支持的本地工具。"
        error.statusCode == 401 -> when (canonicalName) {
            "provider_connection_test",
            "image_generation",
            -> "检查 Provider API Key、Base URL 和模型配置后重试。"
            "web_search",
            "web_search_local",
            -> "检查搜索 API Key、Provider URL 或网关鉴权后重试。"
            else -> "检查 API Key、Provider 配置或网关鉴权后重试。"
        }
        error.statusCode == 429 -> when (canonicalName) {
            "image_generation" -> "图片生成请求被限流，稍后重试，或切换图片模型/Provider。"
            "provider_connection_test" -> "Provider 测试被限流，稍后重试，或切换 Provider/模型。"
            "web_search",
            "web_search_local",
            -> "搜索请求被限流，稍后重试，或切换搜索 Provider。"
            else -> "请求被限流，稍后重试，或切换相关 Provider。"
        }
        error.statusCode != null && error.statusCode in 500..599 -> when (canonicalName) {
            "image_generation" -> "图片服务端异常，稍后重试，或切换图片模型/Provider。"
            "web_search",
            "web_search_local",
            -> "搜索服务端异常，稍后重试，或切换搜索 Provider。"
            else -> "服务端异常，稍后重试，或切换 Provider。"
        }
        canonicalName == "image_upload_to_model" ->
            "请通过聊天输入栏选择图片，并在发送前确认图片会作为多模态内容发送给当前模型。"
        error.retryable == true -> "这是可重试错误，可以稍后重试或调整参数后重跑。"
        error.retryable == false -> "这通常不是自动重试能解决的问题，请先检查配置或参数。"
        else -> "检查工具参数和相关配置后重试。"
    }
}

internal fun sampleInputForToolName(toolName: String): String =
    when (toolName.canonicalToolName()) {
        "time" -> """{"timezone":"Asia/Shanghai"}"""
        "text_transform" -> """{"operation":"json_format","text":"{\"name\":\"demo\"}"}"""
        "code_diff_preview" ->
            """{"fileName":"snippet.kt","original":"fun answer() = \"old\"","modified":"fun answer() = \"new\""}"""
        "web_search_local", "web_search" -> """{"query":"AI 行业最新消息"}"""
        "image_generation" -> """{"prompt":"一张原生移动端 AI 工作台界面概念图","count":1}"""
        "image_upload_to_model" ->
            """{"imageUri":"<聊天输入栏中用户已选择的图片URI>","purpose":"分析这张图片"}"""
        "provider_connection_test" -> """{"providerId":"default"}"""
        "local_js" ->
            """{"language":"javascript","code":"return JSON.stringify({ ok: true })","timeoutMillis":1000,"outputLimitBytes":8192}"""
        "file_read" -> """{"uri":"content://<系统文件选择器返回的授权URI>","maxBytes":65536}"""
        "code_sandbox" -> """{"language":"python","code":"print(1 + 1)","timeoutSeconds":5}"""
        else -> "{}"
    }

@Serializable
internal data class SearchInputJson(
    val query: String,
)

@Serializable
internal data class SearchOutputJson(
    val query: String,
    val fetchedAt: String,
    val results: List<SearchResultOutputJson>,
)

@Serializable
internal data class SearchResultOutputJson(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val publishedAt: String? = null,
)

@Serializable
internal data class SandboxInputJson(
    val language: String,
    val code: String,
    val timeoutSeconds: Int,
)

@Serializable
internal data class SandboxOutputJson(
    val language: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val durationMs: Long,
    val timedOut: Boolean,
    val truncated: Boolean,
)

private val TOOL_HISTORY_CONFIGURATION_ERROR_CODES = setOf(
    "tool_disabled",
    "unknown_tool",
    "hosted_tool_not_executable_locally",
)
