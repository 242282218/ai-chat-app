package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.provider.supportsTextGeneration

internal fun ProviderConfig?.availableChatModels(): List<ModelConfig> =
    this?.models.orEmpty().filter { it.supportsTextGeneration() }
