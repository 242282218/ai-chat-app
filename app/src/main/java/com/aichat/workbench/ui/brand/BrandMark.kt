package com.aichat.workbench.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.theme.workbenchColors

enum class WorkbenchArtworkKind {
    ChatSpark,
    ImageCanvas,
    ProviderControls,
    WorkbenchSpark,
}

@Composable
fun WorkbenchBrandMark(
    modifier: Modifier = Modifier.size(40.dp),
) {
    WorkbenchBrandArtwork(
        kind = WorkbenchArtworkKind.WorkbenchSpark,
        modifier = modifier,
    )
}

@Composable
fun WorkbenchBrandArtwork(
    kind: WorkbenchArtworkKind,
    modifier: Modifier = Modifier.size(88.dp),
) {
    val scheme = MaterialTheme.colorScheme
    val semantic = MaterialTheme.workbenchColors
    val accent = when (kind) {
        WorkbenchArtworkKind.ImageCanvas -> semantic.imageAccent
        WorkbenchArtworkKind.ProviderControls -> semantic.warning
        WorkbenchArtworkKind.ChatSpark,
        WorkbenchArtworkKind.WorkbenchSpark -> scheme.primary
    }
    val container = when (kind) {
        WorkbenchArtworkKind.ImageCanvas -> semantic.imageAccentContainer.copy(alpha = 0.62f)
        WorkbenchArtworkKind.ProviderControls -> semantic.warningContainer.copy(alpha = 0.58f)
        WorkbenchArtworkKind.ChatSpark,
        WorkbenchArtworkKind.WorkbenchSpark -> scheme.primaryContainer.copy(alpha = 0.72f)
    }

    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        drawRoundRect(
            color = container,
            size = size,
            cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
        )
        when (kind) {
            WorkbenchArtworkKind.ChatSpark -> drawChatSpark(
                accent = accent,
                surface = scheme.surface,
                detail = semantic.creativeAccent,
                strokeWidth = strokeWidth,
            )
            WorkbenchArtworkKind.ImageCanvas -> drawImageCanvas(
                accent = accent,
                surface = scheme.surface,
                detail = semantic.creativeAccent,
                strokeWidth = strokeWidth,
            )
            WorkbenchArtworkKind.ProviderControls -> drawProviderControls(
                accent = accent,
                surface = scheme.surface,
                strokeWidth = strokeWidth,
            )
            WorkbenchArtworkKind.WorkbenchSpark -> drawWorkbenchSpark(
                accent = accent,
                surface = scheme.surface,
                detail = semantic.imageAccent,
                strokeWidth = strokeWidth,
            )
        }
    }
}

private fun DrawScope.drawChatSpark(
    accent: Color,
    surface: Color,
    detail: Color,
    strokeWidth: Float,
) {
    val bubbleTopLeft = Offset(size.width * 0.22f, size.height * 0.25f)
    val bubbleSize = Size(size.width * 0.56f, size.height * 0.42f)
    val corner = CornerRadius(size.width * 0.08f, size.width * 0.08f)
    drawRoundRect(surface, bubbleTopLeft, bubbleSize, corner)
    drawRoundRect(accent, bubbleTopLeft, bubbleSize, corner, style = Stroke(strokeWidth))
    drawLine(
        color = accent,
        start = Offset(size.width * 0.39f, size.height * 0.66f),
        end = Offset(size.width * 0.32f, size.height * 0.78f),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawSpark(
        center = Offset(size.width * 0.50f, size.height * 0.46f),
        radius = size.minDimension * 0.11f,
        color = detail,
        strokeWidth = strokeWidth,
    )
}

private fun DrawScope.drawImageCanvas(
    accent: Color,
    surface: Color,
    detail: Color,
    strokeWidth: Float,
) {
    val frameTopLeft = Offset(size.width * 0.24f, size.height * 0.27f)
    val frameSize = Size(size.width * 0.52f, size.height * 0.46f)
    val corner = CornerRadius(size.width * 0.07f, size.width * 0.07f)
    drawRoundRect(surface, frameTopLeft, frameSize, corner)
    drawRoundRect(accent, frameTopLeft, frameSize, corner, style = Stroke(strokeWidth))
    drawCircle(
        color = detail.copy(alpha = 0.78f),
        radius = size.minDimension * 0.035f,
        center = Offset(size.width * 0.62f, size.height * 0.41f),
    )
    val mountain = Path().apply {
        moveTo(size.width * 0.32f, size.height * 0.62f)
        lineTo(size.width * 0.44f, size.height * 0.51f)
        lineTo(size.width * 0.53f, size.height * 0.60f)
        lineTo(size.width * 0.61f, size.height * 0.53f)
        lineTo(size.width * 0.70f, size.height * 0.64f)
    }
    drawPath(
        path = mountain,
        color = accent,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
    )
    drawSpark(
        center = Offset(size.width * 0.38f, size.height * 0.39f),
        radius = size.minDimension * 0.07f,
        color = detail,
        strokeWidth = strokeWidth * 0.8f,
    )
}

private fun DrawScope.drawProviderControls(
    accent: Color,
    surface: Color,
    strokeWidth: Float,
) {
    val lines = listOf(0.34f to 0.58f, 0.50f to 0.42f, 0.66f to 0.64f)
    lines.forEach { (y, knobX) ->
        drawLine(
            color = accent,
            start = Offset(size.width * 0.24f, size.height * y),
            end = Offset(size.width * 0.76f, size.height * y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = surface,
            radius = size.minDimension * 0.055f,
            center = Offset(size.width * knobX, size.height * y),
        )
        drawCircle(
            color = accent,
            radius = size.minDimension * 0.055f,
            center = Offset(size.width * knobX, size.height * y),
            style = Stroke(strokeWidth),
        )
    }
}

private fun DrawScope.drawWorkbenchSpark(
    accent: Color,
    surface: Color,
    detail: Color,
    strokeWidth: Float,
) {
    drawChatSpark(
        accent = accent,
        surface = surface,
        detail = accent,
        strokeWidth = strokeWidth,
    )
    val frameTopLeft = Offset(size.width * 0.54f, size.height * 0.50f)
    val frameSize = Size(size.width * 0.20f, size.height * 0.16f)
    val corner = CornerRadius(size.width * 0.025f, size.width * 0.025f)
    drawRoundRect(surface, frameTopLeft, frameSize, corner)
    drawRoundRect(detail, frameTopLeft, frameSize, corner, style = Stroke(strokeWidth * 0.8f))
}

private fun DrawScope.drawSpark(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
) {
    drawLine(
        color = color,
        start = center.copy(y = center.y - radius),
        end = center.copy(y = center.y + radius),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = center.copy(x = center.x - radius),
        end = center.copy(x = center.x + radius),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawCircle(
        color = color.copy(alpha = 0.58f),
        radius = strokeWidth * 0.9f,
        center = Offset(center.x + radius * 1.35f, center.y - radius * 1.1f),
    )
}
