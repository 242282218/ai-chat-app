package com.aichat.workbench.domain.model

import java.time.Instant

data class PromptPreset(
    val id: PromptPresetId,
    val name: String,
    val description: String?,
    val systemPrompt: String,
    val defaultModel: String?,
    val defaultToolNames: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
