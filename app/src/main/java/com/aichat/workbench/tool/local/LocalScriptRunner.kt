package com.aichat.workbench.tool.local

import java.nio.charset.StandardCharsets

interface LocalScriptRunner {
    fun isSupported(): Boolean

    suspend fun run(request: LocalScriptRunRequest): LocalScriptRunResult
}

data class LocalScriptRunRequest(
    val language: ScriptLanguage,
    val code: String,
    val inputJson: String?,
    val timeoutMillis: Long,
    val outputLimitBytes: Int,
)

data class LocalScriptRunResult(
    val output: String,
    val durationMs: Long,
    val timedOut: Boolean,
    val truncated: Boolean,
)

enum class ScriptLanguage {
    JavaScript,
}

class LocalScriptUnavailableException(message: String) : RuntimeException(message)

class LocalScriptExecutionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class UnsupportedLocalScriptRunner : LocalScriptRunner {
    override fun isSupported(): Boolean = false

    override suspend fun run(request: LocalScriptRunRequest): LocalScriptRunResult {
        throw LocalScriptUnavailableException("当前设备不支持本地 JavaScript 沙箱。")
    }
}

fun truncateUtf8(value: String, limitBytes: Int): TruncatedText {
    require(limitBytes > 0) { "limitBytes must be positive." }
    val bytes = value.toByteArray(StandardCharsets.UTF_8)
    if (bytes.size <= limitBytes) {
        return TruncatedText(value, truncated = false, byteCount = bytes.size)
    }

    var end = value.length
    while (end > 0 && value.substring(0, end).toByteArray(StandardCharsets.UTF_8).size > limitBytes) {
        end -= 1
    }
    val truncated = value.substring(0, end)
    return TruncatedText(
        text = truncated,
        truncated = true,
        byteCount = truncated.toByteArray(StandardCharsets.UTF_8).size,
    )
}

data class TruncatedText(
    val text: String,
    val truncated: Boolean,
    val byteCount: Int,
)
