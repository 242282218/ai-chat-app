package com.aichat.workbench.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tool_invocations",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversation_id", "started_at"]),
        Index(value = ["status"]),
    ],
)
data class ToolInvocationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String?,
    @ColumnInfo(name = "tool_name")
    val toolName: String,
    @ColumnInfo(name = "permission_level")
    val permissionLevel: String,
    @ColumnInfo(name = "input_summary")
    val inputSummary: String,
    @ColumnInfo(name = "output_json")
    val outputJson: String,
    val status: String,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "finished_at")
    val finishedAt: Long?,
    @ColumnInfo(name = "error_json")
    val errorJson: String?,
    @ColumnInfo(name = "raw_input_json")
    val rawInputJson: String?,
    @ColumnInfo(name = "raw_output_json")
    val rawOutputJson: String?,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long?,
    @ColumnInfo(name = "canceled_at")
    val canceledAt: Long?,
)
