package com.aichat.workbench.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["conversation_id", "created_at"]),
        Index(value = ["status"]),
    ],
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String,
    val role: String,
    val content: String,
    @ColumnInfo(name = "content_parts_json")
    val contentPartsJson: String,
    @ColumnInfo(name = "provider_id")
    val providerId: String?,
    val model: String?,
    val status: String,
    @ColumnInfo(name = "error_summary")
    val errorSummary: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "parent_message_id")
    val parentMessageId: String?,
)
