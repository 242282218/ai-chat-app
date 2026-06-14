package com.aichat.workbench.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.ui.markdown.MarkdownMessageContent

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    highlightQuery: String = "",
) {
    val isUser = message.role == MessageRole.User
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            AssistantAvatar()
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            shape = if (isUser) {
                MaterialTheme.shapes.extraLarge
            } else {
                MaterialTheme.shapes.large
            },
            tonalElevation = 0.dp,
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                MarkdownMessageContent(text = message.content.ifBlank { "..." })
            }
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            UserAvatar()
        }
    }
}
