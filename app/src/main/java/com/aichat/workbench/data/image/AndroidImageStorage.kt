package com.aichat.workbench.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.StatFs
import com.aichat.workbench.domain.exception.StorageException
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.StoredImagePaths
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidImageStorage(
    context: Context,
) : ImageStorage {
    private val imageRoot = File(context.filesDir, "images")
    private val originals = File(imageRoot, "originals")
    private val thumbnails = File(imageRoot, "thumbnails")

    override suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths =
        withContext(Dispatchers.IO) {
            // Validate id to prevent path traversal
            require(id.value.matches(SAFE_ID_REGEX)) {
                "Invalid image ID format: must contain only alphanumeric characters and hyphens"
            }

            try {
                // Check available disk space
                val requiredSpace = bytes.size * 2L // Original + thumbnail estimate
                if (!hasEnoughSpace(imageRoot, requiredSpace)) {
                    throw StorageException("磁盘空间不足，无法保存图片")
                }

                // Create directories
                if (!originals.exists() && !originals.mkdirs()) {
                    throw StorageException("无法创建图片存储目录")
                }
                if (!thumbnails.exists() && !thumbnails.mkdirs()) {
                    throw StorageException("无法创建缩略图存储目录")
                }

                val originalFile = File(originals, "${id.value}.png")
                val thumbnailFile = File(thumbnails, "${id.value}.png")

                // Write original file
                try {
                    originalFile.writeBytes(bytes)
                } catch (error: IOException) {
                    throw StorageException("保存原图失败", error)
                }

                // Create and save thumbnail
                try {
                    val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                        "生成的图片数据不是可读取的 bitmap。"
                    }
                    val thumbnail = bitmap.createThumbnail()
                    bitmap.recycle()
                    thumbnail.use {
                        thumbnailFile.outputStream().use { output ->
                            it.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
                        }
                    }
                } catch (error: Exception) {
                    // Cleanup original file if thumbnail creation failed
                    originalFile.delete()
                    throw StorageException("生成缩略图失败", error)
                }

                StoredImagePaths(
                    originalPath = originalFile.absolutePath,
                    thumbnailPath = thumbnailFile.absolutePath,
                )
            } catch (error: StorageException) {
                throw error
            } catch (error: Exception) {
                throw StorageException("保存图片失败", error)
            }
        }

    override suspend fun deleteImage(id: ImageGenerationId) {
        withContext(Dispatchers.IO) {
            if (!id.value.matches(SAFE_ID_REGEX)) return@withContext

            try {
                val originalFile = File(originals, "${id.value}.png")
                val thumbnailFile = File(thumbnails, "${id.value}.png")

                if (originalFile.exists() && !originalFile.delete()) {
                    throw StorageException("删除原图失败: ${id.value}")
                }
                if (thumbnailFile.exists() && !thumbnailFile.delete()) {
                    throw StorageException("删除缩略图失败: ${id.value}")
                }
            } catch (error: Exception) {
                if (error is StorageException) throw error
                throw StorageException("删除图片失败: ${id.value}", error)
            }
        }
    }

    override suspend fun deleteAllImages() {
        withContext(Dispatchers.IO) {
            if (imageRoot.exists() && !imageRoot.deleteRecursively()) {
                throw StorageException("清空图片存储目录失败")
            }
        }
    }

    private fun Bitmap.createThumbnail(): Bitmap {
        val longestSide = maxOf(width, height).coerceAtLeast(1)
        val scale = THUMBNAIL_MAX_SIDE.toFloat() / longestSide.toFloat()
        if (scale >= 1f) return copy(config ?: Bitmap.Config.ARGB_8888, false)
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private inline fun <R> Bitmap.use(block: (Bitmap) -> R): R =
        try {
            block(this)
        } finally {
            recycle()
        }

    private fun hasEnoughSpace(directory: File, requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(directory.parent ?: directory.absolutePath)
            val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
            availableBytes >= requiredBytes + MIN_FREE_SPACE_BYTES
        } catch (error: Exception) {
            // If we can't determine space, assume we have enough
            true
        }
    }

    private companion object {
        const val THUMBNAIL_MAX_SIDE = 320
        const val PNG_QUALITY = 100
        const val MIN_FREE_SPACE_BYTES = 50 * 1024 * 1024L // 50 MB safety buffer
        val SAFE_ID_REGEX = Regex("^[a-zA-Z0-9_-]+$")
    }
}
