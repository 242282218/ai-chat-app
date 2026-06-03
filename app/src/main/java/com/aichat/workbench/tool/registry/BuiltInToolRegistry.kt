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
            permissionLevel = ToolPermissionLevel.Network,
            inputSchemaJson = """
                {
                  "type": "object",
                  "required": ["prompt"],
                  "properties": {
                    "prompt": {
                      "type": "string",
                      "description": "图片生成提示词。"
                    },
                    "model": {
                      "type": "string",
                      "description": "可选图片模型；为空时使用图片生成页保存的模型偏好。"
                    },
                    "size": {
                      "type": "string",
                      "description": "可选图片尺寸，例如 1024x1024。"
                    },
                    "quality": {
                      "type": "string",
                      "description": "可选质量参数，例如 auto、standard 或 high。"
                    },
                    "count": {
                      "type": "integer",
                      "minimum": 1,
                      "maximum": 4,
                      "description": "生成图片数量，范围 1 到 4。"
                    }
                  }
                }
            """.trimIndent(),
            outputSchemaJson = """{"type":"object"}""",
            timeoutSeconds = null,
            source = ToolSource.BuiltIn,
        ),
    )
}
