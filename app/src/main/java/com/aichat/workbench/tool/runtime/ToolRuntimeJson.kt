package com.aichat.workbench.tool.runtime

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal val toolJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

internal inline fun <reified T> decodeToolArguments(arguments: String): T =
    try {
        toolJson.decodeFromString(arguments)
    } catch (error: SerializationException) {
        throw InvalidToolArgumentsException("工具参数 JSON 无效。", error)
    }
