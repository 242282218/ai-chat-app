package com.aichat.workbench.ui.component

import com.aichat.workbench.domain.model.ToolPermissionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolCallPanelTest {
    @Test
    fun inferredDisplayNameUsesCanonicalToolAliases() {
        assertEquals("本机时间", "TIME".inferredDisplayName())
        assertEquals("本地 JavaScript", "local-js".inferredDisplayName())
        assertEquals("读取授权文件", "read_file".inferredDisplayName())
        assertEquals("Provider 连接测试", "provider_test".inferredDisplayName())
        assertEquals("图片发送给模型", "image-upload".inferredDisplayName())
    }

    @Test
    fun inferredPermissionLevelUsesToolRiskDefaults() {
        assertEquals(ToolPermissionLevel.ReadOnly, "code_diff_preview".inferredPermissionLevel())
        assertEquals(ToolPermissionLevel.Network, "web_search_local".inferredPermissionLevel())
        assertEquals(ToolPermissionLevel.Execute, "code-sandbox".inferredPermissionLevel())
        assertEquals(ToolPermissionLevel.HighRisk, "javascript".inferredPermissionLevel())
        assertEquals(ToolPermissionLevel.HighRisk, "image_upload_to_model".inferredPermissionLevel())
        assertNull("custom_tool".inferredPermissionLevel())
    }

    @Test
    fun toolDebugBundleUsesCanonicalNameAndIncludesArgumentsAndResult() {
        val bundle = toolDebugBundle(
            toolName = "local-js",
            arguments = """{"code":"bad()"}""",
            result = """{"code":"tool_unavailable","message":"JS unavailable"}""",
            isError = true,
        )

        assertEquals(
            """
            工具：local_js
            原始工具：local-js
            权限：未知风险
            状态：失败
            参数：
            {"code":"bad()"}
            结果：
            {"code":"tool_unavailable","message":"JS unavailable"}
            """.trimIndent(),
            bundle,
        )
    }

    @Test
    fun toolDebugBundleIncludesDisplayNameAndPermissionWhenAvailable() {
        val bundle = toolDebugBundle(
            toolName = "provider_test",
            displayName = "Provider 连接测试",
            permissionLevel = ToolPermissionLevel.Network,
            arguments = """{"providerId":"p1"}""",
            result = """{"ok":false,"statusCode":401}""",
            isError = true,
        )

        assertEquals(
            """
            工具：provider_connection_test
            原始工具：provider_test
            显示名：Provider 连接测试
            权限：联网
            状态：失败
            参数：
            {"providerId":"p1"}
            结果：
            {"ok":false,"statusCode":401}
            """.trimIndent(),
            bundle,
        )
    }

    @Test
    fun toolDebugBundleCanUseNonFailureTerminalStatus() {
        val bundle = toolDebugBundle(
            toolName = "local-js",
            permissionLevel = ToolPermissionLevel.HighRisk,
            arguments = """{"code":"return 1"}""",
            result = """{"code":"tool_denied","message":"用户拒绝执行工具。"}""",
            isError = true,
            statusLabel = "已拒绝",
        )

        assertEquals(
            """
            工具：local_js
            原始工具：local-js
            权限：高风险
            状态：已拒绝
            参数：
            {"code":"return 1"}
            结果：
            {"code":"tool_denied","message":"用户拒绝执行工具。"}
            """.trimIndent(),
            bundle,
        )
    }

    @Test
    fun toolPlanBundleIncludesPlanMetadataWithoutResult() {
        val bundle = toolPlanBundle(
            toolName = "local-web-search",
            displayName = "联网搜索",
            permissionLevel = ToolPermissionLevel.Network,
            arguments = "",
        )

        assertEquals(
            """
            工具计划：web_search_local
            原始工具：local-web-search
            显示名：联网搜索
            权限：联网
            参数：
            {}
            """.trimIndent(),
            bundle,
        )
    }

    @Test
    fun formatElapsedSecondsUsesCompactMinuteLabel() {
        assertEquals("0s", 0.formatElapsedSeconds())
        assertEquals("59s", 59.formatElapsedSeconds())
        assertEquals("1m 0s", 60.formatElapsedSeconds())
        assertEquals("2m 5s", 125.formatElapsedSeconds())
    }

    @Test
    fun copyableToolArgumentsFallsBackToEmptyJsonObject() {
        assertEquals("{}", "".copyableToolArguments())
        assertEquals("""{"query":"AI"}""", """{"query":"AI"}""".copyableToolArguments())
    }

    @Test
    fun toolCallPanelOutcomeKeepsLegacyBooleansCompatible() {
        assertEquals(
            ToolCallPanelOutcome.Pending,
            toolCallPanelOutcome(result = null, isError = false, isPending = true, isPlanOnly = false),
        )
        assertEquals(
            ToolCallPanelOutcome.Failed,
            toolCallPanelOutcome(result = "boom", isError = true, isPending = false, isPlanOnly = false),
        )
        assertEquals(
            ToolCallPanelOutcome.Completed,
            toolCallPanelOutcome(result = "ok", isError = false, isPending = false, isPlanOnly = false),
        )
        assertEquals(
            ToolCallPanelOutcome.Planned,
            toolCallPanelOutcome(result = null, isError = false, isPending = false, isPlanOnly = true),
        )
        assertEquals(
            ToolCallPanelOutcome.Streaming,
            toolCallPanelOutcome(
                result = null,
                isError = false,
                isPending = false,
                isPlanOnly = false,
                isStreaming = true,
            ),
        )
        assertEquals(
            ToolCallPanelOutcome.Running,
            toolCallPanelOutcome(result = null, isError = false, isPending = false, isPlanOnly = false),
        )
    }
}
