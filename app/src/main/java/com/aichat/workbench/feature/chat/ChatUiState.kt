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
    val filteredMessages: List<Message> = emptyList(),
    val searchMatchCount: Int = 0,
    val matchingMessageIds: List<MessageId> = emptyList(),
) {
    val input: String get() = draft.input
    val titleDraft: String get() = draft.title
    val editingMessageId: MessageId? get() = draft.editingMessageId

    fun withSearchApplied(): ChatUiState {
        val filtered = if (searchQuery.isBlank()) messages
        else messages.filter { it.content.contains(searchQuery, ignoreCase = true) }
        return copy(
            filteredMessages = filtered,
            searchMatchCount = if (searchQuery.isBlank()) 0 else filtered.size,
            matchingMessageIds = filtered.map { it.id },
        )
    }
}
