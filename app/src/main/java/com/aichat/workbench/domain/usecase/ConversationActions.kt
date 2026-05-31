package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.repository.ConversationRepository

class RenameConversationUseCase(
    private val repository: ConversationRepository,
) {
    suspend operator fun invoke(id: ConversationId, title: String) {
        require(title.isNotBlank()) { "Conversation title must not be blank." }
        repository.renameConversation(id, title)
    }
}

class ArchiveConversationUseCase(
    private val repository: ConversationRepository,
) {
    suspend operator fun invoke(id: ConversationId) {
        repository.archiveConversation(id)
    }
}

class DeleteConversationUseCase(
    private val repository: ConversationRepository,
) {
    suspend operator fun invoke(id: ConversationId) {
        repository.deleteConversation(id)
    }
}
