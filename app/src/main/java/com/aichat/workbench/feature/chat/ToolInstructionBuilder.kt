package com.aichat.workbench.feature.chat

import com.aichat.workbench.tool.model.canonicalToolName

/**
 * Builds tool invocation instructions for various task types.
 * These instructions are inserted into the chat input to guide the AI assistant
 * in properly invoking tools with correct parameters and context.
 */

internal fun String.toImageGenerationInstruction(): String =
    """
        请重新生成这张图片，并优先使用图片生成工具；执行前确认 Provider、模型、数量和尺寸。
        这是联网且可能产生费用的调用，不要自动上传本地图片。
        工具：image_generation
        参数：{"prompt":${jsonStringLiteral()},"count":1}
    """.trimIndent()

internal fun String.toWebSearchInstruction(): String =
    """
        请搜索这个主题的最新消息，保留来源链接，并总结事实、影响和待确认信息。
        回答中的关键结论必须标注对应来源 URL；如果没有结果，请明确说明没有可引用来源。
        工具：web_search_local
        参数：{"query":${jsonStringLiteral()}}
    """.trimIndent()

internal fun String.toLocalJsInstruction(): String =
    """
        请使用本地 JavaScript 工具运行下面代码，并解释输出。
        只允许纯计算或文本处理；不要请求网络、文件系统、系统命令或 Android Context。
        工具：local_js
        参数：{"language":"javascript","code":${jsonStringLiteral()},"timeoutMillis":1000,"outputLimitBytes":8192}
    """.trimIndent()

internal fun String.toLocalJsRecoveryInstruction(): String =
    """
        这次本地 JavaScript 工具结果不完整。请根据结果判断原因，并给出更适合重跑的代码或参数建议；如果需要再次运行，请先列出新的 local_js 参数。
        新参数仍必须遵守沙箱边界：不要请求网络、文件系统、系统命令或 Android Context。
        工具：local_js
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

internal fun String.toToolRecoveryInstruction(
    toolName: String,
    reason: String,
): String =
    when (val canonicalName = toolName.canonicalToolName()) {
        "image_generation" -> toImageGenerationRecoveryInstruction(reason)
        "file_read" -> toFileReadRecoveryInstruction(reason)
        "local_js" -> toLocalJsRecoveryInstruction(reason)
        "web_search_local", "web_search" -> toWebSearchRecoveryInstruction(canonicalName, reason)
        "provider_connection_test" -> toProviderConnectionRecoveryInstruction(reason)
        else -> toGenericToolRecoveryInstruction(canonicalName, reason)
    }

private fun String.toGenericToolRecoveryInstruction(
    toolName: String,
    reason: String,
): String =
    """
        这次工具结果需要调整。请根据原因和上次结果重新规划工具参数；如果需要再次运行，请先列出新的 ${toolName.trim()} 参数。
        工具：${toolName.trim()}
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toFileReadRecoveryInstruction(reason: String): String =
    """
        这次文件读取工具结果需要调整。请根据原因和上次结果重新规划 file_read 参数；如果需要再次读取，请先列出新的 file_read 参数。
        必须继续使用用户通过系统文件选择器授权的 content:// URI；不要手写本地路径，不要扫描文件夹，不要自动上传图片或文件内容。
        工具：file_read
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toLocalJsRecoveryInstruction(reason: String): String =
    """
        这次本地 JavaScript 工具结果需要调整。请根据原因和上次结果重新规划 local_js 参数；如果需要再次运行，请先列出新的 local_js 参数。
        新参数仍必须遵守沙箱边界：不要请求网络、文件系统、系统命令或 Android Context；执行前确认超时和输出截断设置。
        工具：local_js
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toWebSearchRecoveryInstruction(
    toolName: String,
    reason: String,
): String =
    """
        这次搜索工具结果需要调整。请根据原因和上次结果重新规划 ${toolName.trim()} 参数；如果需要再次搜索，请先列出新的 ${toolName.trim()} 参数。
        回答中的关键结论必须标注对应来源 URL；如果没有结果，请明确说明没有可引用来源。
        工具：${toolName.trim()}
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toProviderConnectionRecoveryInstruction(reason: String): String =
    """
        这次 Provider 连接测试结果需要调整。请根据原因和上次结果重新规划 provider_connection_test 参数；如果需要再次测试，请先列出新的 provider_connection_test 参数。
        只能使用已保存的 Provider 配置，不要输出或索要 API Key 明文。
        工具：provider_connection_test
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toImageGenerationRecoveryInstruction(reason: String): String =
    """
        这次图片生成工具结果需要调整。请根据原因和上次结果重新规划 image_generation 参数；如果需要再次生成，请先列出新的 image_generation 参数。
        这是联网且可能产生费用的调用，执行前必须确认 Provider、模型、数量、尺寸和质量。
        不要自动上传本地图片；如果需要参考本地图片，先征得确认。
        工具：image_generation
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

internal fun String.toTextTransformInstruction(): String =
    """
        请用本地文本转换工具格式化下面内容。如果不是 JSON，请先说明无法格式化为 JSON，并建议可用的清洗方式。
        工具：text_transform
        参数：{"operation":"json_format","text":${jsonStringLiteral()}}
    """.trimIndent()

internal fun String.toCodeDiffPreviewInstruction(): String =
    """
        请基于下面的原始代码准备修改版本，并用本地 Diff 预览工具展示差异，不写入文件。
        工具：code_diff_preview
        参数：{"fileName":"snippet","original":${jsonStringLiteral()},"modified":${jsonStringLiteral()}}
    """.trimIndent()

internal fun String.toToolResultContinuationInstruction(toolName: String): String =
    if (toolName.canonicalToolName() == "file_read") {
        """
            请基于下面的文件读取结果继续处理，先提炼可见预览中的关键信息，再给出下一步建议。
            注意：工具结果默认只包含文件元数据和文本预览，不代表完整文件内容已发送给模型；不要编造未出现在预览中的内容。如需更多内容，请先要求重新选择文件或调整 maxBytes 后再次读取。
            工具：file_read
            工具结果：
            ```json
            $this
            ```
        """.trimIndent()
    } else {
        """
            请基于下面的工具结果继续处理，先提炼关键信息，再给出下一步建议。
            工具：${toolName.trim()}
            工具结果：
            ```json
            $this
            ```
        """.trimIndent()
    }

internal fun String.jsonStringLiteral(): String =
    buildString {
        append('"')
        this@jsonStringLiteral.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
