package com.aichat.workbench.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_items",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_conversation_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["kind"]),
        Index(value = ["source_conversation_id"]),
        Index(value = ["updated_at"]),
    ],
)
data class MemoryItemEntity(
    @PrimaryKey
    val id: String,
    val kind: String,
    val content: String,
    @ColumnInfo(name = "source_conversation_id")
    val sourceConversationId: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
