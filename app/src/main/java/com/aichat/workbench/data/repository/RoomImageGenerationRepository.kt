package com.aichat.workbench.data.repository

import com.aichat.workbench.data.local.dao.ImageGenerationDao
import com.aichat.workbench.data.mapper.toDomain
import com.aichat.workbench.data.mapper.toEntity
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.repository.ImageGenerationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomImageGenerationRepository(
    private val dao: ImageGenerationDao,
    private val imageStorage: com.aichat.workbench.domain.repository.ImageStorage,
) : ImageGenerationRepository {
    override fun observeImageGenerations(): Flow<List<ImageGeneration>> =
        dao.observeImageGenerations().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getImageGeneration(id: ImageGenerationId): ImageGeneration? =
        dao.getImageGeneration(id.value)?.toDomain()

    override suspend fun saveImageGeneration(imageGeneration: ImageGeneration) {
        dao.upsertImageGeneration(imageGeneration.toEntity())
    }

    override suspend fun deleteImageGeneration(id: ImageGenerationId) {
        // First delete the image files from storage
        imageStorage.deleteImage(id)
        // Then delete the database record
        dao.deleteImageGeneration(id.value)
    }

    override suspend fun deleteAllImageGenerations() {
        dao.deleteAllImageGenerations()
    }
}
