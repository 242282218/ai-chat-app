package com.aichat.workbench.agent.skill

import com.aichat.workbench.domain.model.Skill
import com.aichat.workbench.domain.model.SkillId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class SkillFileParserTest {
    private val parser = SkillFileParser()

    @Test
    fun parseReadsFrontMatterAndPromptBody() {
        val skill = parser.parse(
            id = SkillId("code-task"),
            source = """
                ---
                name: Code Task
                description: Plan code changes
                summary: Use for code work
                ---

                # Code Task

                Inspect files before editing.
            """.trimIndent(),
        )

        assertEquals(SkillId("code-task"), skill.id)
        assertEquals("Code Task", skill.name)
        assertEquals("Plan code changes", skill.description)
        assertEquals("Use for code work", skill.summary)
        assertTrue(skill.prompt.startsWith("# Code Task"))
        assertTrue(skill.prompt.contains("Inspect files before editing."))
    }

    @Test
    fun parseFallsBackToIdAndGeneratedSummaryWithoutFrontMatter() {
        val skill = parser.parse(
            id = SkillId("plain"),
            source = """
                # Plain

                First actionable sentence.
                Second actionable sentence.
            """.trimIndent(),
        )

        assertEquals("plain", skill.name)
        assertEquals("plain", skill.description)
        assertEquals("First actionable sentence. Second actionable sentence.", skill.summary)
    }

    @Test
    fun parseRejectsBlankPrompt() {
        val error = assertFailsWith<IllegalArgumentException> {
            parser.parse(
                id = SkillId("blank"),
                source = """
                    ---
                    name: Blank
                    ---
                """.trimIndent(),
            )
        }

        assertEquals("Skill blank prompt 不能为空。", error.message)
    }

    @Test
    fun inMemoryRegistryReturnsSortedSkillsByNameAndLookupById() {
        val codeSkill = skill(id = "code-task", name = "Code Task")
        val webSkill = skill(id = "web-research", name = "Web Research")
        val registry = InMemorySkillRegistry(listOf(webSkill, codeSkill))

        assertEquals(listOf(codeSkill, webSkill), registry.listSkills())
        assertEquals(webSkill, registry.getSkill(SkillId("web-research")))
        assertEquals(null, registry.getSkill(SkillId("missing")))
    }

    private fun skill(id: String, name: String): Skill =
        Skill(
            id = SkillId(id),
            name = name,
            description = "$name description",
            summary = "$name summary",
            prompt = "$name prompt",
        )
}
