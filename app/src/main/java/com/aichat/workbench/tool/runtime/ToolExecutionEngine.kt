package com.aichat.workbench.tool.runtime

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.tool.ToolExecution
import com.aichat.workbench.domain.tool.ToolExecutionCancelledException
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolRuntimeSetting
import com.aichat.workbench.tool.model.ToolSource
import com.aichat.workbench.tool.model.canonicalToolName
import com.aichat.workbench.tool.model.runtimeSettingFor
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal class ToolExecutionEngine(
    private val toolCatalog: ToolCatalogService,
    private val resultWriter: ToolResultWriter,
    private val localToolRunner: LocalToolRunner,
    private val gatewayToolRunner: GatewayToolRunner,
    private val imageGenerationToolRunner: ImageGenerationToolRunner,
    private val toolSettingsProvider: suspend () -> Map<String, ToolRuntimeSetting>,
    private val clock: Clock,
) {
    suspend fun execute(conversationId: ConversationId, toolCall: ToolCall): ToolExecution =
        execute(conversationId, toolCall, toolCatalog.descriptorForExecution(toolCall.name))

    suspend fun execute(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution {
        val toolDescriptor = descriptor
            ?: return resultWriter.saveFailure(
                conversationId = conversationId,
                toolCall = toolCall,
                toolName = toolCall.name.canonicalToolName(),
                permissionLevel = ToolPermissionLevel.HighRisk,
                code = "unknown_tool",
                message = "未知工具。",
            )

        val disabled = !toolSettingsProvider().runtimeSettingFor(toolDescriptor).enabled
        if (disabled) {
            return resultWriter.saveFailure(
                conversationId = conversationId,
                toolCall = toolCall,
                toolName = toolDescriptor.name,
                permissionLevel = toolDescriptor.permissionLevel,
                code = "tool_disabled",
                message = "工具已禁用。",
                sensitiveInputFields = toolDescriptor.sensitiveInputFields,
            )
        }

        val preflightFailure = toolDescriptor.preflightFailure()
        if (preflightFailure != null) {
            return resultWriter.saveFailure(
                conversationId = conversationId,
                toolCall = toolCall,
                toolName = toolDescriptor.name,
                permissionLevel = toolDescriptor.permissionLevel,
                code = preflightFailure.code,
                message = preflightFailure.message,
                sensitiveInputFields = toolDescriptor.sensitiveInputFields,
            )
        }

        val startedAt = clock.instant()
        return runCatching {
            runTool(conversationId, toolCall, toolDescriptor)
        }.fold(
            onSuccess = { executed ->
                resultWriter.saveSuccess(
                    conversationId = conversationId,
                    toolCall = toolCall,
                    descriptor = toolDescriptor,
                    startedAt = startedAt,
                    executed = executed,
                )
            },
            onFailure = { error ->
                if (error is CancellationException) {
                    val execution = withContext(NonCancellable) {
                        resultWriter.saveFailure(
                            conversationId = conversationId,
                            toolCall = toolCall,
                            toolName = toolDescriptor.name,
                            permissionLevel = toolDescriptor.permissionLevel,
                            code = "tool_cancelled",
                            message = "工具执行已取消。",
                            startedAt = startedAt,
                            status = ToolStatus.Cancelled,
                            sensitiveInputFields = toolDescriptor.sensitiveInputFields,
                        )
                    }
                    throw ToolExecutionCancelledException(execution, error)
                }
                resultWriter.saveFailure(
                    conversationId = conversationId,
                    toolCall = toolCall,
                    toolName = toolDescriptor.name,
                    permissionLevel = toolDescriptor.permissionLevel,
                    code = error.toToolErrorCode(),
                    message = error.message ?: "工具执行失败。",
                    startedAt = startedAt,
                    cause = error,
                    sensitiveInputFields = toolDescriptor.sensitiveInputFields,
                )
            },
        )
    }

    suspend fun deny(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution =
        resultWriter.saveFailure(
            conversationId = conversationId,
            toolCall = toolCall,
            toolName = descriptor?.name ?: toolCall.name.canonicalToolName(),
            permissionLevel = descriptor?.permissionLevel ?: ToolPermissionLevel.HighRisk,
            code = "tool_denied",
            message = "用户拒绝执行工具。",
            status = ToolStatus.Denied,
            sensitiveInputFields = descriptor?.sensitiveInputFields ?: emptySet(),
        )

    suspend fun cancel(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor?,
    ): ToolExecution =
        resultWriter.saveFailure(
            conversationId = conversationId,
            toolCall = toolCall,
            toolName = descriptor?.name ?: toolCall.name.canonicalToolName(),
            permissionLevel = descriptor?.permissionLevel ?: ToolPermissionLevel.HighRisk,
            code = "tool_cancelled",
            message = "工具执行已取消。",
            status = ToolStatus.Cancelled,
            sensitiveInputFields = descriptor?.sensitiveInputFields ?: emptySet(),
        )

    private suspend fun runTool(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor,
    ): ExecutedToolOutput =
        when {
            localToolRunner.canRun(descriptor.name) -> localToolRunner.run(conversationId, toolCall)
            descriptor.name == "web_search" || descriptor.name == "code_sandbox" ->
                gatewayToolRunner.run(descriptor.name, toolCall.arguments)
            descriptor.name == "image_generation" -> imageGenerationToolRunner.run(conversationId, toolCall.arguments)
            else -> error("工具尚未实现：${toolCall.name}")
        }

    private fun ToolDescriptor.preflightFailure(): ToolPreflightFailure? =
        when {
            source == ToolSource.Official -> ToolPreflightFailure(
                code = "hosted_tool_not_executable_locally",
                message = "官方 Hosted Tool 由 Provider 执行，本地不执行。",
            )
            name == "image_upload_to_model" -> ToolPreflightFailure(
                code = "image_upload_requires_chat_confirmation",
                message = "图片发送给模型必须通过聊天输入栏选择图片，并在发送前二次确认；工具不能自动读取或上传本地图片。",
            )
            else -> null
        }

}

private data class ToolPreflightFailure(
    val code: String,
    val message: String,
)
