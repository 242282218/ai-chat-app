package com.aichat.workbench.data.mapper

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelCapabilitySource
import com.aichat.workbench.domain.model.ModelConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val MAPPER_JSON = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

fun List<MessagePart>.toJson(): String =
    MAPPER_JSON.encodeToString(
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
    return runCatching { MAPPER_JSON.decodeFromString<List<MessagePartJson>>(value) }
        .getOrDefault(emptyList())
        .mapNotNull { part ->
        when (part.type) {
            "text" -> MessagePart.Text(part.text.orEmpty())
            "image" -> MessagePart.Image(part.uri.orEmpty(), part.mimeType)
            else -> null
        }
    }
}

fun Map<String, String>.toJsonObjectString(): String =
    MAPPER_JSON.encodeToString<Map<String, String>>(toSortedMap())

fun stringMapFromJson(value: String): Map<String, String> {
    if (value.isBlank()) return emptyMap()
    return MAPPER_JSON.decodeFromString(value)
}

fun List<ModelConfig>.toModelConfigsJson(): String =
    MAPPER_JSON.encodeToString(map { it.toJsonModel() })

fun modelConfigsFromJson(value: String): List<ModelConfig> {
    if (value.isBlank()) return emptyList()
    return MAPPER_JSON.decodeFromString<List<ModelConfigJson>>(value).map { it.toDomain() }
}

fun ModelCapability.toJson(): String =
    MAPPER_JSON.encodeToString(toJsonModel())

fun modelCapabilityFromJson(value: String): ModelCapability =
    MAPPER_JSON.decodeFromString<ModelCapabilityJson>(value).toDomain()

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
        maxContextTokens = maxContextTokens,
        source = source,
    )

private fun ModelCapabilityJson.toDomain(): ModelCapability =
    ModelCapability(
        model = model,
        text = text,
        vision = vision,
        imageGeneration = imageGeneration,
        maxContextTokens = maxContextTokens,
        source = source,
    )

@Serializable
private data class MessagePartJson(
    val type: String,
    val text: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
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
    val maxContextTokens: Int? = null,
    val source: ModelCapabilitySource = ModelCapabilitySource.UserOverride,
)
