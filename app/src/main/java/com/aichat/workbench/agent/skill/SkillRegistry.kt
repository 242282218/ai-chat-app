package com.aichat.workbench.agent.skill

import android.content.Context
import com.aichat.workbench.domain.model.Skill
import com.aichat.workbench.domain.model.SkillId

interface SkillRegistry {
    fun listSkills(): List<Skill>

    fun getSkill(id: SkillId): Skill?
}

class InMemorySkillRegistry(
    skills: List<Skill>,
) : SkillRegistry {
    private val skillsById = skills.associateBy { it.id }

    override fun listSkills(): List<Skill> =
        skillsById.values.sortedBy { it.name }

    override fun getSkill(id: SkillId): Skill? =
        skillsById[id]
}

class AndroidAssetSkillRegistry(
    context: Context,
    private val parser: SkillFileParser = SkillFileParser(),
) : SkillRegistry {
    private val assets = context.assets
    private val skills: List<Skill> by lazy {
        BUILT_IN_SKILL_IDS.mapNotNull { id ->
            runCatching {
                assets.open("skills/${id.value}/SKILL.md").bufferedReader().use { reader ->
                    parser.parse(id = id, source = reader.readText())
                }
            }.getOrNull()
        }
    }

    override fun listSkills(): List<Skill> =
        skills

    override fun getSkill(id: SkillId): Skill? =
        skills.firstOrNull { it.id == id }

    companion object {
        val BUILT_IN_SKILL_IDS: List<SkillId> = listOf(
            SkillId("code-task"),
            SkillId("web-research"),
            SkillId("image-generation"),
        )
    }
}
