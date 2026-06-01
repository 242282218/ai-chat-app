package com.aichat.workbench.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class MessageSearchEntity(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "conversation_id",
        entityColumn = "id",
    )
    val conversation: ConversationEntity,
)
