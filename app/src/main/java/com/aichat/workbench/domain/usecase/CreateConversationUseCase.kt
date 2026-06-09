package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ConversationRepository
import java.time.Clock
import java.util.UUID

class CreateConversationUseCase(
    private val repository: ConversationRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        title: String,
        defaultProviderId: ProviderId? = null,
    ): Conversation {
        val now = clock.instant()
        val conversation = Conversation(
            id = ConversationId(UUID.randomUUID().toString()),
            title = title.ifBlank { "新对话" },
            createdAt = now,
            updatedAt = now,
            defaultProviderId = defaultProviderId,
        )
        repository.saveConversation(conversation)
        return conversation
    }
}
