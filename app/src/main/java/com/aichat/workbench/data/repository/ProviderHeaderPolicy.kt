package com.aichat.workbench.data.repository

internal fun Map<String, String>.persistableProviderHeaders(): Map<String, String> =
    filter { (name, value) ->
        name.trim().lowercase() in PERSISTABLE_PROVIDER_HEADER_NAMES && value.isNotBlank()
    }

private val PERSISTABLE_PROVIDER_HEADER_NAMES = setOf(
    "x-request-id",
    "x-client-id",
    "x-trace",
)
