package com.aichat.workbench.tool.local

import com.aichat.workbench.agent.skill.SkillRegistry
import com.aichat.workbench.domain.model.Skill
import com.aichat.workbench.domain.model.SkillId
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

class LoadSkillTool(
    private val skillRegistry: SkillRegistry,
) : LocalTool {
    override val descriptor: ToolDescriptor = LoadSkillToolDescriptor

    override suspend fun execute(request: LocalToolRequest): LocalToolExecution {
        val args = decodeLocalToolArguments<LoadSkillArguments>(request.toolCall.arguments)
        val skillId = args.skillId.trim()
        if (skillId.isBlank()) {
            throw InvalidLocalToolArgumentsException("skillId 不能为空。")
        }
        val skill = skillRegistry.getSkill(SkillId(skillId))
            ?: throw LocalToolUnavailableException("未找到内置 Skill：$skillId。")
        return LocalToolExecution(
            output = ToolOutput.Json(localToolJson.encodeToString(skill.toOutput())),
        )
    }
}

val LoadSkillToolDescriptor: ToolDescriptor = ToolDescriptor(
    name = "load_skill",
    displayName = "加载 Skill",
    description = "读取一个内置 Skill 的摘要和完整 prompt，用于按任务类型加载可复用工作流。",
    permissionLevel = ToolPermissionLevel.ReadOnly,
    inputSchemaJson = """
        {
          "type": "object",
          "required": ["skillId"],
          "properties": {
            "skillId": {
              "type": "string",
              "enum": ["code-task", "web-research", "image-generation"],
              "description": "要加载的内置 Skill ID。"
            }
          }
        }
    """.trimIndent(),
    outputSchemaJson = """{"type":"object"}""",
    timeoutSeconds = null,
    source = ToolSource.BuiltIn,
    riskLevel = ToolRiskLevel.Low,
    requiresNetwork = false,
    requiresFileAccess = false,
    defaultPermissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
)

@Serializable
private data class LoadSkillArguments(
    val skillId: String = "",
)

@Serializable
private data class LoadSkillOutput(
    val id: String,
    val name: String,
    val description: String,
    val summary: String,
    val prompt: String,
)

private fun Skill.toOutput(): LoadSkillOutput =
    LoadSkillOutput(
        id = id.value,
        name = name,
        description = description,
        summary = summary,
        prompt = prompt,
    )
