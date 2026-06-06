package com.aichat.workbench.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

// Previews for key components
@Preview(name = "StatusPill - All Tones", showBackground = true)
@Composable
private fun StatusPillPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill(text = "Neutral", tone = StatusTone.Neutral)
            StatusPill(text = "Accent", tone = StatusTone.Accent)
            StatusPill(text = "Success", tone = StatusTone.Success)
            StatusPill(text = "Warning", tone = StatusTone.Warning)
            StatusPill(text = "Critical", tone = StatusTone.Critical)
        }
    }
}

@Preview(name = "InlineNotice - Critical", showBackground = true)
@Composable
private fun InlineNoticePreview() {
    MaterialTheme {
        InlineNotice(
            text = "API Key 缺失或无效，请重新配置。",
            icon = Icons.Filled.Warning,
            tone = StatusTone.Critical,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "WorkbenchIconButton", showBackground = true)
@Composable
private fun WorkbenchIconButtonPreview() {
    MaterialTheme {
        WorkbenchIconButton(
            icon = Icons.Filled.Settings,
            label = "设置",
            onClick = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
