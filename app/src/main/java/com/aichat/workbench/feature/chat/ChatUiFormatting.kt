package com.aichat.workbench.feature.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.provider.preferredModel
import com.aichat.workbench.ui.component.StatusTone

internal fun Message.shouldShowHeader(): Boolean =
    role == MessageRole.Tool ||
        role == MessageRole.System ||
        status != MessageStatus.Completed

@Composable
internal fun StatusTone.contentColor() =
    when (this) {
        StatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.Accent -> MaterialTheme.colorScheme.primary
        StatusTone.Success -> MaterialTheme.colorScheme.secondary
        StatusTone.Warning -> MaterialTheme.colorScheme.tertiary
        StatusTone.Critical -> MaterialTheme.colorScheme.error
    }

internal fun chatSubtitle(
    state: ChatUiState,
    selectedConversation: Conversation?,
): String {
    val selectedProvider = selectedChatProvider(state)
    val model = state.modelDraft.ifBlank { selectedProvider?.preferredModel().orEmpty() }
    val providerText = selectedProvider?.let {
        if (model.isBlank()) it.name else "${it.name} / $model"
    } ?: "需要模型连接"
    val stateText = when {
        state.isGenerating -> "生成中"
        selectedConversation?.isTemporary == true || state.temporaryDraft -> "临时会话"
        selectedConversation?.isSensitive == true || state.sensitiveDraft -> "敏感会话"
        else -> null
    }
    return listOfNotNull(stateText, providerText).joinToString(" · ")
}

internal fun ChatUiState.shouldShowConversationMetadata(conversation: Conversation): Boolean =
    selectedConversationMessageCount > 0 || conversation.isTemporary || conversation.isSensitive

internal fun Conversation.chipStatusText(): String? =
    when {
        isSensitive && isTemporary -> "敏感 · 临时"
        isSensitive -> "敏感"
        isTemporary -> "临时"
        else -> null
    }

internal fun selectedChatProvider(state: ChatUiState) =
    state.selectedProviderId
        ?.let { id -> state.providers.firstOrNull { it.id.value == id } }
        ?: state.providers.firstOrNull { it.enabled }

internal fun MessageRole.displayLabel(): String =
    when (this) {
        MessageRole.System -> "系统"
        MessageRole.User -> "用户"
        MessageRole.Assistant -> "助手"
        MessageRole.Tool -> "工具"
    }

internal fun MessageStatus.displayLabel(): String =
    when (this) {
        MessageStatus.Draft -> "草稿"
        MessageStatus.Pending -> "等待中"
        MessageStatus.Streaming -> "生成中"
        MessageStatus.Completed -> "完成"
        MessageStatus.Compressed -> "已压缩"
        MessageStatus.Failed -> "失败"
        MessageStatus.Cancelled -> "已取消"
    }

internal fun canSubmitMessage(
    input: String,
    canSend: Boolean,
    imageDrafts: List<MessagePart.Image>,
): Boolean =
    canSend && (input.trim().isNotEmpty() || imageDrafts.isNotEmpty())

internal fun shouldConfirmImageSend(imageDrafts: List<MessagePart.Image>): Boolean =
    imageDrafts.isNotEmpty()

internal fun Context.persistReadPermission(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

internal fun ModelParameterDraftStatus.tone(): StatusTone =
    if (isValid) StatusTone.Neutral else StatusTone.Critical

internal const val MAX_INLINE_SEARCH_CITATIONS = 5
