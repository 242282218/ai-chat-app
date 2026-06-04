package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

class FileReadTool(
    private val fileReader: AuthorizedFileReader,
) : LocalTool {
    override val descriptor: ToolDescriptor = FileReadToolDescriptor

    override suspend fun execute(request: LocalToolRequest): LocalToolExecution {
        val args = decodeLocalToolArguments<FileReadArguments>(request.toolCall.arguments)
        val uri = args.uri.trim()
        if (uri.isBlank()) {
            throw InvalidLocalToolArgumentsException("文件 URI 不能为空。")
        }
        if (!uri.startsWith("content://")) {
            throw InvalidLocalToolArgumentsException("只能读取 Android 文件选择器授权的 content:// URI。")
        }
        val maxBytes = args.maxBytes ?: DEFAULT_FILE_READ_MAX_BYTES
        if (maxBytes !in MIN_FILE_READ_BYTES..MAX_FILE_READ_BYTES) {
            throw InvalidLocalToolArgumentsException("maxBytes 必须在 1024 到 262144 之间。")
        }

        val result = fileReader.read(AuthorizedFileReadRequest(uri = uri, maxBytes = maxBytes))
        val status = when {
            result.unsupportedReason != null -> "unsupported"
            result.truncated -> "truncated"
            else -> "completed"
        }
        return LocalToolExecution(
            ToolOutput.Json(
                localToolJson.encodeToString(
                    FileReadOutput(
                        uri = uri,
                        fileName = result.fileName,
                        mimeType = result.mimeType,
                        sizeBytes = result.sizeBytes,
                        status = status,
                        preview = result.content?.linePreview(),
                        content = result.content,
                        truncated = result.truncated,
                        unsupportedReason = result.unsupportedReason,
                        sentToModel = false,
                    ),
                ),
            ),
        )
    }
}

val FileReadToolDescriptor: ToolDescriptor = ToolDescriptor(
    name = "file_read",
    displayName = "读取授权文件",
    description = "读取用户通过 Android 文件选择器授权的文本、Markdown、JSON 或代码文件。",
    permissionLevel = ToolPermissionLevel.HighRisk,
    inputSchemaJson = """
        {
          "type": "object",
          "required": ["uri"],
          "properties": {
            "uri": {
              "type": "string",
              "description": "Android OpenDocument 返回的 content:// URI。"
            },
            "maxBytes": {
              "type": "integer",
              "minimum": 1024,
              "maximum": 262144
            }
          }
        }
    """.trimIndent(),
    outputSchemaJson = """{"type":"object"}""",
    timeoutSeconds = null,
    source = ToolSource.BuiltIn,
    riskLevel = ToolRiskLevel.High,
    requiresNetwork = false,
    requiresFileAccess = true,
    defaultPermissionPolicy = ToolPermissionPolicy.AskEveryTime,
)

@Serializable
private data class FileReadArguments(
    val uri: String = "",
    val maxBytes: Int? = null,
)

@Serializable
private data class FileReadOutput(
    val uri: String,
    val fileName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val status: String,
    val preview: String?,
    val content: String?,
    val truncated: Boolean,
    val unsupportedReason: String?,
    val sentToModel: Boolean,
)

private fun String.linePreview(): String =
    lineSequence()
        .take(MAX_FILE_PREVIEW_LINES)
        .joinToString("\n")

private const val DEFAULT_FILE_READ_MAX_BYTES = 64 * 1024
private const val MIN_FILE_READ_BYTES = 1024
private const val MAX_FILE_READ_BYTES = 256 * 1024
private const val MAX_FILE_PREVIEW_LINES = 40
