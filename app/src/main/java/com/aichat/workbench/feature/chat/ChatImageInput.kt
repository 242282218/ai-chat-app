package com.aichat.workbench.feature.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.aichat.workbench.domain.model.MessagePart
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun encodeChatImage(context: Context, uri: Uri): MessagePart.Image =
    withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取图片。")
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
        val base64 = Base64.getEncoder().encodeToString(output.toByteArray())
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

private const val MAX_IMAGE_SIDE = 2048
private const val JPEG_QUALITY = 85
