package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import kotlinx.coroutines.flow.Flow

interface ImageGenerationRepository {
    fun observeImageGenerations(): Flow<List<ImageGeneration>>

    suspend fun getImageGeneration(id: ImageGenerationId): ImageGeneration?

    suspend fun saveImageGeneration(imageGeneration: ImageGeneration)

    suspend fun deleteImageGeneration(id: ImageGenerationId)

    suspend fun deleteAllImageGenerations()
}
