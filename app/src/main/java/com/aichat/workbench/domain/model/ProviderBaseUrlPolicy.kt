package com.aichat.workbench.domain.model

import java.net.URI

fun String.isValidProviderBaseUrl(allowLocalHttp: Boolean): Boolean {
    val uri = runCatching { URI(trim()) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return when (uri.scheme?.lowercase()) {
        "https" -> true
        "http" -> allowLocalHttp && host.isAllowedLocalHttpHost()
        else -> false
    }
}

fun String.isLocalHttpProviderBaseUrl(): Boolean {
    val uri = runCatching { URI(trim()) }.getOrNull() ?: return false
    return uri.scheme.equals("http", ignoreCase = true) &&
        uri.host?.lowercase()?.isAllowedLocalHttpHost() == true
}

private fun String.isAllowedLocalHttpHost(): Boolean =
    this == "localhost" ||
        this == "127.0.0.1" ||
        this == "10.0.2.2" ||
        this == "10.0.3.2"
