package com.aichat.workbench.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.StoredImagePaths
import java.io.File
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
            originals.mkdirs()
            thumbnails.mkdirs()

            val originalFile = File(originals, "${id.value}.png")
            val thumbnailFile = File(thumbnails, "${id.value}.png")
            originalFile.writeBytes(bytes)

            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
                "Generated image data is not a readable bitmap."
            }
            val thumbnail = bitmap.createThumbnail()
            bitmap.recycle()
            thumbnail.use {
                thumbnailFile.outputStream().use { output ->
                    it.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
                }
            }

            StoredImagePaths(
                originalPath = originalFile.absolutePath,
                thumbnailPath = thumbnailFile.absolutePath,
            )
        }

    override suspend fun deleteAllImages() {
        withContext(Dispatchers.IO) {
            imageRoot.deleteRecursively()
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

    private companion object {
        const val THUMBNAIL_MAX_SIDE = 320
        const val PNG_QUALITY = 100
    }
}
