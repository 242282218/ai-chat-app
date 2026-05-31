package com.aichat.workbench.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "image_generations",
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
        Index(value = ["created_at"]),
    ],
)
data class ImageGenerationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "conversation_id")
    val conversationId: String?,
    val prompt: String,
    @ColumnInfo(name = "provider_id")
    val providerId: String?,
    val model: String?,
    val size: String?,
    val quality: String?,
    val count: Int,
    @ColumnInfo(name = "original_path")
    val originalPath: String?,
    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String?,
    val status: String,
    @ColumnInfo(name = "error_summary")
    val errorSummary: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
