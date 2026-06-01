package com.aichat.workbench.feature.provider

import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.ui.component.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDraftsTest {
    @Test
    fun labelsProviderTypesForDisplay() {
        assertEquals("OpenAI", ProviderType.OpenAI.providerTypeLabel())
        assertEquals("兼容 OpenAI", ProviderType.OpenAICompatible.providerTypeLabel())
        assertEquals("OpenRouter", ProviderType.OpenRouter.providerTypeLabel())
        assertEquals("Ollama", ProviderType.Ollama.providerTypeLabel())
    }

    @Test
    fun exposesProviderDefaultsForUserSelection() {
        val ollama = ProviderRegistry.builtInDescriptor(ProviderType.Ollama)
        val openRouter = ProviderRegistry.builtInDescriptor(ProviderType.OpenRouter)

        assertEquals("http://10.0.2.2:11434", ollama?.defaultBaseUrl)
        assertEquals(false, ollama?.requiresApiKey)
        assertEquals("https://openrouter.ai/api/v1", openRouter?.defaultBaseUrl)
        assertEquals(true, openRouter?.requiresApiKey)
    }

    @Test
    fun classifiesProviderUrls() {
        assertEquals(
            ProviderUrlStatus(label = "需要 URL", tone = StatusTone.Warning),
            "".providerUrlStatus(allowHttp = false),
        )
        assertEquals(
            ProviderUrlStatus(label = "URL 有效", tone = StatusTone.Success),
            "https://api.example.com/v1".providerUrlStatus(allowHttp = false),
        )
        assertEquals(
            ProviderUrlStatus(label = "HTTP 已阻止", tone = StatusTone.Critical),
            "http://localhost:11434/v1".providerUrlStatus(allowHttp = false),
        )
        assertEquals(
            ProviderUrlStatus(label = "已允许 HTTP", tone = StatusTone.Warning),
            "http://localhost:11434/v1".providerUrlStatus(allowHttp = true),
        )
        assertEquals(
            ProviderUrlStatus(label = "URL 无效", tone = StatusTone.Critical),
            "localhost:11434/v1".providerUrlStatus(allowHttp = true),
        )
    }

    @Test
    fun classifiesProviderKeys() {
        assertEquals(
            ProviderKeyStatus(label = "已输入 API Key", tone = StatusTone.Success),
            providerKeyStatus(apiKey = "sk-test", hasStoredKey = true),
        )
        assertEquals(
            ProviderKeyStatus(label = "已保存 API Key", tone = StatusTone.Success),
            providerKeyStatus(apiKey = "", hasStoredKey = true),
        )
        assertEquals(
            ProviderKeyStatus(label = "无 API Key", tone = StatusTone.Warning),
            providerKeyStatus(apiKey = "", hasStoredKey = false),
        )
        assertEquals(
            ProviderKeyStatus(label = "无需 API Key", tone = StatusTone.Neutral),
            providerKeyStatus(apiKey = "", hasStoredKey = false, requiresApiKey = false),
        )
    }

    @Test
    fun validatesHeaderLines() {
        assertEquals(
            HeaderStatus(label = "无 Headers", tone = StatusTone.Neutral),
            "".headerStatus(),
        )
        assertEquals(
            HeaderStatus(label = "2 个 Headers", tone = StatusTone.Accent),
            """
            X-Team: mobile
            X-Trace: enabled
            """.trimIndent().headerStatus(),
        )
        assertEquals(
            HeaderStatus(label = "2 个无效 Headers", tone = StatusTone.Critical),
            """
            Missing separator
            Empty:
            X-Trace: enabled
            """.trimIndent().headerStatus(),
        )
    }

    @Test
    fun parsesOnlyValidHeaderLines() {
        val headers = parseHeaderLines(
            """
            X-Team: mobile
            Missing separator
            X-Trace: enabled
            """.trimIndent(),
        )

        assertTrue("X-Team: mobile\nX-Trace: enabled".hasValidHeaderLines())
        assertFalse("X-Team:".hasValidHeaderLines())
        assertEquals(
            mapOf(
                "X-Team" to "mobile",
                "X-Trace" to "enabled",
            ),
            headers,
        )
    }
}
