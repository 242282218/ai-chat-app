package com.aichat.workbench.feature.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ToolsHubScreen(modifier: Modifier = Modifier) {
    ToolsScreen(
        onBack = {},
        showBackButton = false,
        modifier = modifier,
    )
}
