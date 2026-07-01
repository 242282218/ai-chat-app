package com.aichat.workbench.feature.chat.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.ui.theme.WorkbenchSpacing

/**
 * Bottom sheet showing available actions for a message.
 * Shown when user long-presses a message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: Message,
    onAction: (MessageAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = WorkbenchSpacing.l),
        ) {
            MessagePreviewHeader(message)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = WorkbenchSpacing.s),
            )

            val actions = buildAvailableActions(message)
            actions.forEach { item ->
                ActionItem(
                    icon = item.icon,
                    label = item.label,
                    isDestructive = item.isDestructive,
                    onClick = { onAction(item.action) },
                )
            }
        }
    }
}

/**
 * Preview of the message content in the bottom sheet header
 */
@Composable
private fun MessagePreviewHeader(message: Message) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WorkbenchSpacing.l),
    ) {
        Text(
            text = when (message.role) {
                MessageRole.User -> "你的消息"
                MessageRole.Assistant -> "AI 回复"
                MessageRole.System -> "系统消息"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(WorkbenchSpacing.xs))

        Text(
            text = message.previewText(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Single action item in the sheet
 */
@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        modifier = Modifier
            .heightIn(min = 56.dp)
            .clickable(
                onClickLabel = label,
                role = Role.Button,
                onClick = onClick,
            ),
    )
}

/**
 * Build list of available actions based on message state
 */
private fun buildAvailableActions(message: Message): List<ActionItemData> {
    val actions = mutableListOf<ActionItemData>()
    val hasTextContent = message.copyableText().isNotBlank()

    if (hasTextContent) {
        actions.add(
            ActionItemData(
                action = MessageAction.Copy(message),
                icon = Icons.Filled.ContentCopy,
                label = "复制",
            )
        )
    }

    // Edit (only for user messages)
    if (message.role == MessageRole.User) {
        actions.add(
            ActionItemData(
                action = MessageAction.Edit(message.id),
                icon = Icons.Filled.Edit,
                label = "编辑",
            )
        )
    }

    // Retry failed or cancelled assistant messages.
    if (
        message.role == MessageRole.Assistant &&
        (message.status == MessageStatus.Failed || message.status == MessageStatus.Cancelled)
    ) {
        actions.add(
            ActionItemData(
                action = MessageAction.Retry(message.id),
                icon = Icons.Filled.Refresh,
                label = "重新生成",
            )
        )
    }

    if (hasTextContent) {
        actions.add(
            ActionItemData(
                action = MessageAction.Share(message),
                icon = Icons.Filled.Share,
                label = "分享",
            )
        )
    }

    // Delete (always available, destructive)
    actions.add(
        ActionItemData(
            action = MessageAction.Delete(message.id),
            icon = Icons.Filled.Delete,
            label = "删除",
            isDestructive = true,
        )
    )

    return actions
}

/**
 * Internal data class for action items
 */
private data class ActionItemData(
    val action: MessageAction,
    val icon: ImageVector,
    val label: String,
    val isDestructive: Boolean = false,
)

private fun Message.previewText(): String =
    copyableText().takeIf { it.isNotBlank() }
        ?: when {
            contentParts.any { it is MessagePart.Image } -> "图片内容"
            else -> "暂无文本内容"
        }
