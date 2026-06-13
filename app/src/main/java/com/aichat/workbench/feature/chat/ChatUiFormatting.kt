package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.provider.preferredChatModel


internal fun chatSubtitle(state: ChatUiState): String {
    val selectedProvider = selectedChatProvider(state)
    val model = selectedProvider?.preferredChatModel(state.modelRolePreferences).orEmpty()
    val providerText = selectedProvider?.let {
        if (model.isBlank()) it.name else "${it.name} / $model"
    } ?: "需要模型连接"
    val stateText = when {
        state.isGenerating -> "生成中"
        else -> null
    }
    return listOfNotNull(stateText, providerText).joinToString(" · ")
}

internal fun ChatUiState.shouldShowConversationMetadata(): Boolean =
    selectedConversationMessageCount > 0

internal fun selectedChatProvider(state: ChatUiState) =
    state.selectedProviderId
        ?.let { id -> state.providers.firstOrNull { it.id.value == id } }
        ?: state.providers.firstOrNull { it.enabled }

internal fun MessageRole.displayLabel(): String =
    when (this) {
        MessageRole.System -> "系统"
        MessageRole.User -> "用户"
        MessageRole.Assistant -> "助手"
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
