package com.aichat.workbench.tool.runtime

import java.net.URI

internal fun String.isValidGatewayUrl(): Boolean {
    val uri = runCatching { URI(trim()) }.getOrNull() ?: return false
    return uri.host != null && uri.scheme?.lowercase() in setOf("http", "https")
}
