package com.aichat.workbench.domain.model

fun isPersistableProviderHeader(name: String): Boolean =
    name.trim().lowercase() in persistableProviderHeaderNames

fun Map<String, String>.persistableProviderHeaders(): Map<String, String> =
    filter { (name, value) -> isPersistableProviderHeader(name) && value.isNotBlank() }

fun Map<String, String>.providerRequestHeaders(): Map<String, String> =
    persistableProviderHeaders()

val persistableProviderHeaderDisplayNames: List<String> =
    listOf(
        "X-Request-Id",
        "X-Client-Id",
        "X-Trace",
        "X-Title",
    )

private val persistableProviderHeaderNames =
    persistableProviderHeaderDisplayNames.map { it.lowercase() }.toSet()
