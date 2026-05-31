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
}
