package com.aichat.workbench.tool.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Sanitizes sensitive data from tool call arguments before persistence.
 */
object SensitiveDataSanitizer {
    private const val MASK = "***REDACTED***"
    private val json = Json { ignoreUnknownKeys = true }

    private val commonSensitiveFields = setOf(
        "apikey", "api_key", "token", "password", "secret", "credential"
    )

    fun sanitize(argumentsJson: String, sensitiveFields: Set<String>): String {
        if (argumentsJson.isBlank()) return argumentsJson
        val allFields = sensitiveFields + commonSensitiveFields
        if (allFields.isEmpty()) return argumentsJson

        return try {
            val element = json.parseToJsonElement(argumentsJson)
            val sanitized = sanitizeElement(element, allFields)
            json.encodeToString(JsonElement.serializer(), sanitized)
        } catch (e: Exception) {
            argumentsJson
        }
    }

    private fun sanitizeElement(element: JsonElement, fields: Set<String>): JsonElement =
        when (element) {
            is JsonObject -> {
                val sanitized = element.mapValues { (key, value) ->
                    if (isSensitive(key, fields)) JsonPrimitive(MASK)
                    else if (value is JsonObject) sanitizeElement(value, fields)
                    else value
                }
                JsonObject(sanitized)
            }
            else -> element
        }

    private fun isSensitive(key: String, fields: Set<String>): Boolean {
        val normalized = key.lowercase().replace("-", "_")
        return fields.any { it.lowercase().replace("-", "_") == normalized }
    }
}
