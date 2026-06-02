package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.repository.PromptPresetRepository

class SavePromptPresetUseCase(
    private val repository: PromptPresetRepository,
) {
    suspend operator fun invoke(promptPreset: PromptPreset) {
        val normalizedPromptPreset = promptPreset.normalizedForSave()
        require(normalizedPromptPreset.name.isNotBlank()) { "Prompt preset name must not be blank." }
        require(normalizedPromptPreset.systemPrompt.isNotBlank()) { "Prompt preset system prompt must not be blank." }
        repository.savePromptPreset(normalizedPromptPreset)
    }

    private fun PromptPreset.normalizedForSave(): PromptPreset =
        copy(
            name = name.trim(),
            description = description?.trim()?.takeIf { it.isNotBlank() },
            systemPrompt = systemPrompt.trim(),
            defaultModel = defaultModel?.trim()?.takeIf { it.isNotBlank() },
            defaultToolNames = defaultToolNames
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct(),
        )
}
