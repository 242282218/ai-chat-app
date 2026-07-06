package com.aichat.workbench.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aichat.workbench.data.local.AiChatDatabase
import com.aichat.workbench.domain.exception.StorageException
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.repository.ImageStorage
import com.aichat.workbench.domain.repository.StoredImagePaths
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomImageGenerationRepositoryTest {
    private lateinit var database: AiChatDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AiChatDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun deleteImageGeneration_keepsDatabaseRowWhenFileCleanupFails() = runTest {
        val storage = FailingImageStorage()
        val repository = RoomImageGenerationRepository(database.imageGenerationDao(), storage)
        val image = imageGeneration(ImageGenerationId("image-1"))
        repository.saveImageGeneration(image)

        val error = assertFailsWith<StorageException> {
            repository.deleteImageGeneration(image.id)
        }

        assertEquals("图片文件清理失败，请重试。", error.message)
        assertNotNull(repository.getImageGeneration(image.id))
        assertTrue(storage.deleteAttempts.contains(image.id))
    }

    private fun imageGeneration(id: ImageGenerationId): ImageGeneration =
        ImageGeneration(
            id = id,
            conversationId = null,
            prompt = "prompt",
            providerId = ProviderId("provider-1"),
            model = "gpt-image-1",
            size = "1024x1024",
            quality = "auto",
            count = 1,
            originalPath = "original/${id.value}.png",
            thumbnailPath = "thumbnail/${id.value}.png",
            status = ImageGenerationStatus.Completed,
            errorSummary = null,
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
        )

    private class FailingImageStorage : ImageStorage {
        val deleteAttempts = mutableListOf<ImageGenerationId>()

        override suspend fun savePng(id: ImageGenerationId, bytes: ByteArray): StoredImagePaths =
            StoredImagePaths("original/${id.value}.png", "thumbnail/${id.value}.png")

        override suspend fun deleteImage(id: ImageGenerationId) {
            deleteAttempts += id
            error("file cleanup failed")
        }

        override suspend fun deleteAllImages() {
            error("file cleanup failed")
        }
    }
}
