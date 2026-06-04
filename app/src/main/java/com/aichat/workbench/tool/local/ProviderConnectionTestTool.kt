package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

class ProviderConnectionTestTool(
    private val providerRepository: ProviderConfigRepository,
    private val runner: ProviderConnectionTestRunner,
) : LocalTool {
    override val descriptor: ToolDescriptor = ProviderConnectionTestToolDescriptor

    override suspend fun execute(request: LocalToolRequest): LocalToolExecution {
        val args = decodeLocalToolArguments<ProviderConnectionTestArguments>(request.toolCall.arguments)
        val providerId = args.providerId.trim()
        if (providerId.isBlank()) {
            throw InvalidLocalToolArgumentsException("providerId 不能为空。")
        }
        val provider = providerRepository.getProvider(ProviderId(providerId))
            ?: throw InvalidLocalToolArgumentsException("Provider 不存在：$providerId")
        val apiKey = providerRepository.getApiKey(provider.id)
        val result = runner.test(provider, apiKey)

        return LocalToolExecution(
            ToolOutput.Json(
                localToolJson.encodeToString(
                    ProviderConnectionTestOutput(
                        providerId = provider.id.value,
                        providerName = provider.name,
                        providerType = provider.type.value,
                        enabled = provider.enabled,
                        defaultModel = provider.defaultModel,
                        ok = result.ok,
                        statusCode = result.statusCode,
                        message = result.message,
                    ),
                ),
            ),
        )
    }
}

val ProviderConnectionTestToolDescriptor: ToolDescriptor = ToolDescriptor(
    name = "provider_connection_test",
    displayName = "Provider 连接测试",
    description = "使用已保存的 Provider 配置和安全存储中的 API Key 测试模型服务连接，不输出密钥。",
    permissionLevel = ToolPermissionLevel.Network,
    inputSchemaJson = """
        {
          "type": "object",
          "required": ["providerId"],
          "properties": {
            "providerId": {
              "type": "string",
              "description": "要测试的 Provider ID。"
            }
          }
        }
    """.trimIndent(),
    outputSchemaJson = """{"type":"object"}""",
    timeoutSeconds = 20,
    source = ToolSource.BuiltIn,
    riskLevel = ToolRiskLevel.Medium,
    requiresNetwork = true,
    requiresFileAccess = false,
    defaultPermissionPolicy = ToolPermissionPolicy.AskEveryTime,
)

@Serializable
private data class ProviderConnectionTestArguments(
    val providerId: String = "",
)

@Serializable
private data class ProviderConnectionTestOutput(
    val providerId: String,
    val providerName: String,
    val providerType: String,
    val enabled: Boolean,
    val defaultModel: String?,
    val ok: Boolean,
    val statusCode: Int?,
    val message: String,
)
