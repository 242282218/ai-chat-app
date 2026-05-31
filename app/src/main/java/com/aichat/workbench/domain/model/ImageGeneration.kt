package com.aichat.workbench.domain.model

import java.time.Instant

data class ImageGeneration(
    val id: ImageGenerationId,
    val conversationId: ConversationId?,
    val prompt: String,
    val providerId: ProviderId?,
    val model: String?,
    val size: String?,
    val quality: String?,
    val count: Int,
    val originalPath: String?,
    val thumbnailPath: String?,
    val status: ImageGenerationStatus,
    val errorSummary: String?,
    val createdAt: Instant,
)

enum class ImageGenerationStatus {
    Pending,
    Completed,
    Failed,
}
