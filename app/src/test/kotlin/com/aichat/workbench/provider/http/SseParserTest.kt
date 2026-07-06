package com.aichat.workbench.provider.http

import java.io.ByteArrayInputStream
import kotlin.test.assertFailsWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {
    @Test
    fun parseSse_readsDataEvents() {
        val input = """
            event: message
            data: hello
            data: world

        """.trimIndent().byteInputStream()

        val event = parseSse(input).single()

        assertEquals("message", event.event)
        assertEquals("hello\nworld", event.data)
    }

    @Test
    fun parseSse_rejectsLineBeforeUnboundedReadLineAllocation() {
        val input = ByteArrayInputStream("data: ${"x".repeat(1024 * 1024 + 1)}".toByteArray())

        val error = assertFailsWith<SseParseException> {
            parseSse(input).toList()
        }

        assertTrue(error.message.orEmpty().contains("SSE line exceeds maximum length"))
    }
}
