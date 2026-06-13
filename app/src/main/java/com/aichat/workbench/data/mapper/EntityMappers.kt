package com.aichat.workbench.data.mapper

import com.aichat.workbench.data.local.entity.ConversationEntity
import com.aichat.workbench.data.local.entity.ConversationWithPreview
import com.aichat.workbench.data.local.entity.ImageGenerationEntity
import com.aichat.workbench.data.local.entity.MessageEntity
import com.aichat.workbench.data.local.entity.ModelRolePreferenceEntity
import com.aichat.workbench.data.local.entity.ProviderConfigEntity
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationPreview
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ModelRolePreferenceId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import java.time.Instant

fun Conversation.toEntity(): ConversationEntity =
    ConversationEntity(
        id = id.value,
        title = title,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        defaultProviderId = defaultProviderId?.value,
    )

fun ConversationEntity.toDomain(): Conversation =
    Conversation(
        id = ConversationId(id),
        title = title,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        defaultProviderId = defaultProviderId?.let(::ProviderId),
    )

fun Message.toEntity(): MessageEntity =
    MessageEntity(
        id = id.value,
        conversationId = conversationId.value,
        role = role.name,
        content = content,
        contentPartsJson = contentParts.toJson(),
        providerId = providerId?.value,
        model = model,
        status = status.name,
        errorSummary = errorSummary,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        parentMessageId = parentMessageId?.value,
    )

fun MessageEntity.toDomain(): Message =
    normalizedLegacyInlineImages(
        content = content,
        contentParts = messagePartsFromJson(contentPartsJson),
    ).let { normalized ->
        Message(
            id = MessageId(id),
            conversationId = ConversationId(conversationId),
            role = role.toMessageRole(),
            content = normalized.content,
            contentParts = normalized.contentParts,
            providerId = providerId?.let(::ProviderId),
            model = model,
            status = status.toMessageStatus(),
            errorSummary = errorSummary,
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
            parentMessageId = parentMessageId?.let(::MessageId),
        )
    }

fun ModelRolePreferenceEntity.toDomainOrNull(): ModelRolePreference? {
    val parsedRole = ModelRole.entries.firstOrNull { it.name == role } ?: return null
    return ModelRolePreference(
        id = ModelRolePreferenceId(id),
        providerId = ProviderId(providerId),
        role = parsedRole,
        model = model,
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )
}

fun ProviderConfig.toEntity(createdAt: Instant, updatedAt: Instant): ProviderConfigEntity =
    ProviderConfigEntity(
        id = id.value,
        name = name,
        type = type.value,
        baseUrl = baseUrl,
        apiKeyRef = apiKeyRef,
        headersJson = headers.toJsonObjectString(),
        modelsJson = models.toModelConfigsJson(),
        defaultModel = defaultModel,
        enabled = enabled,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )

fun ProviderConfigEntity.toDomain(): ProviderConfig =
    ProviderConfig(
        id = ProviderId(id),
        name = name,
        type = ProviderType.fromStorage(type),
        baseUrl = baseUrl,
        apiKeyRef = apiKeyRef,
        headers = stringMapFromJson(headersJson),
        models = modelConfigsFromJson(modelsJson),
        defaultModel = defaultModel,
        enabled = enabled,
    )

fun ImageGeneration.toEntity(): ImageGenerationEntity =
    ImageGenerationEntity(
        id = id.value,
        conversationId = conversationId?.value,
        prompt = prompt,
        providerId = providerId?.value,
        model = model,
        size = size,
        quality = quality,
        count = count,
        originalPath = originalPath,
        thumbnailPath = thumbnailPath,
        status = status.name,
        errorSummary = errorSummary,
        createdAt = createdAt.toEpochMilli(),
    )

fun ImageGenerationEntity.toDomain(): ImageGeneration =
    ImageGeneration(
        id = ImageGenerationId(id),
        conversationId = conversationId?.let(::ConversationId),
        prompt = prompt,
        providerId = providerId?.let(::ProviderId),
        model = model,
        size = size,
        quality = quality,
        count = count,
        originalPath = originalPath,
        thumbnailPath = thumbnailPath,
        status = ImageGenerationStatus.entries.firstOrNull { it.name == status }
            ?: ImageGenerationStatus.Failed,
        errorSummary = errorSummary,
        createdAt = Instant.ofEpochMilli(createdAt),
    )

