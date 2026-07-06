package com.aichat.workbench.provider.http

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class SseEvent(
    val event: String?,
    val data: String,
)

private const val MAX_LINE_LENGTH = 1024 * 1024 // 1MB per line
private const val MAX_EVENT_SIZE = 10 * 1024 * 1024 // 10MB per event data
private const val MAX_EVENTS = 10000 // Maximum number of events in a single stream

fun parseSse(input: InputStream): Sequence<SseEvent> =
    sequence {
        BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            var eventName: String? = null
            val data = StringBuilder()
            var eventCount = 0

            while (true) {
                val line = reader.readBoundedLine() ?: break

                when {
                    line.isEmpty() -> {
                        if (data.isNotEmpty()) {
                            eventCount++
                            if (eventCount > MAX_EVENTS) {
                                throw SseParseException("SSE stream exceeded maximum event count: $MAX_EVENTS")
                            }
                            yield(SseEvent(eventName, data.toString().trimEnd('\n')))
                        }
                        eventName = null
                        data.clear()
                    }
                    line.startsWith("event:") -> {
                        eventName = line.removePrefix("event:").trim()
                    }
                    line.startsWith("data:") -> {
                        val newData = line.removePrefix("data:").trimStart()
                        // Guard against excessively large event data
                        if (data.length + newData.length > MAX_EVENT_SIZE) {
                            throw SseParseException("SSE event data exceeds maximum size: $MAX_EVENT_SIZE bytes")
                        }
                        data.append(newData).append('\n')
                    }
                }
            }

            if (data.isNotEmpty()) {
                eventCount++
                if (eventCount > MAX_EVENTS) {
                    throw SseParseException("SSE stream exceeded maximum event count: $MAX_EVENTS")
                }
                yield(SseEvent(eventName, data.toString().trimEnd('\n')))
            }
        }
    }

private fun BufferedReader.readBoundedLine(): String? {
    val line = StringBuilder()
    while (true) {
        val char = read()
        when {
            char == -1 -> return line.takeIf { it.isNotEmpty() }?.toString()
            char == '\n'.code -> return line.toString()
            char == '\r'.code -> Unit
            else -> {
                line.append(char.toChar())
                if (line.length > MAX_LINE_LENGTH) {
                    throw SseParseException("SSE line exceeds maximum length: ${line.length} bytes")
                }
            }
        }
    }
}

class SseParseException(message: String) : RuntimeException(message)
