package com.aichat.workbench.stream.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.theme.AiChatTheme

@Preview(showBackground = true)
@Composable
fun PreviewStreamThemeLight() {
    AiChatTheme(darkTheme = false) {
        AiChatStreamTheme {
            StreamThemePreviewContent()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewStreamThemeDark() {
    AiChatTheme(darkTheme = true) {
        AiChatStreamTheme {
            StreamThemePreviewContent()
        }
    }
}

@Composable
private fun StreamThemePreviewContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("本地优先实验主题", color = MaterialTheme.colorScheme.onBackground)
        Text("复用现有聊天链路", color = MaterialTheme.colorScheme.primary)
        Text("默认关闭 Stream 实验入口", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
