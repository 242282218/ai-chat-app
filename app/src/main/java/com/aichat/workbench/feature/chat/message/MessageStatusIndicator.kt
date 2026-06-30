package com.aichat.workbench.feature.chat.message

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.ui.theme.WorkbenchSpacing
import com.aichat.workbench.ui.util.isReduceMotionEnabled

/**
 * Status indicator for messages (streaming, failed, etc.)
 * Shows visual feedback for non-completed message states.
 */
@Composable
fun MessageStatusIndicator(
    status: MessageStatus,
    errorSummary: String? = null,
    modifier: Modifier = Modifier
) {
    Spacer(modifier = Modifier.height(WorkbenchSpacing.xs))

    when (status) {
        MessageStatus.Streaming -> {
            StreamingIndicator(modifier = modifier)
        }
        MessageStatus.Failed -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(WorkbenchSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "失败",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = errorSummary ?: "生成失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        MessageStatus.Completed -> {
            // Optional: show checkmark for completed messages
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "已完成",
                modifier = modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
        MessageStatus.Pending -> {
            Text(
                text = "等待中...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = modifier
            )
        }
        MessageStatus.Cancelled -> {
            Text(
                text = "已取消",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = modifier
            )
        }
        else -> {
            // No indicator for Draft, Compressed
        }
    }
}

/**
 * Animated dots indicator for streaming messages
 */
@Composable
private fun StreamingIndicator(modifier: Modifier = Modifier) {
    if (isReduceMotionEnabled()) {
        Text(
            text = "生成中...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = modifier
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "streaming")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 600,
                        easing = LinearEasing,
                        delayMillis = index * 200
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )

            Text(
                text = "•",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(alpha)
            )
        }
    }
}
