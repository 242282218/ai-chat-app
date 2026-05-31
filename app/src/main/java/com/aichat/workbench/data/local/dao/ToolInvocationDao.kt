package com.aichat.workbench.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aichat.workbench.data.local.entity.ToolInvocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ToolInvocationDao {
    @Query("SELECT * FROM tool_invocations ORDER BY started_at DESC")
    fun observeToolInvocations(): Flow<List<ToolInvocationEntity>>

    @Upsert
    suspend fun upsertToolInvocation(toolInvocation: ToolInvocationEntity)

    @Query("DELETE FROM tool_invocations")
    suspend fun deleteAllToolInvocations()
}
