package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.coroutines.withTimeoutOrNull

class TextTransformTool : LocalTool {
    override val descriptor: ToolDescriptor = TextTransformToolDescriptor

    override suspend fun execute(request: LocalToolRequest): LocalToolExecution {
        val args = decodeLocalToolArguments<TextTransformArguments>(request.toolCall.arguments)
        val operation = args.operation.trim().lowercase().ifBlank { "trim" }
        val output = when (operation) {
            "trim" -> transformed(args, args.text.trim())
            "uppercase" -> transformed(args, args.text.uppercase())
            "lowercase" -> transformed(args, args.text.lowercase())
            "json_format" -> jsonFormatted(args)
            "regex_preview" -> regexPreviewSafely(args)
            "regex_replace" -> regexReplaceSafely(args)
            else -> throw InvalidLocalToolArgumentsException("不支持的文本转换操作：$operation")
        }
        return LocalToolExecution(ToolOutput.Json(localToolJson.encodeToString(output)))
    }

    private fun transformed(args: TextTransformArguments, text: String): TextTransformOutput =
        TextTransformOutput(
            operation = args.operation.trim().lowercase().ifBlank { "trim" },
            inputLength = args.text.length,
            output = text,
        )

    private fun jsonFormatted(args: TextTransformArguments): TextTransformOutput {
        val element = runCatching { compactJson.decodeFromString<JsonElement>(args.text) }
            .getOrElse { throw InvalidLocalToolArgumentsException("JSON 无效：${it.message}", it) }
        return TextTransformOutput(
            operation = "json_format",
            inputLength = args.text.length,
            output = prettyJson.encodeToString(JsonElement.serializer(), element),
            validJson = true,
        )
    }

    private suspend fun regexPreviewSafely(args: TextTransformArguments): TextTransformOutput {
        val regex = args.requiredRegex()
        val input = args.text.take(MAX_REGEX_INPUT_LENGTH)
        val matchesWithOverflow = withTimeoutOrNull(REGEX_TIMEOUT_MS) {
            regex.findAll(input)
                .take(MAX_REGEX_MATCHES + 1)
                .map { it.value }
                .toList()
        } ?: throw InvalidLocalToolArgumentsException("正则预览超时，请简化正则表达式或缩短输入文本。")
        return TextTransformOutput(
            operation = "regex_preview",
            inputLength = args.text.length,
            matches = matchesWithOverflow.take(MAX_REGEX_MATCHES),
            truncated = matchesWithOverflow.size > MAX_REGEX_MATCHES,
        )
    }

    private suspend fun regexReplaceSafely(args: TextTransformArguments): TextTransformOutput {
        val regex = args.requiredRegex()
        val input = args.text.take(MAX_REGEX_INPUT_LENGTH)
        val result = withTimeoutOrNull(REGEX_TIMEOUT_MS) {
            regex.replace(input, args.replacement.orEmpty())
        } ?: throw InvalidLocalToolArgumentsException("正则替换超时，请简化正则表达式或缩短输入文本。")
        return TextTransformOutput(
            operation = "regex_replace",
            inputLength = args.text.length,
            output = result,
        )
    }

    private fun TextTransformArguments.requiredRegex(): Regex {
        val pattern = regex?.takeIf { it.isNotBlank() }
            ?: throw InvalidLocalToolArgumentsException("regex 操作必须提供 regex。")
        if (pattern.hasNestedQuantifier()) {
            throw InvalidLocalToolArgumentsException("正则表达式包含嵌套量词，可能导致执行时间不可控。")
        }
        return runCatching { Regex(pattern) }
            .getOrElse { throw InvalidLocalToolArgumentsException("正则表达式无效：${it.message}", it) }
    }
}

val TextTransformToolDescriptor: ToolDescriptor = ToolDescriptor(
    name = "text_transform",
    displayName = "文本转换",
    description = "本地执行文本清洗、JSON 格式化、正则预览和大小写转换。",
    permissionLevel = ToolPermissionLevel.ReadOnly,
    inputSchemaJson = """
        {
          "type": "object",
          "required": ["text"],
          "properties": {
            "operation": {
              "type": "string",
              "enum": ["trim", "uppercase", "lowercase", "json_format", "regex_preview", "regex_replace"]
            },
            "text": { "type": "string" },
            "regex": { "type": "string" },
            "replacement": { "type": "string" }
          }
        }
    """.trimIndent(),
    outputSchemaJson = """{"type":"object"}""",
    timeoutSeconds = 5,
    source = ToolSource.BuiltIn,
    riskLevel = ToolRiskLevel.Low,
    requiresNetwork = false,
    requiresFileAccess = false,
    defaultPermissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
)

@Serializable
private data class TextTransformArguments(
    val operation: String = "trim",
    val text: String = "",
    val regex: String? = null,
    val replacement: String? = null,
)

@Serializable
private data class TextTransformOutput(
    val operation: String,
    val inputLength: Int,
    val output: String? = null,
    val matches: List<String> = emptyList(),
    val validJson: Boolean? = null,
    val truncated: Boolean = false,
)

private const val MAX_REGEX_MATCHES = 50
private const val MAX_REGEX_INPUT_LENGTH = 50_000
private const val REGEX_TIMEOUT_MS = 3_000L

private fun String.hasNestedQuantifier(): Boolean =
    contains(NESTED_QUANTIFIER_PATTERN)

private val NESTED_QUANTIFIER_PATTERN = Regex("""\((?:[^()\\]|\\.)*[+*](?:[^()\\]|\\.)*\)[+*{]""")

private val compactJson = Json { ignoreUnknownKeys = false }
private val prettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
}
