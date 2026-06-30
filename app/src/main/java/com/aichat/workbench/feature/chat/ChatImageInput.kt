package com.aichat.workbench.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.aichat.workbench.domain.model.MessagePart
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun encodeChatImage(context: Context, uri: Uri): MessagePart.Image =
    withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_INPUT_IMAGE_BYTES + 1) }
            ?: error("无法读取图片。")
        require(bytes.size <= MAX_INPUT_IMAGE_BYTES) {
            "图片文件过大，请选择小于 ${MAX_INPUT_IMAGE_BYTES / 1024 / 1024}MB 的图片。"
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val bitmap = requireNotNull(
            BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = bounds.sampleSizeFor(MAX_IMAGE_SIDE) },
            ),
        ) { "无法解析图片。" }
        val scaled = bitmap.scaleDown(MAX_IMAGE_SIDE)
        if (scaled !== bitmap) bitmap.recycle()
        val output = ByteArrayOutputStream()
        scaled.use {
            it.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        }
        val encodedBytes = output.toByteArray()
        require(encodedBytes.size <= MAX_ENCODED_CHAT_IMAGE_BYTES) {
            "压缩后的图片仍然过大，请选择更小的图片。"
        }
        val base64 = Base64.getEncoder().encodeToString(encodedBytes)
        MessagePart.Image(
            uri = "data:image/jpeg;base64,$base64",
            mimeType = "image/jpeg",
        )
    }

private fun BitmapFactory.Options.sampleSizeFor(maxSide: Int): Int {
    var sampleSize = 1
    val longest = maxOf(outWidth, outHeight)
    while (longest / sampleSize > maxSide * 2) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun Bitmap.scaleDown(maxSide: Int): Bitmap {
    val longest = maxOf(width, height).coerceAtLeast(1)
    if (longest <= maxSide) return this
    val scale = maxSide.toFloat() / longest.toFloat()
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true,
    )
}

private inline fun <R> Bitmap.use(block: (Bitmap) -> R): R =
    try {
        block(this)
    } finally {
        recycle()
    }

private fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = ByteArrayOutputStream(maxBytes.coerceAtMost(DEFAULT_BUFFER_SIZE))
    var remaining = maxBytes
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read == -1) break
        output.write(buffer, 0, read)
        remaining -= read
    }
    return output.toByteArray()
}

private const val MAX_IMAGE_SIDE = 2048
private const val MAX_INPUT_IMAGE_BYTES = 20 * 1024 * 1024
private const val MAX_ENCODED_CHAT_IMAGE_BYTES = 6 * 1024 * 1024
private const val JPEG_QUALITY = 85
