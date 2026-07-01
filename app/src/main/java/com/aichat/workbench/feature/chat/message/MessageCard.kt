package com.aichat.workbench.feature.chat.message

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.ui.util.isReduceMotionEnabled

/**
 * Modern message card with Material 3 design and fluid interactions.
 *
 * Features:
 * - Long press to show action menu
 * - Double tap to quick copy
 * - Haptic feedback
 * - Status indicator for streaming/failed messages
 * - iOS-style press animation
 */
@Composable
fun MessageCard(
    message: Message,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val reduceMotion = isReduceMotionEnabled()
    val maxWidth = when (message.role) {
        MessageRole.User -> 312.dp
        MessageRole.Assistant -> 640.dp
        MessageRole.System -> 520.dp
    }

    val scale by animateFloatAsState(
        targetValue = if (!reduceMotion && isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessHigh,
        ),
        label = "press_scale",
    )

    Card(
        modifier = modifier
            .widthIn(min = 72.dp, max = maxWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(message.id) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showBottomSheet = true
                    },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val copyableText = message.copyableText()
                        if (copyableText.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(copyableText))
                            onAction(MessageAction.Copy(message))
                        } else {
                            showBottomSheet = true
                        }
                    },
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = messageContainerColor(message),
            contentColor = messageContentColor(message),
        ),
        shape = when (message.role) {
            MessageRole.User -> MaterialTheme.shapes.extraLarge
            else -> MaterialTheme.shapes.large
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp,
        ),
        border = messageBorder(message),
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Column(
                modifier = Modifier.padding(end = 44.dp),
            ) {
                MessageContent(
                    message = message,
                    searchQuery = searchQuery,
                )

                if (message.status != MessageStatus.Completed) {
                    MessageStatusIndicator(
                        status = message.status,
                        errorSummary = message.errorSummary,
                    )
                }
            }
            IconButton(
                onClick = { showBottomSheet = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "消息操作",
                    modifier = Modifier.size(18.dp),
                    tint = messageActionTint(message),
                )
            }
        }
    }

    if (showBottomSheet) {
        MessageActionsSheet(
            message = message,
            onAction = { action ->
                onAction(action)
                showBottomSheet = false
            },
            onDismiss = { showBottomSheet = false },
        )
    }
}

@Composable
private fun messageContainerColor(message: Message) = when (message.role) {
    MessageRole.User -> MaterialTheme.colorScheme.primaryContainer
    MessageRole.Assistant -> MaterialTheme.colorScheme.surfaceContainerLow
    MessageRole.System -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
}

@Composable
private fun messageContentColor(message: Message) = when (message.role) {
    MessageRole.User -> MaterialTheme.colorScheme.onPrimaryContainer
    MessageRole.Assistant -> MaterialTheme.colorScheme.onSurface
    MessageRole.System -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun messageBorder(message: Message): BorderStroke = when (message.role) {
    MessageRole.User -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
    MessageRole.Assistant -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f))
    MessageRole.System -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
private fun messageActionTint(message: Message) = when (message.role) {
    MessageRole.User -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.58f)
    MessageRole.Assistant -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
    MessageRole.System -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
}
