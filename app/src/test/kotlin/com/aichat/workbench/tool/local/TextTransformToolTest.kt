package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolOutput
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class TextTransformToolTest {
    private val tool = TextTransformTool()

    @Test
    fun regexPreviewDoesNotMarkExactlyMaxMatchesAsTruncated() = runTest {
        val output = executeRegexPreview(matchCount = 50)

        assertEquals(50, output.matchesCount())
        assertEquals(false, output.truncated())
    }

    @Test
    fun regexPreviewMarksOverflowMatchesAsTruncated() = runTest {
        val output = executeRegexPreview(matchCount = 51)

        assertEquals(50, output.matchesCount())
        assertEquals(true, output.truncated())
    }

    private suspend fun executeRegexPreview(matchCount: Int): String {
        val text = (1..matchCount).joinToString(" ") { "item$it" }
        val execution = tool.execute(
            LocalToolRequest(
                conversationId = ConversationId("conversation"),
                toolCall = ToolCall(
                    id = ToolCallId("call"),
                    name = "text_transform",
                    arguments = """{"operation":"regex_preview","text":"$text","regex":"item\\d+"}""",
                ),
            ),
        )
        return (execution.output as ToolOutput.Json).value
    }

    private fun String.matchesCount(): Int =
        textTransformTestJson.parseToJsonElement(this)
            .jsonObject
            .getValue("matches")
            .jsonArray
            .size

    private fun String.truncated(): Boolean =
        textTransformTestJson.parseToJsonElement(this)
            .jsonObject
            .getValue("truncated")
            .jsonPrimitive
            .boolean
}

private val textTransformTestJson = Json { ignoreUnknownKeys = true }
