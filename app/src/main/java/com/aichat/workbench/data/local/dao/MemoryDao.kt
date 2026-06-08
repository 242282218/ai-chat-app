package com.aichat.workbench.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aichat.workbench.data.local.entity.MemoryItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items ORDER BY updated_at DESC")
    fun observeMemories(): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items WHERE id = :id")
    suspend fun getMemory(id: String): MemoryItemEntity?

    @Query("SELECT * FROM memory_items ORDER BY updated_at DESC LIMIT :limit")
    suspend fun getRecentMemories(limit: Int): List<MemoryItemEntity>

    @Upsert
    suspend fun upsertMemory(memory: MemoryItemEntity)

    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("DELETE FROM memory_items")
    suspend fun deleteAllMemories()
}
