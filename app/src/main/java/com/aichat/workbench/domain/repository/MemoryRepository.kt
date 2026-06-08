package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.MemoryItem
import com.aichat.workbench.domain.model.MemoryItemId
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun observeMemories(): Flow<List<MemoryItem>>

    suspend fun getMemory(id: MemoryItemId): MemoryItem?

    suspend fun saveMemory(memory: MemoryItem)

    suspend fun deleteMemory(id: MemoryItemId)

    suspend fun findRelevantMemories(query: String, limit: Int = 6): List<MemoryItem>
}

object EmptyMemoryRepository : MemoryRepository {
    override fun observeMemories(): Flow<List<MemoryItem>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun getMemory(id: MemoryItemId): MemoryItem? = null

    override suspend fun saveMemory(memory: MemoryItem) = Unit

    override suspend fun deleteMemory(id: MemoryItemId) = Unit

    override suspend fun findRelevantMemories(query: String, limit: Int): List<MemoryItem> =
        emptyList()
}
