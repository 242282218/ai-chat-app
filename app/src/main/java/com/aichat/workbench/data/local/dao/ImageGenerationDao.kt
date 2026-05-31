package com.aichat.workbench.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.aichat.workbench.data.local.entity.ImageGenerationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageGenerationDao {
    @Query("SELECT * FROM image_generations ORDER BY created_at DESC")
    fun observeImageGenerations(): Flow<List<ImageGenerationEntity>>

    @Query("SELECT * FROM image_generations WHERE id = :id")
    suspend fun getImageGeneration(id: String): ImageGenerationEntity?

    @Upsert
    suspend fun upsertImageGeneration(imageGeneration: ImageGenerationEntity)

    @Query("DELETE FROM image_generations WHERE id = :id")
    suspend fun deleteImageGeneration(id: String)

    @Query("DELETE FROM image_generations")
    suspend fun deleteAllImageGenerations()
}
