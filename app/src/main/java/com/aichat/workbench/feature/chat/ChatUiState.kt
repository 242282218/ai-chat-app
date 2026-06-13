package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ModelRolePreference
import com.aichat.workbench.domain.model.ProviderConfig

data class ChatUiState(
    val conversations: List<Conversation> = emptyList(),
    val selectedConversationId: ConversationId? = null,
    val messages: List<Message> = emptyList(),
    val selectedConversationMessageCount: Int = 0,
    val providers: List<ProviderConfig> = emptyList(),
    val modelRolePreferences: List<ModelRolePreference> = emptyList(),
    val selectedProviderId: String? = null,
    val draft: DraftState = DraftState(),
    val imageDrafts: List<MessagePart.Image> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val currentMatchIndex: Int = 0,
) {
    val input: String get() = draft.input
    val titleDraft: String get() = draft.title
    val editingMessageId: MessageId? get() = draft.editingMessageId

    val filteredMessages: List<Message>
        get() = if (searchQuery.isBlank()) messages
        else messages.filter { it.content.contains(searchQuery, ignoreCase = true) }

    val searchMatchCount: Int
        get() = if (searchQuery.isBlank()) 0 else filteredMessages.size

    val matchingMessageIds: List<MessageId>
        get() = filteredMessages.map { it.id }
}
