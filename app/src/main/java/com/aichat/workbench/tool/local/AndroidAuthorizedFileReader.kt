package com.aichat.workbench.tool.local

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

class AndroidAuthorizedFileReader(
    private val context: Context,
) : AuthorizedFileReader {
    override suspend fun read(request: AuthorizedFileReadRequest): AuthorizedFileReadResult =
        try {
            readInternal(request)
        } catch (e: SecurityException) {
            AuthorizedFileReadResult(
                fileName = null,
                mimeType = null,
                sizeBytes = null,
                content = null,
                truncated = false,
                unsupportedReason = "文件访问权限已被撤销，请重新选择文件。",
            )
        }

    private fun readInternal(request: AuthorizedFileReadRequest): AuthorizedFileReadResult {
        val uri = Uri.parse(request.uri)
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)
        val metadata = resolver.query(uri, null, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                FileMetadata(
                    fileName = cursor.stringValue(OpenableColumns.DISPLAY_NAME),
                    sizeBytes = cursor.longValue(OpenableColumns.SIZE),
                )
            } else {
                FileMetadata(fileName = null, sizeBytes = null)
            }
        }
        val unsupportedReason = unsupportedFileReadReason(mimeType, metadata.fileName)
        if (unsupportedReason != null) {
            return AuthorizedFileReadResult(
                fileName = metadata.fileName,
                mimeType = mimeType,
                sizeBytes = metadata.sizeBytes,
                content = null,
                truncated = false,
                unsupportedReason = unsupportedReason,
            )
        }

        var truncated = false
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            val output = ByteArrayOutputStream()
            while (output.size() < request.maxBytes) {
                val read = input.read(buffer)
                if (read < 0) break
                val remaining = request.maxBytes - output.size()
                if (read > remaining) {
                    output.write(buffer, 0, remaining)
                    truncated = true
                    break
                }
                output.write(buffer, 0, read)
            }
            if (!truncated && input.read() >= 0) {
                truncated = true
            }
            output.toByteArray()
        } ?: throw LocalToolUnavailableException("无法打开授权文件。")
        val content = bytes.toString(Charsets.UTF_8)
        val encodingWarning = if (content.hasHighReplacementCharRatio()) {
            "文件可能不是 UTF-8 编码，部分内容可能显示异常。"
        } else {
            null
        }
        return AuthorizedFileReadResult(
            fileName = metadata.fileName,
            mimeType = mimeType,
            sizeBytes = metadata.sizeBytes,
            content = content,
            truncated = truncated,
            unsupportedReason = encodingWarning,
        )
    }
}

// Avoid passing replacement-heavy mojibake to the model as if it were real file content.
private fun String.hasHighReplacementCharRatio(): Boolean {
    if (length < 20) return false
    val replacementCount = count { it == '�' }
    return replacementCount > length * REPLACEMENT_CHAR_RATIO_THRESHOLD
}

private const val REPLACEMENT_CHAR_RATIO_THRESHOLD = 0.05

private data class FileMetadata(
    val fileName: String?,
    val sizeBytes: Long?,
)

private fun Cursor.stringValue(columnName: String): String? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getString(index) else null
}

private fun Cursor.longValue(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getLong(index) else null
}

internal fun unsupportedFileReadReason(mimeType: String?, fileName: String?): String? {
    val normalizedMime = mimeType.orEmpty().lowercase()
    val extension = fileName.orEmpty().substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return when {
        normalizedMime.startsWith("image/") ->
            "图片文件第一阶段只读取元信息；发送给模型前需要单独确认。"
        normalizedMime == "application/pdf" || extension == "pdf" ->
            "PDF 第一阶段暂不支持解析，请选择文本、Markdown、JSON 或代码文件。"
        normalizedMime in unsupportedOfficeMimes || extension in unsupportedOfficeExtensions ->
            "DOCX 等 Office 文件第一阶段暂不支持解析，请先导出为文本或 Markdown。"
        normalizedMime.startsWith("text/") || normalizedMime in textLikeMimes || extension in textLikeExtensions ->
            null
        normalizedMime.isBlank() && extension.isBlank() ->
            "无法识别文件类型，请选择文本、Markdown、JSON 或代码文件。"
        else ->
            "当前文件类型暂不支持解析，请选择文本、Markdown、JSON 或代码文件。"
    }
}

private val textLikeMimes = setOf(
    "application/json",
    "application/xml",
    "application/javascript",
    "application/x-javascript",
    "application/typescript",
    "application/x-sh",
    "application/x-python-code",
)

private val textLikeExtensions = setOf(
    "txt",
    "md",
    "markdown",
    "json",
    "xml",
    "yaml",
    "yml",
    "toml",
    "csv",
    "tsv",
    "kt",
    "kts",
    "java",
    "go",
    "rs",
    "py",
    "js",
    "ts",
    "tsx",
    "jsx",
    "html",
    "css",
    "scss",
    "sh",
    "gradle",
)

private val unsupportedOfficeMimes = setOf(
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/msword",
)

private val unsupportedOfficeExtensions = setOf("doc", "docx")
