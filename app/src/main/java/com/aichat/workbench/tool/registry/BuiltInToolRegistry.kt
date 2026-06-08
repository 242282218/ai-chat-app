package com.aichat.workbench.tool.registry

import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.local.CodeDiffPreviewToolDescriptor
import com.aichat.workbench.tool.local.FileReadToolDescriptor
import com.aichat.workbench.tool.local.LocalJsToolDescriptor
import com.aichat.workbench.tool.local.LocalWebSearchToolDescriptor
import com.aichat.workbench.tool.local.LoadSkillToolDescriptor
import com.aichat.workbench.tool.local.ProviderConnectionTestToolDescriptor
import com.aichat.workbench.tool.local.TextTransformToolDescriptor
import com.aichat.workbench.tool.local.TimeToolDescriptor
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource

object BuiltInToolRegistry {
    val tools: List<ToolDescriptor> = listOf(
        TimeToolDescriptor,
        TextTransformToolDescriptor,
        CodeDiffPreviewToolDescriptor,
        LocalWebSearchToolDescriptor,
        LocalJsToolDescriptor,
        FileReadToolDescriptor,
        LoadSkillToolDescriptor,
        ProviderConnectionTestToolDescriptor,
        ToolDescriptor(
            name = "image_upload_to_model",
            displayName = "图片发送给模型",
            description = "把用户已选择的图片作为多模态输入发送给当前模型；必须通过聊天输入栏确认，工具不会自动读取或上传本地图片。",
            permissionLevel = ToolPermissionLevel.HighRisk,
            inputSchemaJson = """
                {
                  "type": "object",
                  "required": ["imageUri"],
                  "properties": {
                    "imageUri": {
                      "type": "string",
                      "description": "用户通过系统图片选择器加入聊天草稿的图片 URI；不能手写本地路径。"
                    },
                    "purpose": {
                      "type": "string",
                      "description": "发送给模型前向用户说明的分析目的。"
                    }
                  }
                }
            """.trimIndent(),
            outputSchemaJson = """{"type":"object"}""",
            timeoutSeconds = null,
            source = ToolSource.BuiltIn,
            riskLevel = ToolRiskLevel.High,
            requiresNetwork = true,
            requiresFileAccess = true,
            defaultPermissionPolicy = ToolPermissionPolicy.AskEveryTime,
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
            riskLevel = ToolRiskLevel.Medium,
            requiresNetwork = true,
            requiresFileAccess = false,
            defaultPermissionPolicy = ToolPermissionPolicy.AskEveryTime,
        ),
    )
}
