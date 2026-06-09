package com.aichat.workbench.domain.model

import java.time.Instant

data class Conversation(
    val id: ConversationId,
    val title: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val defaultProviderId: ProviderId?,
)
