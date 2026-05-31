package com.aichat.workbench.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["updated_at"]),
        Index(value = ["archived_at"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "default_provider_id")
    val defaultProviderId: String?,
    @ColumnInfo(name = "default_model")
    val defaultModel: String?,
    @ColumnInfo(name = "model_parameters_json")
    val modelParametersJson: String,
    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String?,
    @ColumnInfo(name = "is_temporary")
    val isTemporary: Boolean,
    @ColumnInfo(name = "is_sensitive")
    val isSensitive: Boolean,
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long?,
)
