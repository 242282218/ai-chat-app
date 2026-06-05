package com.aichat.workbench.feature.chat

import com.aichat.workbench.ui.markdown.CodeArtifact
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCodeArtifactActionsTest {
    @Test
    fun diffPromptAsksForCodeDiffPreviewArgumentsWithoutWritingFiles() {
        val prompt = CodeArtifact(
            language = "kotlin",
            content = "fun answer() = 41",
        ).diffPrompt()

        assertTrue(prompt.contains("code_diff_preview"))
        assertTrue(prompt.contains("按参数模板填充 modified"))
        assertTrue(prompt.contains("只展示 diff，不写入文件"))
        assertTrue(prompt.contains("语言：kotlin"))
        assertTrue(prompt.contains(""""fileName":"snippet.kt""""))
        assertTrue(prompt.contains(""""original":"fun answer() = 41""""))
        assertTrue(prompt.contains(""""modified":"fun answer() = 41""""))
        assertTrue(prompt.contains("```kotlin"))
        assertTrue(prompt.contains("fun answer() = 41"))
    }

    @Test
    fun diffPromptEscapesCodeForJsonTemplate() {
        val prompt = CodeArtifact(
            language = "javascript",
            content = "const answer = \"old\"\nconsole.log(answer)",
        ).diffPrompt()

        assertTrue(prompt.contains(""""fileName":"snippet.js""""))
        assertTrue(prompt.contains(""""original":"const answer = \"old\"\nconsole.log(answer)""""))
        assertTrue(prompt.contains(""""modified":"const answer = \"old\"\nconsole.log(answer)""""))
    }
}
