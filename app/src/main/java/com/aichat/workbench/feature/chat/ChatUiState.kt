package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.PromptPreset
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolPermissionLevel

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val selectedConversationId: ConversationId? = null,
    val messages: List<Message> = emptyList(),
    val selectedConversationMessageCount: Int = 0,
    val providers: List<ProviderConfig> = emptyList(),
    val modelRolePreferences: List<ModelRolePreference> = emptyList(),
    val promptPresets: List<PromptPreset> = emptyList(),
    val selectedProviderId: String? = null,
    val draft: DraftState = DraftState(),
    val settingsExpanded: Boolean = false,
    val promptsExpanded: Boolean = false,
    val imageDrafts: List<MessagePart.Image> = emptyList(),
    val isGenerating: Boolean = false,
    val pendingToolCall: PendingToolCall? = null,
    val error: String? = null,
) {
    val input: String get() = draft.input
    val titleDraft: String get() = draft.title
    val systemPromptDraft: String get() = draft.systemPrompt
    val modelDraft: String get() = draft.model
    val temperatureDraft: String get() = draft.temperature
    val topPDraft: String get() = draft.topP
    val maxTokensDraft: String get() = draft.maxTokens
    val temporaryDraft: Boolean get() = draft.temporary
    val sensitiveDraft: Boolean get() = draft.sensitive
    val editingMessageId: MessageId? get() = draft.editingMessageId
}

data class PendingToolCall(
    val toolCall: ToolCall,
    val displayName: String,
    val permissionLevel: ToolPermissionLevel,
)
