package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.ToolCall
import com.aichat.workbench.domain.model.ToolCallId
import com.aichat.workbench.domain.model.ToolOutput
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileReadToolTest {
    @Test
    fun executeReturnsPreviewAndNeverSerializesFullContent() = runTest {
        val tool = FileReadTool(
            RecordingAuthorizedFileReader(
                result = AuthorizedFileReadResult(
                    fileName = "notes.md",
                    mimeType = "text/markdown",
                    sizeBytes = 2048,
                    content = (1..50).joinToString("\n") { "line-$it" },
                    truncated = false,
                    unsupportedReason = null,
                ),
            ),
        )

        val output = tool.execute(
            request("""{"uri":"content://docs/notes.md","maxBytes":4096}"""),
        ).jsonOutput()

        assertTrue(output.contains(""""preview":"line-1\nline-2"""))
        assertTrue(output.contains("line-40"))
        assertFalse(output.contains("line-41"))
        assertFalse(output.contains(""""content""""))
        assertTrue(output.contains(""""sentToModel":false"""))
    }

    @Test
    fun executeRejectsNonContentUriBeforeReading() = runTest {
        val reader = RecordingAuthorizedFileReader()
        val error = assertInvalidArguments {
            FileReadTool(reader).execute(request("""{"uri":"file:///sdcard/secret.txt"}"""))
        }

        assertEquals("只能读取 Android 文件选择器授权的 content:// URI。", error.message)
        assertEquals(emptyList<AuthorizedFileReadRequest>(), reader.requests)
    }

    private fun request(arguments: String): LocalToolRequest =
        LocalToolRequest(
            conversationId = ConversationId("conversation"),
            toolCall = ToolCall(
                id = ToolCallId("call"),
                name = "file_read",
                arguments = arguments,
            ),
        )

    private fun LocalToolExecution.jsonOutput(): String =
        (output as ToolOutput.Json).value

    private suspend fun assertInvalidArguments(
        block: suspend () -> Unit,
    ): InvalidLocalToolArgumentsException {
        return try {
            block()
            throw AssertionError("Expected InvalidLocalToolArgumentsException")
        } catch (error: InvalidLocalToolArgumentsException) {
            error
        }
    }
}

private class RecordingAuthorizedFileReader(
    private val result: AuthorizedFileReadResult = AuthorizedFileReadResult(
        fileName = "notes.md",
        mimeType = "text/plain",
        sizeBytes = 0,
        content = "",
        truncated = false,
        unsupportedReason = null,
    ),
) : AuthorizedFileReader {
    val requests = mutableListOf<AuthorizedFileReadRequest>()

    override suspend fun read(request: AuthorizedFileReadRequest): AuthorizedFileReadResult {
        requests += request
        return result
    }
}
