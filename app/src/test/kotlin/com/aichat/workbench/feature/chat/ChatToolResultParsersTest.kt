package com.aichat.workbench.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatToolResultParsersTest {
    @Test
    fun extractImagePromptReadsImageGenerationPrompt() {
        assertEquals(
            "Draw a cat",
            extractImagePrompt("""{"prompt":"Draw a cat","count":1}"""),
        )
    }

    @Test
    fun imageResultActionStateFallsBackToMessageContentForPromptActions() {
        val state = imageResultActionState(
            toolResult = """{"status":"completed"}""",
            fallbackContent = "Draw from message content",
            imagePath = null,
        )

        assertEquals("Draw from message content", state.prompt)
        assertEquals(null, state.imagePath)
        assertEquals(true, state.hasPromptActions)
        assertEquals(false, state.hasFileActions)
        assertEquals(true, state.hasAnyActions)
    }

    @Test
    fun imageResultActionStateKeepsFileActionsWithoutPrompt() {
        val state = imageResultActionState(
            toolResult = """{"status":"completed"}""",
            fallbackContent = "",
            imagePath = "D:/tmp/generated.png",
        )

        assertEquals(null, state.prompt)
        assertEquals("D:/tmp/generated.png", state.imagePath)
        assertEquals(false, state.hasPromptActions)
        assertEquals(true, state.hasFileActions)
        assertEquals(true, state.hasAnyActions)
    }

    @Test
    fun imageResultActionStateHidesWhenNoPromptOrLocalFile() {
        val state = imageResultActionState(
            toolResult = "{",
            fallbackContent = " ",
            imagePath = null,
        )

        assertEquals(null, state.prompt)
        assertEquals(null, state.imagePath)
        assertEquals(false, state.hasPromptActions)
        assertEquals(false, state.hasFileActions)
        assertEquals(false, state.hasAnyActions)
    }

    @Test
    fun extractSearchCitationsReadsSearchToolOutput() {
        val citations = extractSearchCitations(
            toolName = "web-search-local",
            toolResult = """
                {
                  "query": "AI news",
                  "results": [
                    {
                      "title": "AI News",
                      "summary": "A sourced update",
                      "url": "https://example.com/ai-news",
                      "source": "example.com",
                      "publishedAt": "2026-06-01T00:00:00Z"
                    },
                    {
                      "title": "",
                      "summary": "No source label",
                      "url": "https://wire.example.com/brief",
                      "source": ""
                    },
                    {
                      "title": "Ignored",
                      "url": "",
                      "source": "empty.example"
                    }
                  ]
                }
            """.trimIndent(),
        )

        assertEquals(2, citations.size)
        assertEquals("AI News", citations[0].title)
        assertEquals("A sourced update", citations[0].summary)
        assertEquals("https://example.com/ai-news", citations[0].url)
        assertEquals("example.com", citations[0].source)
        assertEquals("2026-06-01T00:00:00Z", citations[0].publishedAt)
        assertEquals("https://wire.example.com/brief", citations[1].title)
        assertEquals("No source label", citations[1].summary)
        assertEquals("https://wire.example.com/brief", citations[1].source)
    }

    @Test
    fun extractSearchCitationsIgnoresNonSearchAndInvalidJson() {
        assertTrue(extractSearchCitations("local_js", """{"results":[]}""").isEmpty())
        assertTrue(extractSearchCitations("web_search_local", "{").isEmpty())
        assertTrue(extractSearchCitations("web_search_local", null).isEmpty())
    }

    @Test
    fun extractSearchResultSummaryReadsEmptySearchAndRecoveryReason() {
        val result = extractSearchResultSummary(
            toolName = "web-search-local",
            toolResult = """{"query":"rare term","results":[]}""",
        )

        requireNotNull(result)
        assertEquals("rare term", result.query)
        assertEquals(0, result.resultCount)
        assertTrue(result.recoveryReason().contains("换关键词"))
        assertTrue(result.recoveryReason().contains("搜索 Provider"))
    }

    @Test
    fun extractSearchResultSummaryIgnoresErrorsAndInvalidInput() {
        assertEquals(
            null,
            extractSearchResultSummary(
                toolName = "web_search_local",
                toolResult = """{"code":"local_search_http_401","message":"Unauthorized"}""",
            ),
        )
        assertEquals(null, extractSearchResultSummary("local_js", """{"query":"x","results":[]}"""))
        assertEquals(null, extractSearchResultSummary("web_search_local", """{"message":"not a result"}"""))
        assertEquals(null, extractSearchResultSummary("web_search_local", "{"))
    }

    @Test
    fun extractLocalJsResultReadsExecutionSummary() {
        val result = extractLocalJsResult(
            toolName = "local-javascript",
            toolResult = """
                {
                  "language": "javascript",
                  "output": "{\"ok\":true}",
                  "durationMs": 42,
                  "timedOut": false,
                  "truncated": true
                }
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals("""{"ok":true}""", result.output)
        assertEquals(42, result.durationMs)
        assertEquals(false, result.timedOut)
        assertEquals(true, result.truncated)
    }

    @Test
    fun extractLocalJsResultIgnoresInvalidInput() {
        assertEquals(null, extractLocalJsResult("file_read", """{"output":"x"}"""))
        assertEquals(null, extractLocalJsResult("local_js", "{"))
        assertEquals(null, extractLocalJsResult("local_js", null))
    }

    @Test
    fun extractFileReadResultReadsFileSummary() {
        val result = extractFileReadResult(
            toolName = "read-file",
            toolResult = """
                {
                  "uri": "content://doc/1",
                  "fileName": "notes.md",
                  "mimeType": "text/markdown",
                  "sizeBytes": 4096,
                  "status": "truncated",
                  "preview": "# Notes\nFirst line",
                  "truncated": true,
                  "unsupportedReason": null,
                  "sentToModel": true
                }
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals("notes.md", result.fileName)
        assertEquals("text/markdown", result.mimeType)
        assertEquals(4096L, result.sizeBytes)
        assertEquals("truncated", result.status)
        assertEquals("# Notes\nFirst line", result.preview)
        assertEquals(true, result.truncated)
        assertEquals(null, result.unsupportedReason)
        assertEquals(true, result.sentToModel)
        assertEquals("已发送模型", result.modelContextLabel())
        assertEquals(FileReadModelContextTone.Success, result.modelContextTone())
    }

    @Test
    fun extractFileReadResultFallsBackToUriAndIgnoresInvalidInput() {
        val result = extractFileReadResult(
            toolName = "file_read",
            toolResult = """{"uri":"content://doc/2","fileName":"","status":"unsupported","unsupportedReason":"PDF 暂不支持"}""",
        )

        requireNotNull(result)
        assertEquals("content://doc/2", result.fileName)
        assertEquals("unsupported", result.status)
        assertEquals("PDF 暂不支持", result.unsupportedReason)
        assertEquals(false, result.sentToModel)
        assertEquals("未发送内容", result.modelContextLabel())
        assertEquals(FileReadModelContextTone.Neutral, result.modelContextTone())
        assertEquals(null, extractFileReadResult("local_js", """{"uri":"content://doc/2"}"""))
        assertEquals(null, extractFileReadResult("file_read", "{"))
    }

    @Test
    fun fileReadModelContextLabelMarksPreviewOnlyWithoutFullContent() {
        val result = extractFileReadResult(
            toolName = "file_read",
            toolResult = """
                {
                  "uri": "content://doc/3",
                  "fileName": "notes.md",
                  "status": "completed",
                  "preview": "first lines only",
                  "truncated": false,
                  "sentToModel": false
                }
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals("仅预览，未发送全文", result.modelContextLabel())
        assertEquals(FileReadModelContextTone.Warning, result.modelContextTone())
    }

    @Test
    fun extractTextTransformResultReadsOutputAndMatches() {
        val formatted = extractTextTransformResult(
            toolName = "text-transform",
            toolResult = """
                {
                  "operation": "json_format",
                  "inputLength": 13,
                  "output": "{\n  \"ok\": true\n}",
                  "matches": [],
                  "validJson": true,
                  "truncated": false
                }
            """.trimIndent(),
        )
        val preview = extractTextTransformResult(
            toolName = "text_transform",
            toolResult = """
                {
                  "operation": "regex_preview",
                  "inputLength": 18,
                  "matches": ["A1", "B2"],
                  "truncated": true
                }
            """.trimIndent(),
        )

        requireNotNull(formatted)
        assertEquals("json_format", formatted.operation)
        assertEquals(13, formatted.inputLength)
        assertEquals("{\n  \"ok\": true\n}", formatted.output)
        assertEquals(true, formatted.validJson)
        requireNotNull(preview)
        assertEquals(listOf("A1", "B2"), preview.matches)
        assertEquals(true, preview.truncated)
    }

    @Test
    fun extractTextTransformResultIgnoresInvalidInput() {
        assertEquals(null, extractTextTransformResult("local_js", """{"operation":"trim"}"""))
        assertEquals(null, extractTextTransformResult("text_transform", "{"))
    }

    @Test
    fun extractCodeDiffPreviewResultReadsDiffSummary() {
        val result = extractCodeDiffPreviewResult(
            toolName = "code-diff-preview",
            toolResult = """
                {
                  "fileName": "Main.kt",
                  "additions": 2,
                  "deletions": 1,
                  "diff": "--- Main.kt\n+++ Main.kt\n@@ -1 +1 @@\n-old\n+new"
                }
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals("Main.kt", result.fileName)
        assertEquals(2, result.additions)
        assertEquals(1, result.deletions)
        assertTrue(result.diff.contains("+new"))
    }

    @Test
    fun extractCodeDiffPreviewResultIgnoresInvalidInput() {
        assertEquals(null, extractCodeDiffPreviewResult("text_transform", """{"diff":"x"}"""))
        assertEquals(null, extractCodeDiffPreviewResult("code_diff_preview", "{"))
    }

    @Test
    fun extractProviderConnectionTestResultReadsConnectionSummary() {
        val result = extractProviderConnectionTestResult(
            toolName = "provider-connection-test",
            toolResult = """
                {
                  "providerId": "provider-1",
                  "providerName": "NewApi",
                  "providerType": "openai-compatible",
                  "enabled": true,
                  "defaultModel": "gpt-4.1-mini",
                  "ok": true,
                  "statusCode": 200,
                  "message": "连接成功"
                }
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals("NewApi", result.providerName)
        assertEquals("openai-compatible", result.providerType)
        assertEquals(true, result.enabled)
        assertEquals("gpt-4.1-mini", result.defaultModel)
        assertEquals(true, result.ok)
        assertEquals(200, result.statusCode)
        assertEquals("连接成功", result.message)
    }

    @Test
    fun providerConnectionDiagnosticTextIncludesActionableFields() {
        val result = ProviderConnectionTestResultSummary(
            providerName = "NewApi",
            providerType = "openai-compatible",
            enabled = true,
            defaultModel = "gpt-test",
            ok = false,
            statusCode = 401,
            message = "Unauthorized",
        )

        assertEquals(
            """
            Provider：NewApi
            类型：openai-compatible
            启用：是
            模型：gpt-test
            结果：连接失败
            HTTP：401
            消息：Unauthorized
            """.trimIndent(),
            result.diagnosticText(),
        )
    }

    @Test
    fun extractProviderConnectionTestResultFallsBackAndIgnoresInvalidInput() {
        val result = extractProviderConnectionTestResult(
            toolName = "provider_connection_test",
            toolResult = """{"providerId":"provider-2","providerName":"","ok":false,"message":""}""",
        )

        requireNotNull(result)
        assertEquals("provider-2", result.providerName)
        assertEquals(false, result.ok)
        assertEquals("连接失败。", result.message)
        assertEquals(null, extractProviderConnectionTestResult("local_js", """{"ok":true}"""))
        assertEquals(null, extractProviderConnectionTestResult("provider_connection_test", "{"))
    }

    @Test
    fun extractToolErrorResultReadsRetryableHttpFailure() {
        val result = extractToolErrorResult(
            toolResult = """
                {
                  "code": "local_search_http_429",
                  "message": "Rate limit exceeded",
                  "statusCode": 429,
                  "retryable": true
                }
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals("local_search_http_429", result.code)
        assertEquals("Rate limit exceeded", result.message)
        assertEquals(429, result.statusCode)
        assertEquals(true, result.retryable)
        assertEquals("请求被限流，稍后重试，或切换搜索 Provider。", result.recoveryHint())
        assertEquals(
            """
            错误码：local_search_http_429
            HTTP：429
            可重试：是
            建议：请求被限流，稍后重试，或切换搜索 Provider。
            消息：Rate limit exceeded
            """.trimIndent(),
            result.diagnosticText(),
        )
    }

    @Test
    fun toolErrorRecoveryHintUsesToolSpecificProviderGuidance() {
        val rateLimited = ToolErrorResultSummary(
            code = "provider_http_429",
            message = "Rate limit exceeded",
            statusCode = 429,
            retryable = true,
        )
        val unauthorized = ToolErrorResultSummary(
            code = "provider_http_401",
            message = "Unauthorized",
            statusCode = 401,
            retryable = false,
        )

        assertEquals(
            "图片生成请求被限流，稍后重试，或切换图片模型/Provider。",
            rateLimited.recoveryHint("image_generation"),
        )
        assertEquals(
            "搜索请求被限流，稍后重试，或切换搜索 Provider。",
            rateLimited.recoveryHint("web_search_local"),
        )
        assertEquals(
            "检查 Provider API Key、Base URL 和模型配置后重试。",
            unauthorized.recoveryHint("provider_connection_test"),
        )
        assertEquals(
            "请通过聊天输入栏选择图片，并在发送前确认图片会作为多模态内容发送给当前模型。",
            ToolErrorResultSummary(
                code = "image_upload_requires_chat_confirmation",
                message = "Needs confirmation",
                statusCode = null,
                retryable = false,
            ).recoveryHint("image_upload_to_model"),
        )
        assertEquals(
            "请打开工具中心检查工具是否启用、名称是否正确，或改用当前 App 支持的本地工具。",
            ToolErrorResultSummary(
                code = "tool_disabled",
                message = "工具已禁用。",
                statusCode = null,
                retryable = false,
            ).recoveryHint("time"),
        )
        assertEquals(
            "工具已取消，参数和日志已保留；如需继续，请调整参数后重新发起。",
            ToolErrorResultSummary(
                code = "tool_cancelled",
                message = "工具执行已取消。",
                statusCode = null,
                retryable = null,
            ).recoveryHint("local_js"),
        )
        assertEquals(
            "工具已被拒绝执行；如需继续，请确认风险和参数后重新发起。",
            ToolErrorResultSummary(
                code = "tool_denied",
                message = "用户拒绝执行工具。",
                statusCode = null,
                retryable = null,
            ).recoveryHint("file_read"),
        )
        assertTrue(rateLimited.diagnosticText("image_generation").contains("切换图片模型/Provider"))
    }

    @Test
    fun extractToolErrorResultExplainsAuthFailureAndIgnoresInvalidInput() {
        val result = extractToolErrorResult(
            toolResult = """
                {
                  "code": "local_search_http_401",
                  "message": "Unauthorized",
                  "statusCode": 401,
                  "retryable": false
                }
            """.trimIndent(),
        )

        requireNotNull(result)
        assertEquals("检查 API Key、Provider 配置或网关鉴权后重试。", result.recoveryHint())
        assertEquals(null, extractToolErrorResult("""{"message":"missing code"}"""))
        assertEquals(null, extractToolErrorResult("""{"results":[]}"""))
        assertEquals(null, extractToolErrorResult("{"))
        assertEquals(null, extractToolErrorResult(null))
    }
}
