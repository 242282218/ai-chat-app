package com.aichat.workbench.domain.model

data class ModelParameters(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
)
