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
import org.json.JSONObject

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
        )

    private fun ToolResult.toEntity(conversationId: ConversationId?): ToolInvocationEntity =
        ToolInvocationEntity(
            id = id.value,
            conversationId = conversationId?.value,
            toolName = toolName,
            permissionLevel = permissionLevel.name,
            inputSummary = inputSummary,
            outputJson = output.toJson(),
            status = status.name,
            startedAt = startedAt.toEpochMilli(),
            finishedAt = finishedAt?.toEpochMilli(),
            errorJson = error?.toJson(),
        )

    private fun ToolOutput.toJson(): String =
        when (this) {
            is ToolOutput.Text -> JSONObject()
                .put("type", "text")
                .put("text", text)
                .toString()
            is ToolOutput.Json -> JSONObject()
                .put("type", "json")
                .put("value", value)
                .toString()
        }

    private fun String.toToolOutput(): ToolOutput {
        val json = JSONObject(this)
        return when (json.optString("type")) {
            "text" -> ToolOutput.Text(json.optString("text"))
            "json" -> ToolOutput.Json(json.optString("value"))
            else -> ToolOutput.Text(this)
        }
    }

    private fun ToolError.toJson(): String =
        JSONObject()
            .put("code", code)
            .put("message", message)
            .toString()

    private fun String.toToolError(): ToolError {
        val json = JSONObject(this)
        return ToolError(
            code = json.optString("code"),
            message = json.optString("message"),
        )
    }
}
