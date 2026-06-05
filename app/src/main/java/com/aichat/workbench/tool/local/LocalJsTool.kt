package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import java.nio.charset.StandardCharsets

class LocalJsTool(
    private val scriptRunner: LocalScriptRunner,
) : LocalTool {
    override val descriptor: ToolDescriptor = LocalJsToolDescriptor

    override suspend fun execute(request: LocalToolRequest): LocalToolExecution {
        val args = decodeLocalToolArguments<LocalJsArguments>(request.toolCall.arguments)
        val language = args.language.trim().lowercase().ifBlank { "javascript" }
        if (language != "javascript" && language != "js") {
            throw InvalidLocalToolArgumentsException("本地脚本第一阶段仅支持 JavaScript。")
        }
        val code = args.code.trim()
        if (code.isBlank()) {
            throw InvalidLocalToolArgumentsException("JavaScript 代码不能为空。")
        }
        val inputJson = args.inputJson.normalizedInputJson()
        if (inputJson != null && inputJson.toByteArray(Charsets.UTF_8).size > MAX_LOCAL_JS_INPUT_BYTES) {
            throw InvalidLocalToolArgumentsException("inputJson 不能超过 ${MAX_LOCAL_JS_INPUT_BYTES / 1024}KB。")
        }
        if (!scriptRunner.isSupported()) {
            throw LocalToolUnavailableException("当前设备不支持本地 JavaScript 沙箱。")
        }
        val timeoutMillis = args.timeoutMillis ?: DEFAULT_LOCAL_JS_TIMEOUT_MS
        if (timeoutMillis !in MIN_LOCAL_JS_TIMEOUT_MS..MAX_LOCAL_JS_TIMEOUT_MS) {
            throw InvalidLocalToolArgumentsException("timeoutMillis 必须在 100 到 5000 之间。")
        }
        val outputLimitBytes = args.outputLimitBytes ?: DEFAULT_LOCAL_JS_OUTPUT_LIMIT_BYTES
        if (outputLimitBytes !in MIN_LOCAL_JS_OUTPUT_LIMIT_BYTES..MAX_LOCAL_JS_OUTPUT_LIMIT_BYTES) {
            throw InvalidLocalToolArgumentsException("outputLimitBytes 必须在 64 到 32768 之间。")
        }

        val result = scriptRunner.run(
            LocalScriptRunRequest(
                language = ScriptLanguage.JavaScript,
                code = code,
                inputJson = inputJson,
                timeoutMillis = timeoutMillis,
                outputLimitBytes = outputLimitBytes,
            ),
        )
        return LocalToolExecution(
            ToolOutput.Json(
                localToolJson.encodeToString(
                    LocalJsOutput(
                        language = "javascript",
                        output = result.output,
                        durationMs = result.durationMs,
                        timedOut = result.timedOut,
                        truncated = result.truncated,
                    ),
                ),
            ),
        )
    }
}

val LocalJsToolDescriptor: ToolDescriptor = ToolDescriptor(
    name = "local_js",
    displayName = "本地 JavaScript",
    description = "在 AndroidX JavaScriptSandbox 中运行受限 JavaScript，不提供网络、文件系统或 Android Context。",
    permissionLevel = ToolPermissionLevel.HighRisk,
    inputSchemaJson = """
        {
          "type": "object",
          "required": ["code"],
          "properties": {
            "language": {
              "type": "string",
              "enum": ["javascript", "js"]
            },
            "code": {
              "type": "string",
              "description": "函数体代码，可使用 input 变量；返回字符串或 JSON 可序列化对象。"
            },
            "inputJson": {
              "type": "string",
              "description": "可选 JSON 输入，会作为 input 变量传入。"
            },
            "timeoutMillis": {
              "type": "integer",
              "minimum": 100,
              "maximum": 5000
            },
            "outputLimitBytes": {
              "type": "integer",
              "minimum": 64,
              "maximum": 32768
            }
          }
        }
    """.trimIndent(),
    outputSchemaJson = """{"type":"object"}""",
    timeoutSeconds = 5,
    source = ToolSource.BuiltIn,
    riskLevel = ToolRiskLevel.High,
    requiresNetwork = false,
    requiresFileAccess = false,
    defaultPermissionPolicy = ToolPermissionPolicy.AskEveryTime,
)

@Serializable
private data class LocalJsArguments(
    val language: String = "javascript",
    val code: String = "",
    val inputJson: String? = null,
    val timeoutMillis: Long? = null,
    val outputLimitBytes: Int? = null,
)

@Serializable
private data class LocalJsOutput(
    val language: String,
    val output: String,
    val durationMs: Long,
    val timedOut: Boolean,
    val truncated: Boolean,
)

private fun String?.normalizedInputJson(): String? {
    val value = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
    try {
        localToolJson.decodeFromString<JsonElement>(value)
    } catch (error: SerializationException) {
        throw InvalidLocalToolArgumentsException("inputJson 必须是合法 JSON。", error)
    }
    return value
}

private const val DEFAULT_LOCAL_JS_TIMEOUT_MS = 1_000L
private const val MIN_LOCAL_JS_TIMEOUT_MS = 100L
private const val MAX_LOCAL_JS_TIMEOUT_MS = 5_000L
private const val DEFAULT_LOCAL_JS_OUTPUT_LIMIT_BYTES = 8_192
private const val MIN_LOCAL_JS_OUTPUT_LIMIT_BYTES = 64
private const val MAX_LOCAL_JS_OUTPUT_LIMIT_BYTES = 32_768
private const val MAX_LOCAL_JS_INPUT_BYTES = 32 * 1024
