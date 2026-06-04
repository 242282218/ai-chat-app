package com.aichat.workbench.data.mapper

import com.aichat.workbench.data.local.entity.ConversationEntity
import com.aichat.workbench.data.local.entity.ImageGenerationEntity
import com.aichat.workbench.data.local.entity.MessageEntity
import com.aichat.workbench.data.local.entity.ModelPreferenceEntity
import com.aichat.workbench.data.local.entity.ModelRolePreferenceEntity
import com.aichat.workbench.data.local.entity.PromptPresetEntity
import com.aichat.workbench.data.local.entity.ProviderConfigEntity
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.ModelPreference
import com.aichat.workbench.domain.model.ModelPreferenceId
import com.aichat.workbench.domain.model.ModelRole
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ModelRolePreferenceId
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.domain.model.ToolCallId
import java.time.Instant

fun Conversation.toEntity(): ConversationEntity =
    ConversationEntity(
        id = id.value,
        title = title,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
        defaultProviderId = defaultProviderId?.value,
        defaultModel = defaultModel,
        modelParametersJson = modelParameters.toJson(),
        systemPrompt = systemPrompt,
        isTemporary = isTemporary,
        isSensitive = isSensitive,
        archivedAt = archivedAt?.toEpochMilli(),
    )

fun ConversationEntity.toDomain(): Conversation =
    Conversation(
        id = ConversationId(id),
        title = title,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        defaultProviderId = defaultProviderId?.let(::ProviderId),
        defaultModel = defaultModel,
        modelParameters = modelParametersFromJson(modelParametersJson),
        systemPrompt = systemPrompt,
        isTemporary = isTemporary,
        isSensitive = isSensitive,
        archivedAt = archivedAt?.let(Instant::ofEpochMilli),
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
        toolCallId = toolCallId?.value,
        parentMessageId = parentMessageId?.value,
        toolCallsJson = toolCalls.toToolCallsJson(),
        toolResult = toolResult,
    )

fun MessageEntity.toDomain(): Message =
    Message(
        id = MessageId(id),
        conversationId = ConversationId(conversationId),
        role = MessageRole.valueOf(role),
        content = content,
        contentParts = messagePartsFromJson(contentPartsJson),
        providerId = providerId?.let(::ProviderId),
        model = model,
        status = MessageStatus.valueOf(status),
        errorSummary = errorSummary,
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
        toolCallId = toolCallId?.let(::ToolCallId),
        parentMessageId = parentMessageId?.let(::MessageId),
        toolCalls = toolCallsFromJson(toolCallsJson),
        toolResult = toolResult,
    )

fun PromptPreset.toEntity(): PromptPresetEntity =
    PromptPresetEntity(
        id = id.value,
        name = name,
        description = description,
        systemPrompt = systemPrompt,
        defaultModel = defaultModel,
        defaultToolNamesJson = defaultToolNames.toJsonArrayString(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )

fun PromptPresetEntity.toDomain(): PromptPreset =
    PromptPreset(
        id = PromptPresetId(id),
        name = name,
        description = description,
        systemPrompt = systemPrompt,
        defaultModel = defaultModel,
        defaultToolNames = stringListFromJson(defaultToolNamesJson),
        createdAt = Instant.ofEpochMilli(createdAt),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

fun ModelPreference.toEntity(createdAt: Instant = updatedAt): ModelPreferenceEntity =
    ModelPreferenceEntity(
        id = id.value,
        providerId = providerId.value,
        model = model,
        isFavorite = isFavorite,
        isDefault = isDefault,
        capabilityJson = capability?.toJson(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt.toEpochMilli(),
    )

fun ModelPreferenceEntity.toDomain(): ModelPreference =
    ModelPreference(
        id = ModelPreferenceId(id),
        providerId = ProviderId(providerId),
        model = model,
        isFavorite = isFavorite,
        isDefault = isDefault,
        capability = capabilityJson?.let(::modelCapabilityFromJson),
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

fun ModelRolePreferenceEntity.toDomain(): ModelRolePreference =
    ModelRolePreference(
        id = ModelRolePreferenceId(id),
        providerId = ProviderId(providerId),
        role = ModelRole.valueOf(role),
        model = model,
        updatedAt = Instant.ofEpochMilli(updatedAt),
    )

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
        status = ImageGenerationStatus.valueOf(status),
        errorSummary = errorSummary,
        createdAt = Instant.ofEpochMilli(createdAt),
    )
