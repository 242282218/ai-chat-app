package com.aichat.workbench.domain.usecase

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.MemoryItem
import com.aichat.workbench.domain.model.MemoryItemId
import com.aichat.workbench.domain.model.MemoryKind
import com.aichat.workbench.domain.repository.MemoryRepository
import java.time.Clock
import java.util.UUID

class SaveMemoryUseCase(
    private val repository: MemoryRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        content: String,
        kind: MemoryKind = MemoryKind.UserFact,
        sourceConversationId: ConversationId? = null,
    ): MemoryItem {
        val normalized = content
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_MEMORY_CHARS)
        require(normalized.isNotBlank()) { "记忆内容不能为空。" }

        val now = clock.instant()
        val memory = MemoryItem(
            id = MemoryItemId("memory-${UUID.randomUUID()}"),
            kind = kind,
            content = normalized,
            sourceConversationId = sourceConversationId,
            createdAt = now,
            updatedAt = now,
        )
        repository.saveMemory(memory)
        return memory
    }

    private companion object {
        const val MAX_MEMORY_CHARS = 2_000
    }
}
