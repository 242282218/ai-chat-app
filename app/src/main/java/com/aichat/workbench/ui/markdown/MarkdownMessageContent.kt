package com.aichat.workbench.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aichat.workbench.feature.chat.CopyState
import com.aichat.workbench.feature.chat.rememberCopyState
import com.aichat.workbench.ui.component.WorkbenchIconButton

@Composable
fun MarkdownMessageContent(
    text: String,
    modifier: Modifier = Modifier,
    parser: MarkdownBlockParser = DefaultMarkdownBlockParser.instance,
    highlightQuery: String = "",
) {
    val blocks = remember(text, parser) { parser.parse(text) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            MarkdownBlockContent(block = block, highlightQuery = highlightQuery)
        }
    }
}

@Composable
internal fun MarkdownBlockContent(
    block: MarkdownBlock,
    highlightQuery: String,
) {
    when (block) {
        is MarkdownBlock.Paragraph -> ParagraphText(block.text, highlightQuery)
        is MarkdownBlock.Heading -> HeadingText(block, highlightQuery)
        is MarkdownBlock.CodeBlock -> CodeBlockContent(block, highlightQuery)
        is MarkdownBlock.LatexBlock -> LatexBlockContent(block, highlightQuery)
        is MarkdownBlock.Quote -> QuoteContent(block, highlightQuery)
        is MarkdownBlock.BulletList -> BulletListContent(block, highlightQuery)
        is MarkdownBlock.OrderedList -> OrderedListContent(block, highlightQuery)
        is MarkdownBlock.Table -> TableContent(block, highlightQuery)
        MarkdownBlock.Divider -> HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 0.5.dp,
        )
    }
}

@Composable
private fun rememberHighlightedText(text: String, query: String): AnnotatedString {
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    return remember(text, query, highlightBg) {
        if (query.isBlank()) AnnotatedString(text)
        else buildHighlightedAnnotatedString(text, query, highlightBg)
    }
}

private fun buildHighlightedAnnotatedString(
    text: String,
    query: String,
    highlightColor: Color,
): AnnotatedString = buildAnnotatedString {
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    var pos = 0
    while (pos < text.length) {
        val matchIndex = lowerText.indexOf(lowerQuery, pos)
        if (matchIndex < 0) {
            append(text.substring(pos))
            break
        }
        if (matchIndex > pos) {
            append(text.substring(pos, matchIndex))
        }
        withStyle(SpanStyle(background = highlightColor)) {
            append(text.substring(matchIndex, matchIndex + query.length))
        }
        pos = matchIndex + query.length
    }
}

@Composable
private fun ParagraphText(text: String, highlightQuery: String) {
    val annotated = rememberHighlightedText(text, highlightQuery)
    SelectionContainer {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HeadingText(block: MarkdownBlock.Heading, highlightQuery: String) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    val annotated = rememberHighlightedText(block.text, highlightQuery)
    SelectionContainer {
        Text(
            text = annotated,
            style = style,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuoteContent(block: MarkdownBlock.Quote, highlightQuery: String) {
    val annotated = rememberHighlightedText(block.text, highlightQuery)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = annotated,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun BulletListContent(block: MarkdownBlock.BulletList, highlightQuery: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "\u2022",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                val annotated = rememberHighlightedText(item, highlightQuery)
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderedListContent(block: MarkdownBlock.OrderedList, highlightQuery: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${block.startNumber + index}.",
                    modifier = Modifier.width(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                val annotated = rememberHighlightedText(item, highlightQuery)
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockContent(block: MarkdownBlock.CodeBlock, highlightQuery: String) {
    val title = when {
        block.mermaid -> "Mermaid"
        block.language != null -> block.language
        else -> "代码"
    }
    val highlightedCode = rememberHighlightedCode(block.content, block.language)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            CopyHeader(
                label = title,
                value = block.content,
            )
            SelectionContainer {
                Text(
                    text = highlightedCode.ifEmpty { AnnotatedString(" ") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                )
            }
        }
    }
}

@Composable
private fun LatexBlockContent(block: MarkdownBlock.LatexBlock, highlightQuery: String) {
    val annotated = rememberHighlightedText(block.content, highlightQuery)
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            CopyHeader(label = "LaTeX", value = block.content)
            Text(
                text = annotated,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TableContent(block: MarkdownBlock.Table, highlightQuery: String) {
    val columnWidths = remember(block) {
        tableColumnWidths(block.headers, block.rows)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            if (block.headers.isNotEmpty()) {
                TableRow(
                    cells = block.headers,
                    columnWidths = columnWidths,
                    header = true,
                    highlightQuery = highlightQuery,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 0.5.dp,
                )
            }
            block.rows.forEach { row ->
                TableRow(
                    cells = row,
                    columnWidths = columnWidths,
                    header = false,
                    highlightQuery = highlightQuery,
                )
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    columnWidths: List<Dp>,
    header: Boolean,
    highlightQuery: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEachIndexed { index, cell ->
            val annotated = rememberHighlightedText(cell, highlightQuery)
            val cellWidth = columnWidths.getOrElse(index) { 120.dp }
            Text(
                text = annotated,
                modifier = Modifier.widthIn(min = cellWidth, max = cellWidth),
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CopyHeader(label: String, value: String) {
    val clipboardManager = LocalClipboardManager.current
    val copyState = rememberCopyState(value)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        WorkbenchIconButton(
            icon = when (copyState.value) { CopyState.Copied -> Icons.Filled.Check; CopyState.Failed -> Icons.Filled.Close; CopyState.Ready -> Icons.Filled.ContentCopy },
            label = when (copyState.value) { CopyState.Copied -> "已复制"; CopyState.Failed -> "复制失败"; CopyState.Ready -> copyContentDescription(label) },
            onClick = {
                try {
                    clipboardManager.setText(AnnotatedString(value))
                    copyState.value = CopyState.Copied
                } catch (_: Exception) {
                    copyState.value = CopyState.Failed
                }
            },
            tint = when (copyState.value) { CopyState.Copied -> MaterialTheme.colorScheme.primary; CopyState.Failed -> MaterialTheme.colorScheme.error; CopyState.Ready -> MaterialTheme.colorScheme.onSurfaceVariant },
        )
    }
}

private fun copyContentDescription(label: String): String =
    when (label.lowercase()) {
        "code",
        "代码" -> "复制代码"
        "latex" -> "复制 LaTeX"
        "mermaid" -> "复制 Mermaid 源码"
        else -> "复制 $label 代码"
    }

private fun tableColumnWidths(headers: List<String>, rows: List<List<String>>): List<Dp> {
    val maxColumns = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    return (0 until maxColumns).map { column ->
        val maxLength = listOf(headers.getOrNull(column).orEmpty())
            .plus(rows.map { row -> row.getOrNull(column).orEmpty() })
            .maxOf { it.length }
        tableCellWidth(maxLength)
    }
}

private fun tableCellWidth(length: Int) =
    when {
        length <= 8 -> 96.dp
        length <= 16 -> 136.dp
        else -> 180.dp
    }
