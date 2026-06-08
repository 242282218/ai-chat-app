package com.aichat.workbench.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.util.Base64

internal sealed interface InlineImageSource {
    data class RemoteUrl(val url: String) : InlineImageSource
    data class LocalContent(val raw: String) : InlineImageSource
    data object Unsupported : InlineImageSource
}

internal fun resolveInlineImageSource(imageUrl: String?): InlineImageSource {
    val normalized = imageUrl?.trim().orEmpty()
    if (normalized.isBlank()) return InlineImageSource.Unsupported
    return when {
        normalized.startsWith("http://", ignoreCase = true) ||
            normalized.startsWith("https://", ignoreCase = true) -> InlineImageSource.RemoteUrl(normalized)
        normalized.startsWith("data:image", ignoreCase = true) -> InlineImageSource.LocalContent(normalized)
        normalized.startsWith("file://", ignoreCase = true) -> InlineImageSource.LocalContent(normalized)
        File(normalized).isFile -> InlineImageSource.LocalContent(normalized)
        else -> InlineImageSource.Unsupported
    }
}

internal fun decodeInlineImageBitmap(
    raw: String,
    maxSide: Int = DEFAULT_INLINE_IMAGE_MAX_SIDE,
): ImageBitmap? =
    runCatching {
        when {
            raw.startsWith("data:image", ignoreCase = true) -> decodeBase64Bitmap(raw, maxSide)
            raw.startsWith("file://", ignoreCase = true) -> {
                val path = Uri.parse(raw).path ?: return@runCatching null
                decodeFileBitmap(path, maxSide)
            }
            File(raw).isFile -> decodeFileBitmap(raw, maxSide)
            else -> null
        }?.asImageBitmap()
    }.getOrNull()

private fun decodeBase64Bitmap(
    raw: String,
    maxSide: Int,
): Bitmap? {
    val base64 = raw.substringAfter("base64,", missingDelimiterValue = "")
    if (base64.isBlank()) return null
    val bytes = Base64.getDecoder().decode(base64)
    return decodeByteArrayBitmap(bytes, maxSide)
}

private fun decodeByteArrayBitmap(
    bytes: ByteArray,
    maxSide: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply {
            inSampleSize = bounds.sampleSizeFor(maxSide)
        },
    )
}

private fun decodeFileBitmap(
    path: String,
    maxSide: Int,
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = bounds.sampleSizeFor(maxSide)
        },
    )
}

private fun BitmapFactory.Options.sampleSizeFor(maxSide: Int): Int {
    var sampleSize = 1
    val longest = maxOf(outWidth, outHeight).coerceAtLeast(1)
    while (longest / sampleSize > maxSide) {
        sampleSize *= 2
    }
    return sampleSize
}

private const val DEFAULT_INLINE_IMAGE_MAX_SIDE = 1536
