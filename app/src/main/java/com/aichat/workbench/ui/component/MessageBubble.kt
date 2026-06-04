package com.aichat.workbench.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.ui.markdown.MarkdownMessageContent
import com.aichat.workbench.ui.theme.AccentContainer
import com.aichat.workbench.ui.theme.Neutral150

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    onGenerateDiff: ((com.aichat.workbench.ui.markdown.CodeArtifact) -> Unit)? = null,
) {
    val isUser = message.role == MessageRole.User
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isUser) {
            AssistantAvatar()
            Spacer(Modifier.width(8.dp))
        }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(if (isUser) AccentContainer else Neutral150)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            MarkdownMessageContent(
                text = message.content.ifBlank { "..." },
                onGenerateDiff = onGenerateDiff,
            )
        }
    }
}

@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "W",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
