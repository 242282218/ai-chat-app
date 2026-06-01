package com.aichat.workbench.tool.registry

import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource

object BuiltInToolRegistry {
    val tools: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            name = "time",
            displayName = "本机时间",
            description = "读取设备本地时间。",
            permissionLevel = ToolPermissionLevel.ReadOnly,
            inputSchemaJson = """{"type":"object","properties":{}}""",
            outputSchemaJson = """{"type":"object"}""",
            timeoutSeconds = null,
            source = ToolSource.BuiltIn,
        ),
        ToolDescriptor(
            name = "image_generation",
            displayName = "图片生成",
            description = "通过已配置的图片 Provider 生成图片。",
            permissionLevel = ToolPermissionLevel.ReadOnly,
            inputSchemaJson = """{"type":"object","required":["prompt"],"properties":{"prompt":{"type":"string"}}}""",
            outputSchemaJson = """{"type":"object"}""",
            timeoutSeconds = null,
            source = ToolSource.BuiltIn,
        ),
    )
}
