package com.aichat.workbench.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownBlockParserTest {
    private val parser = MarkdownBlockParser()

    @Test
    fun parsesHeadingsParagraphsAndCodeBlocks() {
        val blocks = parser.parse(
            """
            # Title

            Hello **world**.

            ```kotlin
            val answer = 42
            ```
            """.trimIndent(),
        )

        assertEquals(MarkdownBlock.Heading(level = 1, text = "Title"), blocks[0])
        assertEquals(MarkdownBlock.Paragraph("Hello world."), blocks[1])

        val code = blocks[2] as MarkdownBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val answer = 42", code.content)
    }

    @Test
    fun parsesGfmTables() {
        val blocks = parser.parse(
            """
            | Name | Value |
            | --- | --- |
            | temp | 0.7 |
            | top_p | 1.0 |
            """.trimIndent(),
        )

        val table = blocks.single() as MarkdownBlock.Table
        assertEquals(listOf("Name", "Value"), table.headers)
        assertEquals(
            listOf(
                listOf("temp", "0.7"),
                listOf("top_p", "1.0"),
            ),
            table.rows,
        )
    }

    @Test
    fun marksMermaidCodeBlocksForFallbackRendering() {
        val blocks = parser.parse(
            """
            ```mermaid
            graph TD
              A --> B
            ```
            """.trimIndent(),
        )

        val code = blocks.single() as MarkdownBlock.CodeBlock
        assertTrue(code.mermaid)
        assertEquals("mermaid", code.language)
    }

    @Test
    fun detectsLatexBlocks() {
        val blocks = parser.parse(
            """
            $$
            a^2 + b^2 = c^2
            $$
            """.trimIndent(),
        )

        assertEquals(MarkdownBlock.LatexBlock("a^2 + b^2 = c^2"), blocks.single())
    }

    @Test
    fun parsesQuotesListsAndDividers() {
        val blocks = parser.parse(
            """
            > Keep answers traceable.

            - web search
            - code sandbox

            3. inspect
            4. verify

            ---
            """.trimIndent(),
        )

        assertEquals(MarkdownBlock.Quote("Keep answers traceable."), blocks[0])
        assertEquals(MarkdownBlock.BulletList(listOf("web search", "code sandbox")), blocks[1])
        assertEquals(MarkdownBlock.OrderedList(startNumber = 3, items = listOf("inspect", "verify")), blocks[2])
        assertEquals(MarkdownBlock.Divider, blocks[3])
    }

    @Test
    fun reusesCachedBlocksForRepeatedMarkdown() {
        val cachedParser = MarkdownBlockParser(cacheSize = 2)

        val first = cachedParser.parse("# Cached")
        val second = cachedParser.parse("# Cached")

        assertTrue(first === second)
    }

    @Test
    fun evictsOldMarkdownBlocksWhenCacheIsFull() {
        val cachedParser = MarkdownBlockParser(cacheSize = 1)

        val first = cachedParser.parse("# Old")
        cachedParser.parse("# New")
        val reparsed = cachedParser.parse("# Old")

        assertEquals(first, reparsed)
        assertTrue(first !== reparsed)
    }
}
