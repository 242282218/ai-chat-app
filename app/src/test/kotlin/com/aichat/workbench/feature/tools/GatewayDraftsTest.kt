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
            GatewayUrlStatus(label = "HTTP 网关", isValid = true, isWarning = true),
            "HTTP://localhost:8080".gatewayUrlStatus(),
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
            gatewayApiTokenDraft = "token",
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
            gatewayApiTokenDraft = "token",
            sandboxCode = "print(1)",
            remoteTools = listOf(tool("code_sandbox", ToolPermissionLevel.Execute)),
        )

        assertFalse(base.canRunSandbox())
        assertTrue(base.copy(gatewayBaseUrlDraft = "http://127.0.0.1:8080").canRunSandbox())
    }

    @Test
    fun toolWorkbenchActionsAreDisabledWhileLoading() {
        val base = ToolsUiState(
            gatewayEnabled = true,
            gatewayBaseUrlDraft = "https://gateway.example.com",
            gatewayApiTokenDraft = "token",
            searchQuery = "release notes",
            sandboxCode = "print(1)",
            remoteTools = listOf(
                tool("web_search", ToolPermissionLevel.Network),
                tool("code_sandbox", ToolPermissionLevel.Execute),
            ),
        )

        assertTrue(base.canSearch())
        assertTrue(base.canRunSandbox())
        assertFalse(base.copy(isLoading = true).canSearch())
        assertFalse(base.copy(isLoading = true).canRunSandbox())
    }

    @Test
    fun gatewaySettingsCanBeSavedDisabledWithInvalidUrl() {
        val disabledInvalid = ToolsUiState(
            gatewayEnabled = false,
            gatewayBaseUrlDraft = "gateway.local",
        )

        assertTrue(disabledInvalid.canSaveGatewaySettings())
        assertFalse(disabledInvalid.copy(gatewayEnabled = true).canSaveGatewaySettings())
        assertFalse(disabledInvalid.copy(isLoading = true).canSaveGatewaySettings())
    }

    @Test
    fun gatewayHealthCanRunDisabledButManifestRequiresEnabled() {
        val enabledValid = ToolsUiState(
            gatewayEnabled = true,
            gatewayBaseUrlDraft = "https://gateway.example.com",
        )

        assertTrue(enabledValid.canCheckGatewayHealth())
        assertTrue(enabledValid.canFetchGatewayManifest())
        assertTrue(enabledValid.copy(gatewayEnabled = false).canCheckGatewayHealth())
        assertFalse(enabledValid.copy(gatewayEnabled = false).canFetchGatewayManifest())
        assertFalse(enabledValid.copy(gatewayBaseUrlDraft = "gateway.local").canCheckGatewayHealth())
        assertFalse(enabledValid.copy(gatewayBaseUrlDraft = "gateway.local").canFetchGatewayManifest())
        assertFalse(enabledValid.copy(isLoading = true).canCheckGatewayHealth())
        assertFalse(enabledValid.copy(isLoading = true).canFetchGatewayManifest())
    }

    @Test
    fun searchActionStatusExplainsPrimaryMissingRequirement() {
        val ready = ToolsUiState(
            gatewayEnabled = true,
            gatewayBaseUrlDraft = "https://gateway.example.com",
            gatewayApiTokenDraft = "token",
            searchQuery = "release notes",
            remoteTools = listOf(tool("web_search", ToolPermissionLevel.Network)),
        )

        assertEquals(
            GatewayActionStatus(label = "就绪", isReady = true),
            ready.searchActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "处理中", isReady = false, isBusy = true),
            ready.copy(isLoading = true).searchActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "网关关闭", isReady = false),
            ready.copy(gatewayEnabled = false).searchActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "URL 无效", isReady = false),
            ready.copy(gatewayBaseUrlDraft = "gateway.local").searchActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "需要 Token", isReady = false),
            ready.copy(gatewayApiTokenDraft = "").searchActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "需要工具清单", isReady = false),
            ready.copy(remoteTools = emptyList()).searchActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "需要关键词", isReady = false),
            ready.copy(searchQuery = "").searchActionStatus(),
        )
    }

    @Test
    fun sandboxActionStatusExplainsPrimaryMissingRequirement() {
        val ready = ToolsUiState(
            gatewayEnabled = true,
            gatewayBaseUrlDraft = "https://gateway.example.com",
            gatewayApiTokenDraft = "token",
            sandboxCode = "print(1)",
            remoteTools = listOf(tool("code_sandbox", ToolPermissionLevel.Execute)),
        )

        assertEquals(
            GatewayActionStatus(label = "就绪", isReady = true),
            ready.sandboxActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "处理中", isReady = false, isBusy = true),
            ready.copy(isLoading = true).sandboxActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "网关关闭", isReady = false),
            ready.copy(gatewayEnabled = false).sandboxActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "URL 无效", isReady = false),
            ready.copy(gatewayBaseUrlDraft = "ftp://gateway.example.com").sandboxActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "需要 Token", isReady = false),
            ready.copy(gatewayApiTokenDraft = "").sandboxActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "需要工具清单", isReady = false),
            ready.copy(remoteTools = emptyList()).sandboxActionStatus(),
        )
        assertEquals(
            GatewayActionStatus(label = "需要代码", isReady = false),
            ready.copy(sandboxCode = "").sandboxActionStatus(),
        )
    }

    @Test
    fun remoteGatewayActionsRequireApiToken() {
        val base = ToolsUiState(
            gatewayEnabled = true,
            gatewayBaseUrlDraft = "https://gateway.example.com",
            searchQuery = "release notes",
            sandboxCode = "print(1)",
            remoteTools = listOf(
                tool("web_search", ToolPermissionLevel.Network),
                tool("code_sandbox", ToolPermissionLevel.Execute),
            ),
        )

        assertFalse(base.canSearch())
        assertFalse(base.canRunSandbox())
        assertTrue(base.copy(gatewayApiTokenDraft = "token").canSearch())
        assertTrue(base.copy(gatewayApiTokenDraft = "token").canRunSandbox())
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
