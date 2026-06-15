package com.aichat.workbench.ui.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optimized Markdown renderer with async parsing.
 * Part of Phase 3: Performance Optimization
 *
 * Features:
 * - Async parsing on Dispatchers.Default (doesn't block UI thread)
 * - Shows loading indicator during parsing
 * - Graceful fallback on error
 */
@Composable
fun AsyncMarkdownMessageContent(
    text: String,
    modifier: Modifier = Modifier,
    parser: MarkdownBlockParser = DefaultMarkdownBlockParser.instance,
    highlightQuery: String = "",
) {
    // Async parsing using produceState
    val parseResult = produceState<ParseResult>(
        initialValue = ParseResult.Loading,
        key1 = text,
        key2 = parser
    ) {
        value = try {
            val blocks = withContext(Dispatchers.Default) {
                parser.parse(text)
            }
            ParseResult.Success(blocks)
        } catch (e: Exception) {
            ParseResult.Error(e)
        }
    }

    when (val result = parseResult.value) {
        is ParseResult.Loading -> {
            // Show minimal loading indicator for very long text
            if (text.length > 5000) {
                Column(
                    modifier = modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // For short text, show as-is (parsing is fast)
                MarkdownMessageContent(
                    text = text,
                    modifier = modifier,
                    parser = parser,
                    highlightQuery = highlightQuery
                )
            }
        }
        is ParseResult.Success -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                result.blocks.forEach { block ->
                    MarkdownBlockContent(block = block, highlightQuery = highlightQuery)
                }
            }
        }
        is ParseResult.Error -> {
            // Fallback to synchronous rendering
            MarkdownMessageContent(
                text = text,
                modifier = modifier,
                parser = parser,
                highlightQuery = highlightQuery
            )
        }
    }
}

/**
 * Parse result sealed class
 */
private sealed interface ParseResult {
    object Loading : ParseResult
    data class Success(val blocks: List<MarkdownBlock>) : ParseResult
    data class Error(val exception: Exception) : ParseResult
}
