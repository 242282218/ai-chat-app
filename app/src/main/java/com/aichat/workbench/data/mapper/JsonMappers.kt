package com.aichat.workbench.data.mapper

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelCapabilitySource
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ModelParameters
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val mapperJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

fun ModelParameters.toJson(): String =
    mapperJson.encodeToString(
        ModelParametersJson(
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
        ),
    )

fun modelParametersFromJson(value: String): ModelParameters {
    if (value.isBlank()) return ModelParameters()
    val json = mapperJson.decodeFromString<ModelParametersJson>(value)
    return ModelParameters(
        temperature = json.temperature,
        topP = json.topP,
        maxTokens = json.maxTokens,
    )
}

fun List<MessagePart>.toJson(): String =
    mapperJson.encodeToString(
        map { part ->
            when (part) {
                is MessagePart.Text -> MessagePartJson(type = "text", text = part.text)
                is MessagePart.Image -> MessagePartJson(
                    type = "image",
                    uri = part.uri,
                    mimeType = part.mimeType,
                )
            }
        },
    )

fun messagePartsFromJson(value: String): List<MessagePart> {
    if (value.isBlank()) return emptyList()
    return mapperJson.decodeFromString<List<MessagePartJson>>(value).mapNotNull { part ->
        when (part.type) {
            "text" -> MessagePart.Text(part.text.orEmpty())
            "image" -> MessagePart.Image(part.uri.orEmpty(), part.mimeType)
            else -> null
        }
    }
}

fun List<ToolCall>.toToolCallsJson(): String =
    mapperJson.encodeToString(
        map { call ->
            ToolCallJson(
                id = call.id.value,
                name = call.name,
                arguments = call.arguments,
            )
        },
    )

fun toolCallsFromJson(value: String?): List<ToolCall> {
    if (value.isNullOrBlank()) return emptyList()
    return mapperJson.decodeFromString<List<ToolCallJson>>(value).map { call ->
        ToolCall(
            id = ToolCallId(call.id),
            name = call.name,
            arguments = call.arguments,
        )
    }
}

fun List<String>.toJsonArrayString(): String =
    mapperJson.encodeToString(this)

fun stringListFromJson(value: String): List<String> {
    if (value.isBlank()) return emptyList()
    return mapperJson.decodeFromString(value)
}

fun Map<String, String>.toJsonObjectString(): String =
    mapperJson.encodeToString<Map<String, String>>(toSortedMap())

fun stringMapFromJson(value: String): Map<String, String> {
    if (value.isBlank()) return emptyMap()
    return mapperJson.decodeFromString(value)
}

fun List<ModelConfig>.toModelConfigsJson(): String =
    mapperJson.encodeToString(map { it.toJsonModel() })

fun modelConfigsFromJson(value: String): List<ModelConfig> {
    if (value.isBlank()) return emptyList()
    return mapperJson.decodeFromString<List<ModelConfigJson>>(value).map { it.toDomain() }
}

fun ModelCapability.toJson(): String =
    mapperJson.encodeToString(toJsonModel())

fun modelCapabilityFromJson(value: String): ModelCapability =
    mapperJson.decodeFromString<ModelCapabilityJson>(value).toDomain()

private fun ModelConfig.toJsonModel(): ModelConfigJson =
    ModelConfigJson(
        id = id,
        displayName = displayName,
        capability = capability?.toJsonModel(),
    )

private fun ModelConfigJson.toDomain(): ModelConfig =
    ModelConfig(
        id = id,
        displayName = displayName ?: id,
        capability = capability?.toDomain(),
    )

private fun ModelCapability.toJsonModel(): ModelCapabilityJson =
    ModelCapabilityJson(
        model = model,
        text = text,
        vision = vision,
        imageGeneration = imageGeneration,
        toolCalling = toolCalling,
        structuredOutput = structuredOutput,
        longContext = longContext,
        maxContextTokens = maxContextTokens,
        source = source,
    )

private fun ModelCapabilityJson.toDomain(): ModelCapability =
    ModelCapability(
        model = model,
        text = text,
        vision = vision,
        imageGeneration = imageGeneration,
        toolCalling = toolCalling,
        structuredOutput = structuredOutput,
        longContext = longContext,
        maxContextTokens = maxContextTokens,
        source = source,
    )

@Serializable
private data class ModelParametersJson(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
)

@Serializable
private data class MessagePartJson(
    val type: String,
    val text: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
)

@Serializable
private data class ToolCallJson(
    val id: String,
    val name: String,
    val arguments: String,
)

@Serializable
private data class ModelConfigJson(
    val id: String,
    val displayName: String? = null,
    val capability: ModelCapabilityJson? = null,
)

@Serializable
private data class ModelCapabilityJson(
    val model: String,
    val text: Boolean,
    val vision: Boolean,
    val imageGeneration: Boolean,
    val toolCalling: Boolean,
    val structuredOutput: Boolean,
    val longContext: Boolean,
    val maxContextTokens: Int? = null,
    val source: ModelCapabilitySource = ModelCapabilitySource.UserOverride,
)
