package com.aichat.workbench.data.repository

import com.aichat.workbench.data.local.dao.ToolInvocationDao
import com.aichat.workbench.data.local.entity.ToolInvocationEntity
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolError
import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.domain.model.ToolResult
import com.aichat.workbench.domain.model.ToolStatus
import com.aichat.workbench.domain.repository.ToolInvocationRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomToolInvocationRepository(
    private val dao: ToolInvocationDao,
) : ToolInvocationRepository {
    override fun observeToolInvocations(): Flow<List<ToolResult>> =
        dao.observeToolInvocations().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun saveToolResult(conversationId: ConversationId?, toolResult: ToolResult) {
        dao.upsertToolInvocation(toolResult.toEntity(conversationId))
    }

    private fun ToolInvocationEntity.toDomain(): ToolResult =
        ToolResult(
            id = ToolCallId(id),
            toolName = toolName,
            permissionLevel = ToolPermissionLevel.valueOf(permissionLevel),
            inputSummary = inputSummary,
            output = outputJson.toToolOutput(),
            status = ToolStatus.valueOf(status),
            startedAt = Instant.ofEpochMilli(startedAt),
            finishedAt = finishedAt?.let(Instant::ofEpochMilli),
            error = errorJson?.toToolError(),
            conversationId = conversationId?.let(::ConversationId),
            rawInputJson = rawInputJson,
            rawOutputJson = rawOutputJson,
            durationMs = durationMs,
            canceledAt = canceledAt?.let(Instant::ofEpochMilli),
        )

    private fun ToolResult.toEntity(conversationId: ConversationId?): ToolInvocationEntity =
        ToolInvocationEntity(
            id = id.value,
            conversationId = conversationId?.value ?: this.conversationId?.value,
            toolName = toolName,
            permissionLevel = permissionLevel.name,
            inputSummary = inputSummary,
            outputJson = output.toJson(),
            status = status.name,
            startedAt = startedAt.toEpochMilli(),
            finishedAt = finishedAt?.toEpochMilli(),
            errorJson = error?.toJson(),
            rawInputJson = rawInputJson,
            rawOutputJson = rawOutputJson,
            durationMs = durationMs,
            canceledAt = canceledAt?.toEpochMilli(),
        )

    private fun ToolOutput.toJson(): String =
        when (this) {
            is ToolOutput.Text -> repositoryJson.encodeToString(ToolOutputJson(type = "text", text = text))
            is ToolOutput.Json -> repositoryJson.encodeToString(ToolOutputJson(type = "json", value = value))
        }

    private fun String.toToolOutput(): ToolOutput {
        val json = runCatching { repositoryJson.decodeFromString<ToolOutputJson>(this) }.getOrNull()
            ?: return ToolOutput.Text(this)
        return when (json.type) {
            "text" -> ToolOutput.Text(json.text.orEmpty())
            "json" -> ToolOutput.Json(json.value.orEmpty())
            else -> ToolOutput.Text(this)
        }
    }

    private fun ToolError.toJson(): String =
        repositoryJson.encodeToString(
            ToolErrorJson(
                code = code,
                message = message,
                statusCode = statusCode,
                retryable = retryable,
            ),
        )

    private fun String.toToolError(): ToolError {
        val json = repositoryJson.decodeFromString<ToolErrorJson>(this)
        return ToolError(
            code = json.code,
            message = json.message,
            statusCode = json.statusCode,
            retryable = json.retryable,
        )
    }

    private companion object {
        val repositoryJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

@Serializable
private data class ToolOutputJson(
    val type: String,
    val text: String? = null,
    val value: String? = null,
)

@Serializable
private data class ToolErrorJson(
    val code: String,
    val message: String,
    val statusCode: Int? = null,
    val retryable: Boolean? = null,
)
