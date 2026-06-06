package com.aichat.workbench.provider.api

import okhttp3.ResponseBody

/**
 * Safely reads error response body with size limit to prevent OOM from
 * malicious providers returning huge error pages.
 */
private const val MAX_ERROR_BODY_BYTES = 8 * 1024 // 8 KB cap for error bodies

fun ResponseBody?.readErrorBodySafely(): String {
    if (this == null) return ""
    return try {
        source().use { src ->
            src.request(MAX_ERROR_BODY_BYTES.toLong())
            val buffer = src.buffer
            val size = minOf(buffer.size, MAX_ERROR_BODY_BYTES.toLong())
            buffer.readUtf8(size)
        }
    } catch (e: Exception) {
        ""
    }
}
