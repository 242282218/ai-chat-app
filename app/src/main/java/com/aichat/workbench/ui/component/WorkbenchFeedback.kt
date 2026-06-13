package com.aichat.workbench.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
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
    val noticeModifier = modifier
        .fillMaxWidth()
        .semantics {
            if (tone == StatusTone.Critical) {
                liveRegion = LiveRegionMode.Polite
                error(text)
            }
        }
    Surface(
        modifier = noticeModifier,
        color = colors.container,
        contentColor = colors.content,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(0.5.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            action()
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.Neutral,
) {
    val colors = statusColors(tone)
    Text(
        text = text,
        modifier = modifier
            .border(0.5.dp, colors.border, MaterialTheme.shapes.small)
            .background(colors.container, MaterialTheme.shapes.small)
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
    Box(
        modifier = modifier
            .size(36.dp)
            .background(colors.container, MaterialTheme.shapes.small),
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
