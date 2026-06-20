package com.aichat.workbench.stream.theme

import androidx.compose.runtime.Composable

/**
 * Local-first theme boundary for the Stream migration experiment.
 *
 * The experiment currently reuses the existing app chat UI and keeps this wrapper
 * as the safe integration point for future visual changes.
 */
@Composable
fun AiChatStreamTheme(
    content: @Composable () -> Unit,
) {
    content()
}
