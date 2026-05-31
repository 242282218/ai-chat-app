package com.aichat.workbench.ui.markdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class CodeBlock(val language: String?, val content: String, val mermaid: Boolean) : MarkdownBlock
    data class LatexBlock(val content: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class OrderedList(val startNumber: Int, val items: List<String>) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data object Divider : MarkdownBlock
}

class MarkdownBlockParser(
    private val parser: Parser = Parser.builder()
        .extensions(listOf(TablesExtension.create()))
        .build(),
) {
    fun parse(markdown: String): List<MarkdownBlock> =
        runCatching {
            val blocks = parser.parse(markdown).children().mapNotNull { it.toBlock() }.toList()
            blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(markdown)) }
        }.getOrElse {
            listOf(MarkdownBlock.Paragraph(markdown))
        }

    private fun Node.toBlock(): MarkdownBlock? =
        when (this) {
            is Heading -> MarkdownBlock.Heading(level = level, text = inlineText().trim())
            is Paragraph -> paragraphBlock()
            is FencedCodeBlock -> codeBlock(info, literal)
            is IndentedCodeBlock -> codeBlock(language = null, content = literal)
            is BlockQuote -> inlineText().trim().takeIf { it.isNotBlank() }?.let(MarkdownBlock::Quote)
            is BulletList -> listItems().takeIf { it.isNotEmpty() }?.let(MarkdownBlock::BulletList)
            is OrderedList -> listItems().takeIf { it.isNotEmpty() }?.let {
                MarkdownBlock.OrderedList(startNumber = markerStartNumber, items = it)
            }
            is TableBlock -> tableBlock()
            is ThematicBreak -> MarkdownBlock.Divider
            else -> inlineText().trim().takeIf { it.isNotBlank() }?.let(MarkdownBlock::Paragraph)
        }

    private fun Paragraph.paragraphBlock(): MarkdownBlock? {
        val text = inlineText().trim()
        if (text.isBlank()) return null
        return latexBlock(text) ?: MarkdownBlock.Paragraph(text)
    }

    private fun latexBlock(text: String): MarkdownBlock.LatexBlock? {
        if (!text.startsWith("$$") || !text.endsWith("$$") || text.length < 4) return null
        return MarkdownBlock.LatexBlock(text.removePrefix("$$").removeSuffix("$$").trim())
    }

    private fun codeBlock(language: String?, content: String): MarkdownBlock.CodeBlock {
        val normalizedLanguage = language
            ?.trim()
            ?.substringBefore(' ')
            ?.takeIf { it.isNotBlank() }
        return MarkdownBlock.CodeBlock(
            language = normalizedLanguage,
            content = content.trimEnd(),
            mermaid = normalizedLanguage.equals("mermaid", ignoreCase = true),
        )
    }

    private fun Node.listItems(): List<String> =
        children()
            .filterIsInstance<ListItem>()
            .map { it.inlineText().trim() }
            .filter { it.isNotBlank() }
            .toList()

    private fun TableBlock.tableBlock(): MarkdownBlock.Table? {
        val headRows = children()
            .filterIsInstance<TableHead>()
            .flatMap { it.rows() }
            .toList()
        val bodyRows = children()
            .filterIsInstance<TableBody>()
            .flatMap { it.rows() }
            .toList()

        val headers = headRows.firstOrNull().orEmpty()
        if (headers.isEmpty() && bodyRows.isEmpty()) return null
        return MarkdownBlock.Table(headers = headers, rows = bodyRows)
    }

    private fun Node.rows(): Sequence<List<String>> =
        children()
            .filterIsInstance<TableRow>()
            .map { row ->
                row.children()
                    .filterIsInstance<TableCell>()
                    .map { it.inlineText().trim() }
                    .toList()
            }

    private fun Node.inlineText(): String {
        val builder = StringBuilder()
        accept(
            object : AbstractVisitor() {
                override fun visit(text: Text) {
                    builder.append(text.literal)
                }

                override fun visit(code: Code) {
                    builder.append(code.literal)
                }

                override fun visit(softLineBreak: SoftLineBreak) {
                    builder.append('\n')
                }

                override fun visit(hardLineBreak: HardLineBreak) {
                    builder.append('\n')
                }

                override fun visit(link: Link) {
                    visitChildren(link)
                    if (link.destination.isNotBlank()) {
                        builder.append(" (").append(link.destination).append(')')
                    }
                }

                override fun visit(emphasis: Emphasis) {
                    visitChildren(emphasis)
                }

                override fun visit(strongEmphasis: StrongEmphasis) {
                    visitChildren(strongEmphasis)
                }
            },
        )
        return builder.toString()
    }

    private fun Node.children(): Sequence<Node> =
        generateSequence(firstChild) { it.next }
}

object DefaultMarkdownBlockParser {
    val instance = MarkdownBlockParser()
}
