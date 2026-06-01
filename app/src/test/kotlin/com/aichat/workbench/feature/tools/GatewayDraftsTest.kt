package com.aichat.workbench.feature.tools

import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayDraftsTest {
    @Test
    fun classifiesGatewayUrls() {
        assertEquals(
            GatewayUrlStatus(label = "需要 URL", isValid = false, isWarning = true),
            "".gatewayUrlStatus(),
        )
        assertEquals(
            GatewayUrlStatus(label = "URL 无效", isValid = false, isWarning = false),
            "gateway.local".gatewayUrlStatus(),
        )
        assertEquals(
            GatewayUrlStatus(label = "HTTP 网关", isValid = true, isWarning = true),
            "http://localhost:8080".gatewayUrlStatus(),
        )
        assertEquals(
            GatewayUrlStatus(label = "URL 有效", isValid = true, isWarning = false),
            "https://gateway.example.com".gatewayUrlStatus(),
        )
    }

    @Test
    fun searchRequiresValidGatewayUrl() {
        val base = ToolsUiState(
            gatewayEnabled = true,
            gatewayBaseUrlDraft = "gateway.local",
            searchQuery = "release notes",
            remoteTools = listOf(tool("web_search", ToolPermissionLevel.Network)),
        )

        assertFalse(base.canSearch())
        assertTrue(base.copy(gatewayBaseUrlDraft = "https://gateway.example.com").canSearch())
    }

    @Test
    fun sandboxRequiresValidGatewayUrl() {
        val base = ToolsUiState(
            gatewayEnabled = true,
            gatewayBaseUrlDraft = "ftp://gateway.example.com",
            sandboxCode = "print(1)",
            remoteTools = listOf(tool("code_sandbox", ToolPermissionLevel.Execute)),
        )

        assertFalse(base.canRunSandbox())
        assertTrue(base.copy(gatewayBaseUrlDraft = "http://127.0.0.1:8080").canRunSandbox())
    }

    private fun tool(
        name: String,
        permissionLevel: ToolPermissionLevel,
    ): ToolDescriptor =
        ToolDescriptor(
            name = name,
            displayName = name,
            description = name,
            permissionLevel = permissionLevel,
            inputSchemaJson = """{"type":"object"}""",
            outputSchemaJson = null,
            timeoutSeconds = null,
            source = ToolSource.Gateway,
        )
}
