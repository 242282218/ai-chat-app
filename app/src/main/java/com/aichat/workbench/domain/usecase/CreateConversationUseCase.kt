package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ModelParameters
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
        defaultModel: String? = null,
        modelParameters: ModelParameters = ModelParameters(),
        systemPrompt: String? = null,
        isTemporary: Boolean = false,
        isSensitive: Boolean = false,
    ): Conversation {
        val now = clock.instant()
        val conversation = Conversation(
            id = ConversationId(UUID.randomUUID().toString()),
            title = title.ifBlank { "新对话" },
            createdAt = now,
            updatedAt = now,
            defaultProviderId = defaultProviderId,
            defaultModel = defaultModel,
            modelParameters = modelParameters,
            systemPrompt = systemPrompt,
            isTemporary = isTemporary,
            isSensitive = isSensitive,
            archivedAt = null,
        )
        repository.saveConversation(conversation)
        return conversation
    }
}
