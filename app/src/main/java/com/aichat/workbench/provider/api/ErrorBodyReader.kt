package com.aichat.workbench.provider.api

import okhttp3.ResponseBody

/**
 * Safely reads error response body with size limit to prevent OOM from
 * malicious providers returning huge error pages.
 */
private const val MAX_ERROR_BODY_BYTES = 8 * 1024 // 8 KB cap for error bodies
const val MAX_JSON_BODY_BYTES = 2 * 1024 * 1024 // 2 MB cap for normal JSON responses

fun ResponseBody?.readErrorBodySafely(): String {
    if (this == null) return ""
    return try {
        readBodyWithLimit(MAX_ERROR_BODY_BYTES)
    } catch (e: Exception) {
        ""
    }
}

fun ResponseBody.readJsonBodySafely(): String =
    readBodyWithLimit(MAX_JSON_BODY_BYTES)

fun ResponseBody.readBodyWithLimit(maxBytes: Int): String {
    val declaredLength = contentLength()
    require(declaredLength <= maxBytes || declaredLength == -1L) {
        "响应体过大：$declaredLength bytes，最大允许 $maxBytes bytes。"
    }
    source().use { src ->
        src.request(maxBytes.toLong() + 1)
        val size = minOf(src.buffer.size, maxBytes.toLong() + 1)
        require(size <= maxBytes) {
            "响应体超过最大限制：$maxBytes bytes。"
        }
        return src.buffer.readUtf8(size)
    }
}
