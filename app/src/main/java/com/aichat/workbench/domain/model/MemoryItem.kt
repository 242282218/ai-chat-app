package com.aichat.workbench.domain.model

import java.time.Instant

data class MemoryItem(
    val id: MemoryItemId,
    val kind: MemoryKind,
    val content: String,
    val sourceConversationId: ConversationId?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class MemoryKind {
    UserFact,
    ProjectFact,
    Preference,
    TaskConclusion,
}
