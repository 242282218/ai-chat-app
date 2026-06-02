package com.aichat.workbench.feature.prompt

import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.PromptPresetId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PromptDraftsTest {
    @Test
    fun saveStatusExplainsMissingRequiredFields() {
        assertEquals(
            PromptActionStatus(label = "需要名称", isReady = false),
            promptSaveStatus(name = "", systemPrompt = "act as reviewer"),
        )
        assertEquals(
            PromptActionStatus(label = "需要系统指令", isReady = false),
            promptSaveStatus(name = "Reviewer", systemPrompt = ""),
        )
        assertEquals(
            PromptActionStatus(label = "可保存", isReady = true),
            promptSaveStatus(name = "Reviewer", systemPrompt = "act as reviewer"),
        )
    }

    @Test
    fun parsesPromptToolNamesWithoutBlankOrDuplicateEntries() {
        assertEquals(
            listOf("web_search", "code_sandbox"),
            parsePromptToolNames(" web_search, code_sandbox, web_search, , "),
        )
    }

    @Test
    fun summarizesPromptDefaults() {
        assertEquals("未绑定", promptDefaultsLabel(defaultModel = "", defaultTools = ""))
        assertEquals("模型", promptDefaultsLabel(defaultModel = "gpt-4.1-mini", defaultTools = ""))
        assertEquals("2 个工具", promptDefaultsLabel(defaultModel = "", defaultTools = "web_search, code_sandbox"))
        assertEquals(
            "模型 + 工具",
            promptDefaultsLabel(defaultModel = "gpt-4.1-mini", defaultTools = "web_search"),
        )
    }

    @Test
    fun filtersPromptPresetsBySearchableFields() {
        val presets = listOf(
            prompt(
                name = "Code Reviewer",
                description = "Pull request",
                systemPrompt = "Review Kotlin changes",
                defaultModel = "gpt-4.1-mini",
                defaultToolNames = listOf("web_search"),
            ),
            prompt(
                name = "Translator",
                description = null,
                systemPrompt = "Translate to Chinese",
                defaultModel = null,
                defaultToolNames = emptyList(),
            ),
        )

        assertEquals(listOf("Code Reviewer"), presets.filterByQuery("kotlin").map { it.name })
        assertEquals(listOf("Code Reviewer"), presets.filterByQuery("web_search").map { it.name })
        assertEquals(listOf("Translator"), presets.filterByQuery(" chinese ").map { it.name })
        assertEquals(presets, presets.filterByQuery(""))
    }

    @Test
    fun buildsPromptSummaryText() {
        assertEquals(
            "已描述 · gpt-4.1-mini · 1 个工具",
            prompt(
                description = "review",
                defaultModel = "gpt-4.1-mini",
                defaultToolNames = listOf("web_search"),
            ).summaryText(),
        )
        assertEquals(
            "无描述 · 无默认模型 · 无默认工具",
            prompt(description = null, defaultModel = null, defaultToolNames = emptyList()).summaryText(),
        )
    }

    @Test
    fun previewsPromptTextAfterTrimming() {
        assertEquals("short", " short ".previewPromptText(10))
        assertEquals("abcdefg...", "abcdefghijk".previewPromptText(10))
    }

    private fun prompt(
        name: String = "Prompt",
        description: String? = "description",
        systemPrompt: String = "system prompt",
        defaultModel: String? = "model",
        defaultToolNames: List<String> = listOf("tool"),
    ): PromptPreset =
        PromptPreset(
            id = PromptPresetId(name.lowercase().replace(" ", "-")),
            name = name,
            description = description,
            systemPrompt = systemPrompt,
            defaultModel = defaultModel,
            defaultToolNames = defaultToolNames,
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-06-01T00:00:00Z"),
        )
}
