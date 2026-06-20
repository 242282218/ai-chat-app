package com.aichat.workbench.stream.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.feature.chat.ChatScreen
import com.aichat.workbench.stream.theme.AiChatStreamTheme

/**
 * Local-first Stream migration experiment.
 *
 * This screen deliberately reuses the existing Room + provider-backed chat flow.
 * Stream SDK UI components are not used here because they require connected users
 * and channel queries against Stream Cloud, which conflicts with this app's
 * local-first storage model.
 */
@Composable
fun StreamChatScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    initialConversationId: ConversationId? = null,
    initialDraft: String = "",
    modifier: Modifier = Modifier,
) {
    AiChatStreamTheme {
        ChatScreen(
            onBack = onBack,
            onOpenProviders = onOpenProviders,
            initialConversationId = initialConversationId,
            initialDraft = initialDraft,
            modifier = modifier,
        )
    }
}
