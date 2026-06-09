package com.aichat.workbench.ui.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.component.WorkbenchIconButton

@Composable
fun MarkdownMessageContent(
    text: String,
    modifier: Modifier = Modifier,
    parser: MarkdownBlockParser = DefaultMarkdownBlockParser.instance,
) {
    val blocks = remember(text, parser) { parser.parse(text) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            MarkdownBlockContent(block = block)
        }
    }
}

@Composable
private fun MarkdownBlockContent(
    block: MarkdownBlock,
) {
    when (block) {
        is MarkdownBlock.Paragraph -> ParagraphText(block.text)
        is MarkdownBlock.Heading -> HeadingText(block)
        is MarkdownBlock.CodeBlock -> CodeBlockContent(block)
        is MarkdownBlock.LatexBlock -> LatexBlockContent(block)
        is MarkdownBlock.Quote -> QuoteContent(block)
        is MarkdownBlock.BulletList -> BulletListContent(block)
        is MarkdownBlock.OrderedList -> OrderedListContent(block)
        is MarkdownBlock.Table -> TableContent(block)
        MarkdownBlock.Divider -> HorizontalDivider()
    }
}

@Composable
private fun ParagraphText(text: String) {
    SelectionContainer {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun HeadingText(block: MarkdownBlock.Heading) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    SelectionContainer {
        Text(
            text = block.text,
            style = style,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QuoteContent(block: MarkdownBlock.Quote) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = block.text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BulletListContent(block: MarkdownBlock.BulletList) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderedListContent(block: MarkdownBlock.OrderedList) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${block.startNumber + index}.",
                    modifier = Modifier.width(28.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlockContent(block: MarkdownBlock.CodeBlock) {
    val title = when {
        block.mermaid -> "Mermaid"
        block.language != null -> block.language
        else -> "代码"
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            CopyHeader(
                label = title,
                value = block.content,
            )
            SelectionContainer {
                Text(
                    text = block.content.ifBlank { " " },
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun LatexBlockContent(block: MarkdownBlock.LatexBlock) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.46f)),
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            CopyHeader(label = "LaTeX", value = block.content)
            Text(
                text = block.content,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TableContent(block: MarkdownBlock.Table) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f)),
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        ) {
            if (block.headers.isNotEmpty()) {
                TableRow(block.headers, header = true)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
            block.rows.forEach { row ->
                TableRow(row, header = false)
            }
        }
    }
}

@Composable
private fun TableRow(cells: List<String>, header: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEach { cell ->
            Text(
                text = cell,
                modifier = Modifier.width(120.dp),
                style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
@Suppress("DEPRECATION")
private fun CopyHeader(label: String, value: String) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        WorkbenchIconButton(
            icon = Icons.Filled.ContentCopy,
            label = copyContentDescription(label),
            onClick = { clipboardManager.setText(AnnotatedString(value)) },
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
