package com.aichat.workbench.data.mapper

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelParameters
import org.json.JSONArray
import org.json.JSONObject

fun ModelParameters.toJson(): String =
    JSONObject().apply {
        putNullable("temperature", temperature)
        putNullable("topP", topP)
        putNullable("maxTokens", maxTokens)
    }.toString()

fun modelParametersFromJson(value: String): ModelParameters {
    if (value.isBlank()) return ModelParameters()
    val json = JSONObject(value)
    return ModelParameters(
        temperature = json.optNullableDouble("temperature"),
        topP = json.optNullableDouble("topP"),
        maxTokens = json.optNullableInt("maxTokens"),
    )
}

fun List<MessagePart>.toJson(): String {
    val array = JSONArray()
    forEach { part ->
        val json = JSONObject()
        when (part) {
            is MessagePart.Text -> {
                json.put("type", "text")
                json.put("text", part.text)
            }
            is MessagePart.Image -> {
                json.put("type", "image")
                json.put("uri", part.uri)
                json.putNullable("mimeType", part.mimeType)
            }
        }
        array.put(json)
    }
    return array.toString()
}

fun messagePartsFromJson(value: String): List<MessagePart> {
    if (value.isBlank()) return emptyList()
    val array = JSONArray(value)
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.getJSONObject(index)
            when (json.optString("type")) {
                "text" -> add(MessagePart.Text(json.optString("text")))
                "image" -> add(MessagePart.Image(json.optString("uri"), json.optNullableString("mimeType")))
            }
        }
    }
}

fun List<String>.toJsonArrayString(): String {
    val array = JSONArray()
    forEach { array.put(it) }
    return array.toString()
}

fun stringListFromJson(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    val array = JSONArray(value)
    return buildList {
        for (index in 0 until array.length()) {
            add(array.getString(index))
        }
    }
}

fun Map<String, String>.toJsonObjectString(): String =
    JSONObject().apply {
        entries.sortedBy { it.key }.forEach { (key, value) ->
            put(key, value)
        }
    }.toString()

fun stringMapFromJson(value: String): Map<String, String> {
    if (value.isBlank()) return emptyMap()
    val json = JSONObject(value)
    return buildMap {
        json.keys().forEach { key ->
            put(key, json.getString(key))
        }
    }
}

fun List<ModelConfig>.toModelConfigsJson(): String {
    val array = JSONArray()
    forEach { model ->
        array.put(
            JSONObject().apply {
                put("id", model.id)
                put("displayName", model.displayName)
                putNullable("capability", model.capability?.let { JSONObject(it.toJson()) })
            },
        )
    }
    return array.toString()
}

fun modelConfigsFromJson(value: String): List<ModelConfig> {
    if (value.isBlank()) return emptyList()
    val array = JSONArray(value)
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.getJSONObject(index)
            add(
                ModelConfig(
                    id = json.getString("id"),
                    displayName = json.optString("displayName", json.getString("id")),
                    capability = json.optJSONObject("capability")?.let { modelCapabilityFromJson(it.toString()) },
                ),
            )
        }
    }
}

fun ModelCapability.toJson(): String =
    JSONObject().apply {
        put("model", model)
        put("text", text)
        put("vision", vision)
        put("imageGeneration", imageGeneration)
        put("toolCalling", toolCalling)
        put("structuredOutput", structuredOutput)
        put("longContext", longContext)
        putNullable("maxContextTokens", maxContextTokens)
    }.toString()

fun modelCapabilityFromJson(value: String): ModelCapability {
    val json = JSONObject(value)
    return ModelCapability(
        model = json.getString("model"),
        text = json.getBoolean("text"),
        vision = json.getBoolean("vision"),
        imageGeneration = json.getBoolean("imageGeneration"),
        toolCalling = json.getBoolean("toolCalling"),
        structuredOutput = json.getBoolean("structuredOutput"),
        longContext = json.getBoolean("longContext"),
        maxContextTokens = json.optNullableInt("maxContextTokens"),
    )
}

private fun JSONObject.putNullable(name: String, value: Any?) {
    if (value == null) {
        put(name, JSONObject.NULL)
    } else {
        put(name, value)
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null

private fun JSONObject.optNullableDouble(name: String): Double? =
    if (has(name) && !isNull(name)) getDouble(name) else null

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) getInt(name) else null
