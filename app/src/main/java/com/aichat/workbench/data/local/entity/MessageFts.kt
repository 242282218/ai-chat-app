package com.aichat.workbench.data.local.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "messages_fts")
@Fts4(contentEntity = MessageEntity::class)
data class MessageFts(
    val content: String,
)
