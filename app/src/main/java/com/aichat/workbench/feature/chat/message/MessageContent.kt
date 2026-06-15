package com.aichat.workbench.feature.chat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.feature.chat.ChatImagePreview
import com.aichat.workbench.ui.markdown.MarkdownMessageContent

/**
 * Renders the content of a message with Markdown support.
 * This is a wrapper around the existing MarkdownMessageContent.
 */
@Composable
fun MessageContent(
    message: Message,
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val images = message.contentParts.filterIsInstance<MessagePart.Image>()

    if (images.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(
                items = images,
                key = { image -> image.uri.take(48) },
            ) { image ->
                ChatImagePreview(
                    image = image,
                    modifier = Modifier.size(96.dp),
                )
            }
        }
    }

    if (message.content.isNotBlank()) {
        MarkdownMessageContent(
            text = message.content,
            modifier = modifier,
            highlightQuery = searchQuery
        )
    } else if (message.contentParts.isNotEmpty()) {
        val textPart = message.contentParts.firstOrNull {
            it is MessagePart.Text
        }
        if (textPart is MessagePart.Text) {
            Text(
                text = textPart.text,
                modifier = modifier,
                maxLines = 100,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
