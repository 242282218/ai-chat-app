package com.aichat.workbench.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTaskTemplatesTest {
    @Test
    fun exposesWorkbenchMainPathTemplates() {
        val templates = homeTaskTemplates()

        assertEquals(
            listOf(
                HomeTaskTemplateKind.WebSearch,
                HomeTaskTemplateKind.LocalJs,
                HomeTaskTemplateKind.ImageGeneration,
            ),
            templates.map { it.kind },
        )
    }

    @Test
    fun templatesBuildToolReadyDrafts() {
        val templates = homeTaskTemplates().associateBy { it.kind }

        assertTrue(templates.getValue(HomeTaskTemplateKind.WebSearch).draft.contains("工具：web_search_local"))
        assertTrue(templates.getValue(HomeTaskTemplateKind.WebSearch).draft.contains("\"query\":\"今天 AI 新闻\""))
        assertTrue(templates.getValue(HomeTaskTemplateKind.LocalJs).draft.contains("工具：local_js"))
        assertTrue(templates.getValue(HomeTaskTemplateKind.LocalJs).draft.contains("\"language\":\"javascript\""))
        assertTrue(templates.getValue(HomeTaskTemplateKind.ImageGeneration).draft.contains("工具：image_generation"))
        assertTrue(templates.getValue(HomeTaskTemplateKind.ImageGeneration).draft.contains("\"count\":1"))
    }
}
