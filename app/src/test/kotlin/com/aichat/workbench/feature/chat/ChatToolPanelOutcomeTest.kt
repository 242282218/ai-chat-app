package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.ui.component.ToolCallPanelOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatToolPanelOutcomeTest {
    @Test
    fun cancelledToolMessageWithDeniedResultStaysDenied() {
        assertEquals(
            ToolCallPanelOutcome.Denied,
            toolPanelOutcomeForMessage(
                status = MessageStatus.Cancelled,
                isPending = false,
                toolResultText = """{"code":"tool_denied","message":"用户拒绝执行工具。"}""",
            ),
        )
    }

    @Test
    fun cancelledToolMessageWithoutDeniedResultStaysCancelled() {
        assertEquals(
            ToolCallPanelOutcome.Cancelled,
            toolPanelOutcomeForMessage(
                status = MessageStatus.Cancelled,
                isPending = false,
                toolResultText = """{"code":"tool_cancelled","message":"工具执行已取消。"}""",
            ),
        )
    }

    @Test
    fun streamingToolMessageStaysStreamingUntilResultArrives() {
        assertEquals(
            ToolCallPanelOutcome.Streaming,
            toolPanelOutcomeForMessage(
                status = MessageStatus.Streaming,
                isPending = false,
                toolResultText = null,
            ),
        )
    }

    @Test
    fun searchToolErrorsOfferToolSettingsEntry() {
        assertTrue(
            "web-search-local".shouldOfferToolSettingsForError(
                ToolErrorResultSummary(
                    code = "local_search_key_required",
                    message = "搜索 API Key 未配置，请在工具页保存搜索 Provider Key。",
                    statusCode = null,
                    retryable = false,
                ),
            ),
        )
        assertTrue(
            "web_search_local".shouldOfferToolSettingsForError(
                ToolErrorResultSummary(
                    code = "local_search_http_401",
                    message = "Unauthorized",
                    statusCode = 401,
                    retryable = false,
                ),
            ),
        )
    }

    @Test
    fun toolConfigurationErrorsOfferToolSettingsEntry() {
        listOf(
            "tool_disabled",
            "unknown_tool",
            "hosted_tool_not_executable_locally",
        ).forEach { code ->
            assertTrue(
                "custom_tool".shouldOfferToolSettingsForError(
                    ToolErrorResultSummary(
                        code = code,
                        message = "Tool configuration problem",
                        statusCode = null,
                        retryable = false,
                    ),
                ),
            )
        }
    }

    @Test
    fun nonSearchToolErrorsDoNotOfferToolSettingsEntry() {
        assertFalse(
            "image_generation".shouldOfferToolSettingsForError(
                ToolErrorResultSummary(
                    code = "provider_http_429",
                    message = "too many image requests",
                    statusCode = 429,
                    retryable = true,
                ),
            ),
        )
    }

    @Test
    fun providerBackedToolErrorsOfferProviderSettingsEntry() {
        assertTrue(
            "image_generation".shouldOfferProviderSettingsForError(
                ToolErrorResultSummary(
                    code = "provider_http_429",
                    message = "too many image requests",
                    statusCode = 429,
                    retryable = true,
                ),
            ),
        )
        assertTrue(
            "provider_connection_test".shouldOfferProviderSettingsForError(
                ToolErrorResultSummary(
                    code = "provider_http_401",
                    message = "API Key invalid",
                    statusCode = 401,
                    retryable = false,
                ),
            ),
        )
    }

    @Test
    fun nonProviderBackedToolErrorsDoNotOfferProviderSettingsEntry() {
        assertFalse(
            "web_search_local".shouldOfferProviderSettingsForError(
                ToolErrorResultSummary(
                    code = "local_search_http_429",
                    message = "Rate limit exceeded",
                    statusCode = 429,
                    retryable = true,
                ),
            ),
        )
    }
}
