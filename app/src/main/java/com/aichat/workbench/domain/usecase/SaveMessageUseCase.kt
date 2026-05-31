package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.Message
import com.aichat.workbench.domain.repository.ConversationRepository

class SaveMessageUseCase(
    private val repository: ConversationRepository,
) {
    suspend operator fun invoke(message: Message) {
        repository.saveMessage(message)
    }
}
