package com.aichat.workbench.provider.http

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

data class SseEvent(
    val event: String?,
    val data: String,
)

fun parseSse(input: InputStream): Sequence<SseEvent> =
    sequence {
        val reader = BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8))
        var eventName: String? = null
        val data = StringBuilder()

        while (true) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> {
                    if (data.isNotEmpty()) {
                        yield(SseEvent(eventName, data.toString().trimEnd('\n')))
                    }
                    eventName = null
                    data.clear()
                }
                line.startsWith("event:") -> {
                    eventName = line.removePrefix("event:").trim()
                }
                line.startsWith("data:") -> {
                    data.append(line.removePrefix("data:").trimStart()).append('\n')
                }
            }
        }

        if (data.isNotEmpty()) {
            yield(SseEvent(eventName, data.toString().trimEnd('\n')))
        }
    }
