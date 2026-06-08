package com.aichat.workbench.tool.runtime

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import com.aichat.workbench.domain.tool.ToolExecution
import com.aichat.workbench.tool.model.SensitiveDataSanitizer
import com.aichat.workbench.tool.model.ToolDescriptor
import java.time.Clock
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

internal class ToolResultWriter(
    private val repository: ToolInvocationRepository,
    private val clock: Clock,
) {
    suspend fun saveSuccess(
        conversationId: ConversationId,
        toolCall: ToolCall,
        descriptor: ToolDescriptor,
        startedAt: Instant,
        executed: ExecutedToolOutput,
    ): ToolExecution {
        val output = executed.output
        val finishedAt = clock.instant()
        val result = ToolResult(
            id = toolCall.id,
            toolName = descriptor.name,
            permissionLevel = descriptor.permissionLevel,
            inputSummary = toolCall.arguments.toInputSummary(),
            output = output,
            status = ToolStatus.Completed,
            startedAt = startedAt,
            finishedAt = finishedAt,
            error = null,
            conversationId = conversationId,
            rawInputJson = SensitiveDataSanitizer.sanitize(toolCall.arguments, descriptor.sensitiveInputFields),
            rawOutputJson = output.rawJsonOrNull(),
            durationMs = startedAt.durationUntilMs(finishedAt),
        )
        repository.saveToolResult(conversationId, result)
        return ToolExecution(result, output.asModelContent(), executed.contentParts)
    }

    suspend fun saveFailure(
        conversationId: ConversationId,
        toolCall: ToolCall,
        toolName: String,
        permissionLevel: ToolPermissionLevel,
        code: String,
        message: String,
        startedAt: Instant = clock.instant(),
        status: ToolStatus = ToolStatus.Failed,
        cause: Throwable? = null,
        sensitiveInputFields: Set<String> = emptySet(),
    ): ToolExecution {
        val output = ToolOutput.Json(
            toolJson.encodeToString(
                ToolErrorOutput(
                    code = code,
                    message = message,
                    statusCode = cause.toolErrorStatusCode(),
                    retryable = cause.toolErrorRetryable(),
                ),
            ),
        )
        val finishedAt = clock.instant()
        val result = ToolResult(
            id = toolCall.id,
            toolName = toolName,
            permissionLevel = permissionLevel,
            inputSummary = toolCall.arguments.toInputSummary(),
            output = output,
            status = status,
            startedAt = startedAt,
            finishedAt = finishedAt,
            error = ToolError(
                code = code,
                message = message,
                statusCode = cause.toolErrorStatusCode(),
                retryable = cause.toolErrorRetryable(),
            ),
            conversationId = conversationId,
            rawInputJson = SensitiveDataSanitizer.sanitize(toolCall.arguments, sensitiveInputFields),
            rawOutputJson = output.value,
            durationMs = startedAt.durationUntilMs(finishedAt),
            canceledAt = finishedAt.takeIf { status == ToolStatus.Cancelled },
        )
        repository.saveToolResult(conversationId, result)
        return ToolExecution(result, output.asModelContent())
    }
}

@Serializable
private data class ToolErrorOutput(
    val code: String,
    val message: String,
    val statusCode: Int? = null,
    val retryable: Boolean? = null,
)
