package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.repository.PromptPresetRepository

class SavePromptPresetUseCase(
    private val repository: PromptPresetRepository,
) {
    suspend operator fun invoke(promptPreset: PromptPreset) {
        require(promptPreset.name.isNotBlank()) { "Prompt preset name must not be blank." }
        require(promptPreset.systemPrompt.isNotBlank()) { "Prompt preset system prompt must not be blank." }
        repository.savePromptPreset(promptPreset)
    }
}
