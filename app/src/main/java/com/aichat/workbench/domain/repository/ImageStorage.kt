package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.ImageGenerationId

data class StoredImagePaths(
    val originalPath: String,
    val thumbnailPath: String,
)

interface ImageStorage {
    suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths

    suspend fun deleteAllImages()
}
