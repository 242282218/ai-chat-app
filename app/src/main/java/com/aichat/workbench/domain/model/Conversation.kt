package com.aichat.workbench.domain.model

import java.time.Instant

data class Conversation(
    val id: ConversationId,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val defaultProviderId: ProviderId?,
    val defaultModel: String?,
    val modelParameters: ModelParameters,
    val systemPrompt: String?,
    val isTemporary: Boolean,
    val isSensitive: Boolean,
    val archivedAt: Instant?,
)

data class ModelParameters(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
)
