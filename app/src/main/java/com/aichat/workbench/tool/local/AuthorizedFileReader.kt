package com.aichat.workbench.tool.local

interface AuthorizedFileReader {
    suspend fun read(request: AuthorizedFileReadRequest): AuthorizedFileReadResult
}

data class AuthorizedFileReadRequest(
    val uri: String,
    val maxBytes: Int,
)

data class AuthorizedFileReadResult(
    val fileName: String?,
    val mimeType: String?,
    val sizeBytes: Long?,
    val content: String?,
    val truncated: Boolean,
    val unsupportedReason: String?,
)

class UnsupportedAuthorizedFileReader : AuthorizedFileReader {
    override suspend fun read(request: AuthorizedFileReadRequest): AuthorizedFileReadResult {
        throw LocalToolUnavailableException("当前环境不支持读取 Android 授权文件。")
    }
}
