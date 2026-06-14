package com.aichat.workbench.feature.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.ui.component.AssistantAvatar
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.UserAvatar
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchIconButton
import com.aichat.workbench.ui.util.isReduceMotionEnabled
import com.aichat.workbench.ui.markdown.MarkdownMessageContent
import kotlinx.coroutines.delay

internal enum class CopyState { Ready, Copied, Failed }

@Composable
internal fun rememberCopyState(messageId: Any): MutableState<CopyState> {
    val state = remember(messageId) { mutableStateOf(CopyState.Ready) }
    LaunchedEffect(state.value) {
        if (state.value != CopyState.Ready) {
            delay(2000)
            state.value = CopyState.Ready
        }
    }
    return state
}

@Composable
@Suppress("DEPRECATION")
internal fun MessageBubble(
    message: Message,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit = {},
    highlightQuery: String = "",
) {
    val clipboardManager = LocalClipboardManager.current
    val copyState = rememberCopyState(message.id)
    val isUser = message.role == MessageRole.User

    val semanticsLabel = if (isUser) "你发送的消息" else "AI 回复的消息"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .semantics { contentDescription = semanticsLabel },
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            AssistantAvatar()
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = if (isUser) 300.dp else 600.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (message.status != MessageStatus.Completed && message.status != MessageStatus.Compressed) {
                Row(
                    modifier = Modifier.padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (message.role == MessageRole.System) {
                        StatusPill(
                            text = message.role.displayLabel(),
                            tone = message.roleTone(),
                        )
                    }
                    StatusPill(
                        text = message.status.displayLabel(),
                        tone = message.statusTone(),
                    )
                }
            }

            Surface(
                color = messageContainerColor(message),
                contentColor = messageContentColor(message),
                shape = if (isUser) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier.padding(message.contentPadding()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val images = message.contentParts.filterIsInstance<MessagePart.Image>()
                    if (images.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(
                                count = images.size,
                                key = { idx -> "msg-img-${images[idx].uri.take(32)}-$idx" },
                            ) { idx ->
                                ChatImagePreview(
                                    image = images[idx],
                                    modifier = Modifier.size(96.dp),
                                )
                            }
                        }
                    }

                    if (message.content.isBlank() && images.isEmpty() && message.errorSummary == null) {
                        TypingIndicator()
                    } else if (message.content.isNotBlank()) {
                        MarkdownMessageContent(text = message.content, highlightQuery = highlightQuery)
                    }

                    message.errorSummary?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            MessageActionRow(
                message = message,
                copyState = copyState.value,
                onCopy = {
                    try {
                        clipboardManager.setText(AnnotatedString(message.content))
                        copyState.value = CopyState.Copied
                    } catch (_: Exception) {
                        copyState.value = CopyState.Failed
                    }
                },
                onEdit = onEdit,
                onRetry = onRetry,
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            UserAvatar()
        }
    }
}

@Composable
private fun TypingIndicator() {
    val reduceMotion = isReduceMotionEnabled()
    if (reduceMotion) {
        TypingDots(alpha = 0.4f, animate = false)
    } else {
        TypingDots(alpha = 0f, animate = true)
    }
}

@Composable
private fun TypingDots(alpha: Float, animate: Boolean) {
    val transition = if (animate) rememberInfiniteTransition(label = "typing") else null
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .semantics { contentDescription = "AI 正在输入" },
    ) {
        repeat(3) { index ->
            val dotAlpha = transition?.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot_$index",
            )?.value ?: alpha
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = dotAlpha)),
            )
        }
    }
}

@Composable
internal fun MessageActionRow(
    message: Message,
    copyState: CopyState = CopyState.Ready,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkbenchIconButton(
            icon = when (copyState) { CopyState.Copied -> Icons.Filled.Check; CopyState.Failed -> Icons.Filled.Close; CopyState.Ready -> Icons.Filled.ContentCopy },
            label = when (copyState) { CopyState.Copied -> "已复制"; CopyState.Failed -> "复制失败"; CopyState.Ready -> "复制消息" },
            onClick = onCopy,
            tint = when (copyState) { CopyState.Copied -> MaterialTheme.colorScheme.primary; CopyState.Failed -> MaterialTheme.colorScheme.error; CopyState.Ready -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) },
        )
        if (message.role == MessageRole.User) {
            WorkbenchIconButton(
                icon = Icons.Filled.Edit,
                label = "编辑消息",
                onClick = onEdit,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
        }
        if (message.role == MessageRole.Assistant && (message.status == MessageStatus.Failed || message.status == MessageStatus.Cancelled)) {
            WorkbenchIconButton(
                icon = Icons.Filled.Replay,
                label = "重试回复",
                onClick = onRetry,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (message.role == MessageRole.Assistant && message.status == MessageStatus.Completed && !message.model.isNullOrBlank()) {
            Text(
                text = message.model,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun messageContainerColor(message: Message) =
    when (message.role) {
        MessageRole.User -> MaterialTheme.colorScheme.primary
        MessageRole.Assistant -> MaterialTheme.colorScheme.surfaceContainerLow
        MessageRole.System -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

@Composable
private fun messageContentColor(message: Message) =
    when (message.role) {
        MessageRole.User -> MaterialTheme.colorScheme.onPrimary
        MessageRole.Assistant -> MaterialTheme.colorScheme.onSurface
        MessageRole.System -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun Message.contentPadding(): androidx.compose.foundation.layout.PaddingValues =
    when (role) {
        MessageRole.Assistant -> androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        MessageRole.User -> androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        MessageRole.System -> androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    }

private fun Message.roleTone(): StatusTone =
    when (role) {
        MessageRole.User -> StatusTone.Accent
        MessageRole.Assistant -> StatusTone.Success
        MessageRole.System -> StatusTone.Neutral
    }

private fun Message.statusTone(): StatusTone =
    when (status) {
        MessageStatus.Completed -> StatusTone.Success
        MessageStatus.Failed -> StatusTone.Critical
        MessageStatus.Cancelled -> StatusTone.Warning
        MessageStatus.Streaming,
        MessageStatus.Pending,
        MessageStatus.Draft,
        MessageStatus.Compressed,
        -> StatusTone.Accent
    }
