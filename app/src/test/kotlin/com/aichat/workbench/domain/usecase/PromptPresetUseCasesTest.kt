package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.repository.PromptPresetRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class PromptPresetUseCasesTest {
    @Test
    fun savePromptPresetNormalizesDraftBeforeSaving() = runTest {
        val repository = RecordingPromptPresetRepository()
        val savePromptPreset = SavePromptPresetUseCase(repository)

        savePromptPreset(
            prompt(
                name = " Reviewer ",
                description = " ",
                systemPrompt = " Review Kotlin code ",
                defaultModel = " gpt-4.1-mini ",
                defaultToolNames = listOf(" web_search ", "", "web_search", " code_sandbox "),
            ),
        )

        val saved = requireNotNull(repository.savedPromptPreset)
        assertEquals("Reviewer", saved.name)
        assertNull(saved.description)
        assertEquals("Review Kotlin code", saved.systemPrompt)
        assertEquals("gpt-4.1-mini", saved.defaultModel)
        assertEquals(listOf("web_search", "code_sandbox"), saved.defaultToolNames)
    }

    @Test
    fun savePromptPresetRejectsBlankRequiredFieldsAfterTrimming() = runTest {
        val repository = RecordingPromptPresetRepository()
        val savePromptPreset = SavePromptPresetUseCase(repository)

        try {
            savePromptPreset(prompt(name = " ", systemPrompt = "valid"))
            fail("Expected blank name to be rejected.")
        } catch (error: IllegalArgumentException) {
            assertEquals("Prompt preset name must not be blank.", error.message)
        }

        try {
            savePromptPreset(prompt(name = "Reviewer", systemPrompt = " "))
            fail("Expected blank system prompt to be rejected.")
        } catch (error: IllegalArgumentException) {
            assertEquals("Prompt preset system prompt must not be blank.", error.message)
        }

        assertNull(repository.savedPromptPreset)
    }

    private fun prompt(
        name: String = "Prompt",
        description: String? = "description",
        systemPrompt: String = "system prompt",
        defaultModel: String? = "model",
        defaultToolNames: List<String> = emptyList(),
    ): PromptPreset =
        PromptPreset(
            id = PromptPresetId("prompt-1"),
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            defaultModel = defaultModel,
            defaultToolNames = defaultToolNames,
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-06-01T00:00:00Z"),
        )
}

private class RecordingPromptPresetRepository : PromptPresetRepository {
    private val prompts = MutableStateFlow<List<PromptPreset>>(emptyList())
    var savedPromptPreset: PromptPreset? = null

    override fun observePromptPresets(): Flow<List<PromptPreset>> =
        prompts

    override suspend fun getPromptPreset(id: PromptPresetId): PromptPreset? =
        prompts.value.firstOrNull { it.id == id }

    override suspend fun savePromptPreset(promptPreset: PromptPreset) {
        savedPromptPreset = promptPreset
        prompts.value = listOf(promptPreset)
    }

    override suspend fun deletePromptPreset(id: PromptPresetId) {
        prompts.value = prompts.value.filterNot { it.id == id }
    }
}
