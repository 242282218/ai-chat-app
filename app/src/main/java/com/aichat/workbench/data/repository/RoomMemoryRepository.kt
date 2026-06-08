package com.aichat.workbench.data.repository

import com.aichat.workbench.data.local.dao.MemoryDao
import com.aichat.workbench.data.mapper.toDomain
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.domain.model.MemoryItem
import com.aichat.workbench.domain.model.MemoryItemId
import com.aichat.workbench.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMemoryRepository(
    private val dao: MemoryDao,
) : MemoryRepository {
    override fun observeMemories(): Flow<List<MemoryItem>> =
        dao.observeMemories().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getMemory(id: MemoryItemId): MemoryItem? =
        dao.getMemory(id.value)?.toDomain()

    override suspend fun saveMemory(memory: MemoryItem) {
        dao.upsertMemory(memory.toEntity())
    }

    override suspend fun deleteMemory(id: MemoryItemId) {
        dao.deleteMemory(id.value)
    }

    override suspend fun findRelevantMemories(query: String, limit: Int): List<MemoryItem> {
        val normalizedLimit = limit.coerceAtLeast(0)
        if (normalizedLimit == 0) return emptyList()
        val terms = query.memoryTerms()
        val recent = dao.getRecentMemories(MAX_MEMORY_SCAN).map { it.toDomain() }
        if (terms.isEmpty()) return recent.take(normalizedLimit)
        return recent
            .map { memory -> memory to memory.relevanceScore(terms) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<MemoryItem, Int>> { it.second }
                    .thenByDescending { it.first.updatedAt },
            )
            .map { it.first }
            .take(normalizedLimit)
    }

    private fun String.memoryTerms(): Set<String> =
        lowercase()
            .split(MEMORY_TERM_SPLIT)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .toSet()

    private fun MemoryItem.relevanceScore(terms: Set<String>): Int {
        val normalizedContent = content.lowercase()
        return terms.count { term -> normalizedContent.contains(term) }
    }

    private companion object {
        const val MAX_MEMORY_SCAN = 200
        val MEMORY_TERM_SPLIT = Regex("""[^\p{L}\p{N}_-]+""")
    }
}
