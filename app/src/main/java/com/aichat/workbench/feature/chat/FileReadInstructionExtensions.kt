package com.aichat.workbench.feature.chat

/**
 * Extension functions for handling file read instruction text manipulation.
 * These functions manage the special file_read tool instruction format
 * that is appended to user input when attaching files.
 */

internal fun String.appendFileReadInstruction(uri: String): String {
    val currentInput = removeFileReadInstruction().trimEnd()
    val instruction = """
        请读取我刚通过系统文件选择器授权的文件，并基于文件内容继续处理。
        工具：file_read
        参数：{"uri":${uri.jsonStringLiteral()},"maxBytes":65536}
    """.trimIndent()
    return if (currentInput.isBlank()) instruction else "$currentInput\n\n$instruction"
}

internal fun String.hasFileReadInstruction(): Boolean =
    FILE_READ_INSTRUCTION_REGEX.containsMatchIn(this)

internal fun String.removeFileReadInstruction(): String {
    if (!hasFileReadInstruction()) return this
    return FILE_READ_INSTRUCTION_REGEX
        .replace(this, "\n\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private const val FILE_READ_INSTRUCTION_PREFIX =
    "请读取我刚通过系统文件选择器授权的文件，并基于文件内容继续处理。\n工具：file_read\n参数："

private val FILE_READ_INSTRUCTION_REGEX = Regex(
    "(?:^|\\n\\n)" +
        Regex.escape(FILE_READ_INSTRUCTION_PREFIX) +
        "\\{\"uri\":\"(?:\\\\.|[^\"\\\\])*\",\"maxBytes\":65536}\\s*(?=\\n\\n|$)",
)

private fun String.jsonStringLiteral(): String =
    buildString {
        append('"')
        this@jsonStringLiteral.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