private fun String.toMessageRole(): MessageRole {
    val normalized = trim()
    return MessageRole.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        ?: when (normalized.lowercase()) {
            "function", "tool" -> MessageRole.Assistant
            else -> MessageRole.User
        }
}

private fun String.toMessageStatus(): MessageStatus {
    val normalized = trim()
    return MessageStatus.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        ?: when (normalized.lowercase()) {
            "canceled" -> MessageStatus.Cancelled
            else -> MessageStatus.Completed
        }
}

private fun normalizedLegacyInlineImages(
    content: String,
    contentParts: List<MessagePart>,
): NormalizedLegacyMessageContent {
    if (contentParts.any { it is MessagePart.Image } || !content.contains("![")) {
        return NormalizedLegacyMessageContent(content = content, contentParts = contentParts)
    }

    val legacyImages = LEGACY_INLINE_IMAGE_MARKDOWN
        .findAll(content)
        .mapNotNull { match ->
            val uri = match.groupValues[1].trim()
            uri.takeIf { it.looksLikeLegacyInlineImageUri() }
                ?.let { MessagePart.Image(uri = it, mimeType = it.inferLegacyInlineImageMimeType()) }
        }
        .toList()
    if (legacyImages.isEmpty()) {
        return NormalizedLegacyMessageContent(content = content, contentParts = contentParts)
    }

    val strippedContent = LEGACY_INLINE_IMAGE_MARKDOWN
        .replace(content) { match ->
            val uri = match.groupValues[1].trim()
            if (uri.looksLikeLegacyInlineImageUri()) {
                ""
            } else {
                match.value
            }
        }
        .replace(LEGACY_INLINE_IMAGE_WHITESPACE, "\n\n")
        .trim()

    return NormalizedLegacyMessageContent(
        content = strippedContent,
        contentParts = contentParts + legacyImages,
    )
}

private fun String.looksLikeLegacyInlineImageUri(): Boolean {
    val normalized = trim()
    val lowercase = normalized.lowercase()
    return lowercase.startsWith("data:image") ||
        lowercase.startsWith("file://") ||
        lowercase.startsWith("http://") ||
        lowercase.startsWith("https://") ||
        normalized.startsWith("/") ||
        WINDOWS_ABSOLUTE_PATH.matches(normalized)
}

private fun String.inferLegacyInlineImageMimeType(): String? {
    val normalized = trim()
    val lowercase = normalized.lowercase()
    return when {
        lowercase.startsWith("data:image/jpeg") -> "image/jpeg"
        lowercase.startsWith("data:image/jpg") -> "image/jpeg"
        lowercase.startsWith("data:image/webp") -> "image/webp"
        lowercase.startsWith("data:image/gif") -> "image/gif"
        lowercase.startsWith("data:image/") -> "image/png"
        lowercase.endsWith(".jpg") || lowercase.endsWith(".jpeg") -> "image/jpeg"
        lowercase.endsWith(".webp") -> "image/webp"
        lowercase.endsWith(".gif") -> "image/gif"
        lowercase.endsWith(".png") -> "image/png"
        else -> null
    }
}

private data class NormalizedLegacyMessageContent(
    val content: String,
    val contentParts: List<MessagePart>,
)

private val LEGACY_INLINE_IMAGE_MARKDOWN = Regex("""!\[[^\]]*]\(([^)\r\n]+)\)""")
private val LEGACY_INLINE_IMAGE_WHITESPACE = Regex("""(\s*\n){3,}""")
private val WINDOWS_ABSOLUTE_PATH = Regex("""^[A-Za-z]:[\\/].+""")

fun ConversationWithPreview.toPreview(): ConversationPreview = ConversationPreview(
    id = ConversationId(id),
    title = title,
    createdAt = java.time.Instant.ofEpochMilli(createdAt),
    updatedAt = java.time.Instant.ofEpochMilli(updatedAt),
    defaultProviderId = defaultProviderId?.let(::ProviderId),
    lastMessageContent = lastMessageContent,
    lastMessageRole = lastMessageRole,
)