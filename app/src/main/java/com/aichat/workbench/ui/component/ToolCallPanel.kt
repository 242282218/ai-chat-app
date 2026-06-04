package com.aichat.workbench.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.ui.theme.Accent
import com.aichat.workbench.ui.theme.AccentVariant
import com.aichat.workbench.ui.theme.Neutral100
import com.aichat.workbench.ui.theme.Neutral300
import com.aichat.workbench.ui.theme.SemanticError
import com.aichat.workbench.ui.theme.SemanticSuccess

@Composable
fun ToolCallPanel(
    toolCall: ToolCall,
    result: String?,
    isError: Boolean,
    isPending: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = toolVisualInfo(toolCall.name)
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(0.5.dp, Neutral300, MaterialTheme.shapes.medium)
            .background(Neutral100)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(info.icon, contentDescription = null, tint = info.color, modifier = Modifier.size(16.dp))
            Text(info.label, style = MaterialTheme.typography.labelMedium, color = info.color)
            Spacer(Modifier.weight(1f))
            val (pillText, tone) = when {
                isPending -> "等待确认" to StatusTone.Warning
                isError -> "失败" to StatusTone.Critical
                result != null -> "完成" to StatusTone.Success
                else -> "运行中" to StatusTone.Neutral
            }
            StatusPill(pillText, tone = tone)
        }
        Text(
            text = toolCall.arguments.abbreviate(140),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            result != null -> Text(
                text = result.abbreviate(240),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) SemanticError else SemanticSuccess,
            )
            isPending -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDeny, modifier = Modifier.weight(1f)) {
                    Text("拒绝")
                }
                Button(onClick = onApprove, modifier = Modifier.weight(1f)) {
                    Text("允许")
                }
            }
        }
    }
}

private data class ToolVisualInfo(
    val icon: ImageVector,
    val label: String,
    val color: Color,
)

private fun toolVisualInfo(name: String): ToolVisualInfo =
    when (name) {
        "web_search" -> ToolVisualInfo(Icons.Outlined.Search, "联网搜索", Accent)
        "code_sandbox" -> ToolVisualInfo(Icons.Outlined.Code, "代码执行", AccentVariant)
        else -> ToolVisualInfo(Icons.Outlined.Build, name, Accent)
    }

private fun String.abbreviate(maxLength: Int): String =
    if (length > maxLength) "${take(maxLength)}..." else this
