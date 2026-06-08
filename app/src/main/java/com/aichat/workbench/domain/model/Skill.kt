package com.aichat.workbench.domain.model

data class Skill(
    val id: SkillId,
    val name: String,
    val description: String,
    val summary: String,
    val prompt: String,
)
