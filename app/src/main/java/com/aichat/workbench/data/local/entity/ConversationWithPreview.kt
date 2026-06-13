package com.aichat.workbench.data.local.entity

import androidx.room.ColumnInfo

data class ConversationWithPreview(
    val id: String,
    val title: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "default_provider_id")
    val defaultProviderId: String?,
    @ColumnInfo(name = "last_message_content")
    val lastMessageContent: String?,
    @ColumnInfo(name = "last_message_role")
    val lastMessageRole: String?,
)
