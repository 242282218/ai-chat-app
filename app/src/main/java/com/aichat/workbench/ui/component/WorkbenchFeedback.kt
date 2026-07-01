package com.aichat.workbench.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun InlineNotice(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
    action: @Composable RowScope.() -> Unit = {},
) {
    val colors = statusColors(tone)
    val shape = MaterialTheme.shapes.medium
    val noticeModifier = modifier
        .fillMaxWidth()
        .background(colors.container, shape)
        .border(1.dp, colors.border, shape)
        .semantics {
            if (tone != StatusTone.Neutral) {
                liveRegion = LiveRegionMode.Polite
            }
            if (tone == StatusTone.Critical) {
                error(text)
            }
        }
    Row(
        modifier = noticeModifier.padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.content,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        action()
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
) {
    val colors = statusColors(tone)
    val shape = MaterialTheme.shapes.small
    Text(
        text = text,
        modifier = modifier
            .widthIn(max = 240.dp)
            .background(colors.container, shape)
            .border(1.dp, colors.border, shape)
            .semantics {
                contentDescription = text
            }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        color = colors.content,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (tone == StatusTone.Critical) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
) {
    val colors = statusColors(tone)
    val shape = MaterialTheme.shapes.small
    Box(
        modifier = modifier
            .size(36.dp)
            .background(colors.container, shape)
            .border(1.dp, colors.border, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.content,
            modifier = Modifier.size(18.dp),
        )
    }
}
