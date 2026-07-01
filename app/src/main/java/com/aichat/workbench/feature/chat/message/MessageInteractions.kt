package com.aichat.workbench.feature.chat.message

import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart

/**
 * Actions that can be performed on a message
 */
sealed interface MessageAction {
    data class Copy(val message: Message) : MessageAction
    data class Edit(val messageId: MessageId) : MessageAction
    data class Retry(val messageId: MessageId) : MessageAction
    data class Delete(val messageId: MessageId) : MessageAction
    data class Share(val message: Message) : MessageAction
}

/**
 * Build available actions for a message based on its role and status
 */
fun buildMessageActions(message: Message): List<MessageActionItem> {
    val actions = mutableListOf<MessageActionItem>()
    val copyableText = message.copyableText()

    if (copyableText.isNotBlank()) {
        actions.add(
            MessageActionItem(
                action = MessageAction.Copy(message),
                label = "复制",
                icon = "content_copy"
            )
        )
    }

    // Edit is only available for user messages
    if (message.role == com.aichat.workbench.domain.model.MessageRole.User) {
        actions.add(
            MessageActionItem(
                action = MessageAction.Edit(message.id),
                label = "编辑",
                icon = "edit"
            )
        )
    }

    // Retry is only available for failed assistant messages
    if (message.role == com.aichat.workbench.domain.model.MessageRole.Assistant &&
        message.status == com.aichat.workbench.domain.model.MessageStatus.Failed
    ) {
        actions.add(
            MessageActionItem(
                action = MessageAction.Retry(message.id),
                label = "重新生成",
                icon = "refresh"
            )
        )
    }

    if (copyableText.isNotBlank()) {
        actions.add(
            MessageActionItem(
                action = MessageAction.Share(message),
                label = "分享",
                icon = "share"
            )
        )
    }

    // Delete is always available
    actions.add(
        MessageActionItem(
            action = MessageAction.Delete(message.id),
            label = "删除",
            icon = "delete",
            isDestructive = true
        )
    )

    return actions
}

/**
 * Represents a single action item in the message action menu
 */
data class MessageActionItem(
    val action: MessageAction,
    val label: String,
    val icon: String,
    val isDestructive: Boolean = false
)

internal fun Message.copyableText(): String =
    content.takeIf { it.isNotBlank() }
        ?: contentParts
            .filterIsInstance<MessagePart.Text>()
            .joinToString("\n") { it.text }
            .trim()
