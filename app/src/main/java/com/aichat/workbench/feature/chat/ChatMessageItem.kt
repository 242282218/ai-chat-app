package com.aichat.workbench.feature.chat

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.feature.chat.message.MessageAction
import com.aichat.workbench.feature.chat.message.MessageCard
import com.aichat.workbench.ui.markdown.MarkdownMessageContent

@Composable
internal fun MessageItem(
    message: Message,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit = {},
    highlightQuery: String = "",
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    if (message.status == MessageStatus.Compressed) {
        CompressedMessagesCard(message = message, highlightQuery = highlightQuery)
        return
    }

    MessageCard(
        message = message,
        onAction = { action ->
            when (action) {
                is MessageAction.Copy -> {
                    runCatching {
                        clipboardManager.setText(AnnotatedString(action.message.content))
                    }
                }
                is MessageAction.Edit -> onEdit()
                is MessageAction.Retry -> onRetry()
                is MessageAction.Delete -> onDelete()
                is MessageAction.Share -> {
                    val shareIntent = Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, action.message.content)
                        },
                        "分享消息",
                    )
                    context.startActivity(shareIntent)
                }
            }
        },
        searchQuery = highlightQuery,
        modifier = modifier,
    )
}

@Composable
internal fun CompressedMessagesCard(message: Message, highlightQuery: String = "") {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "上下文已压缩",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            MarkdownMessageContent(text = message.content, highlightQuery = highlightQuery)
        }
    }
}
