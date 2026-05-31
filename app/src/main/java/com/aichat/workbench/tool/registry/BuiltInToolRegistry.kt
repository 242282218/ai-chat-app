package com.aichat.workbench.tool.registry

import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource

object BuiltInToolRegistry {
    val tools: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            name = "time",
            displayName = "Time",
            description = "Read the local device time.",
            permissionLevel = ToolPermissionLevel.ReadOnly,
            inputSchemaJson = """{"type":"object","properties":{}}""",
            outputSchemaJson = """{"type":"object"}""",
            timeoutSeconds = null,
            source = ToolSource.BuiltIn,
        ),
        ToolDescriptor(
            name = "image_generation",
            displayName = "Image generation",
            description = "Generate images through the configured image Provider.",
            permissionLevel = ToolPermissionLevel.ReadOnly,
            inputSchemaJson = """{"type":"object","required":["prompt"],"properties":{"prompt":{"type":"string"}}}""",
            outputSchemaJson = """{"type":"object"}""",
            timeoutSeconds = null,
            source = ToolSource.BuiltIn,
        ),
    )
}
