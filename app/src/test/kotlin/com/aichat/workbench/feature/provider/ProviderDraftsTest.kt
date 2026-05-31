package com.aichat.workbench.feature.provider

import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.ui.component.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDraftsTest {
    @Test
    fun labelsProviderTypesForDisplay() {
        assertEquals("OpenAI", ProviderType.OpenAI.providerTypeLabel())
        assertEquals("Compatible", ProviderType.OpenAICompatible.providerTypeLabel())
    }

    @Test
    fun classifiesProviderUrls() {
        assertEquals(
            ProviderUrlStatus(label = "URL required", tone = StatusTone.Warning),
            "".providerUrlStatus(allowHttp = false),
        )
        assertEquals(
            ProviderUrlStatus(label = "URL valid", tone = StatusTone.Success),
            "https://api.example.com/v1".providerUrlStatus(allowHttp = false),
        )
        assertEquals(
            ProviderUrlStatus(label = "HTTP blocked", tone = StatusTone.Critical),
            "http://localhost:11434/v1".providerUrlStatus(allowHttp = false),
        )
        assertEquals(
            ProviderUrlStatus(label = "HTTP allowed", tone = StatusTone.Warning),
            "http://localhost:11434/v1".providerUrlStatus(allowHttp = true),
        )
        assertEquals(
            ProviderUrlStatus(label = "URL invalid", tone = StatusTone.Critical),
            "localhost:11434/v1".providerUrlStatus(allowHttp = true),
        )
    }

    @Test
    fun classifiesProviderKeys() {
        assertEquals(
            ProviderKeyStatus(label = "Key entered", tone = StatusTone.Success),
            providerKeyStatus(apiKey = "sk-test", hasStoredKey = true),
        )
        assertEquals(
            ProviderKeyStatus(label = "Key stored", tone = StatusTone.Success),
            providerKeyStatus(apiKey = "", hasStoredKey = true),
        )
        assertEquals(
            ProviderKeyStatus(label = "No key", tone = StatusTone.Warning),
            providerKeyStatus(apiKey = "", hasStoredKey = false),
        )
    }

    @Test
    fun validatesHeaderLines() {
        assertEquals(
            HeaderStatus(label = "No headers", tone = StatusTone.Neutral),
            "".headerStatus(),
        )
        assertEquals(
            HeaderStatus(label = "2 headers", tone = StatusTone.Accent),
            """
            X-Team: mobile
            X-Trace: enabled
            """.trimIndent().headerStatus(),
        )
        assertEquals(
            HeaderStatus(label = "2 invalid headers", tone = StatusTone.Critical),
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
