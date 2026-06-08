package com.aichat.workbench.feature.provider

import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
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
        assertEquals("New API", ProviderType.NewApi.providerTypeLabel())
        assertEquals("Sub2 API", ProviderType.Sub2Api.providerTypeLabel())
        assertEquals("自定义兼容接口", ProviderType.Custom.providerTypeLabel())
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
            ProviderUrlStatus(label = "需要接口地址", tone = StatusTone.Warning),
            "".providerUrlStatus(allowHttp = false),
        )
        assertEquals(
            ProviderUrlStatus(label = "接口地址有效", tone = StatusTone.Success),
            "https://api.example.com/v1".providerUrlStatus(allowHttp = false),
        )
        assertEquals(
            ProviderUrlStatus(label = "HTTP 已阻止", tone = StatusTone.Critical),
            "http://localhost:11434/v1".providerUrlStatus(allowHttp = false),
        )
        assertEquals(
            ProviderUrlStatus(label = "HTTP 已阻止", tone = StatusTone.Critical),
            "HTTP://localhost:11434/v1".providerUrlStatus(allowHttp = false),
        )
        assertEquals(
            ProviderUrlStatus(label = "已允许 HTTP", tone = StatusTone.Warning),
            "http://localhost:11434/v1".providerUrlStatus(allowHttp = true),
        )
        assertEquals(
            ProviderUrlStatus(label = "已允许 HTTP", tone = StatusTone.Warning),
            "HTTP://localhost:11434/v1".providerUrlStatus(allowHttp = true),
        )
        assertEquals(
            ProviderUrlStatus(label = "接口地址无效", tone = StatusTone.Critical),
            "localhost:11434/v1".providerUrlStatus(allowHttp = true),
        )
    }

    @Test
    fun previewsOpenAiCompatibleEndpointUrls() {
        assertEquals(
            ProviderEndpointPreview(
                requestBaseUrl = "https://zzshu.cc/v1",
                modelDiscoveryBaseUrl = "https://zzshu.cc/v1/models",
                imageGenerationUrl = "https://zzshu.cc/v1/images/generations",
            ),
            providerEndpointPreview(
                type = ProviderType.NewApi,
                baseUrl = "https://zzshu.cc",
                allowHttp = false,
            ),
        )
    }

    @Test
    fun previewsOllamaModelDiscoveryWithoutOpenAiSuffix() {
        assertEquals(
            ProviderEndpointPreview(
                requestBaseUrl = "http://10.0.2.2:11434/v1",
                modelDiscoveryBaseUrl = "http://10.0.2.2:11434/api/tags",
                imageGenerationUrl = null,
            ),
            providerEndpointPreview(
                type = ProviderType.Ollama,
                baseUrl = "http://10.0.2.2:11434",
                allowHttp = true,
            ),
        )
    }

    @Test
    fun omitsEndpointPreviewForInvalidUrls() {
        assertEquals(
            null,
            providerEndpointPreview(
                type = ProviderType.NewApi,
                baseUrl = "zzshu.cc",
                allowHttp = false,
            ),
        )
    }

    @Test
    fun exposesProviderCapabilityTags() {
        assertEquals(
            listOf("文本", "视觉", "工具", "图片", "结构化", "长上下文"),
            ProviderType.NewApi.providerCapabilityTags().map { it.label },
        )
        assertEquals(
            listOf("文本", "视觉", "长上下文"),
            ProviderType.Ollama.providerCapabilityTags().map { it.label },
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
    fun saveStatusRequiresApiKeyOnlyForEnabledKeyProviders() {
        assertEquals(
            ProviderActionStatus(label = "需要 API Key", isReady = false),
            providerSaveStatus(
                name = "OpenAI",
                type = ProviderType.OpenAI,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "",
                hasStoredKey = false,
                headers = "",
                enabled = true,
                allowHttp = false,
            ),
        )
        assertEquals(
            ProviderActionStatus(label = "可保存", isReady = true),
            providerSaveStatus(
                name = "Custom",
                type = ProviderType.Custom,
                baseUrl = "https://zzshu.cc",
                apiKey = "sk-test",
                hasStoredKey = false,
                headers = "",
                enabled = false,
                allowHttp = false,
            ),
        )
        assertEquals(
            ProviderActionStatus(label = "可保存", isReady = true),
            providerSaveStatus(
                name = "Ollama",
                type = ProviderType.Ollama,
                baseUrl = "http://10.0.2.2:11434",
                apiKey = "",
                hasStoredKey = false,
                headers = "",
                enabled = true,
                allowHttp = true,
            ),
        )
    }

    @Test
    fun saveStatusAllowsDisabledProviderWithInvalidUrlButTestDoesNot() {
        assertEquals(
            ProviderActionStatus(label = "可保存", isReady = true),
            providerSaveStatus(
                name = "Broken",
                type = ProviderType.OpenAICompatible,
                baseUrl = "broken.local",
                apiKey = "",
                hasStoredKey = false,
                headers = "",
                enabled = false,
                allowHttp = false,
            ),
        )
        assertEquals(
            ProviderActionStatus(label = "接口地址无效", isReady = false),
            providerTestStatus(
                type = ProviderType.OpenAICompatible,
                baseUrl = "broken.local",
                apiKey = "sk-test",
                hasStoredKey = false,
                headers = "",
                allowHttp = false,
            ),
        )
    }

    @Test
    fun providerActionsRejectInvalidHeaders() {
        assertEquals(
            ProviderActionStatus(label = "1 个不允许保存", isReady = false),
            providerSaveStatus(
                name = "OpenAI",
                type = ProviderType.OpenAI,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-test",
                hasStoredKey = false,
                headers = "Authorization: Bearer test",
                enabled = true,
                allowHttp = false,
            ),
        )
        assertEquals(
            ProviderActionStatus(label = "1 个无效请求头", isReady = false),
            providerTestStatus(
                type = ProviderType.OpenAI,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "sk-test",
                hasStoredKey = false,
                headers = "Missing separator",
                allowHttp = false,
            ),
        )
    }

    @Test
    fun summarizesUnsupportedStoredProviderAsUnavailable() {
        assertEquals(
            "暂不可用 · Anthropic · claude-test · 未同步模型 · 缺少密钥",
            provider(type = ProviderType.Anthropic, model = "claude-test").connectionSummary(),
        )
    }

    @Test
    fun countsOnlySupportedEnabledChatProvidersAsAvailable() {
        val stats = listOf(
            provider(
                type = ProviderType.OpenAI,
                name = "OpenAI",
                apiKeyRef = "key-1",
            ),
            provider(
                type = ProviderType.Anthropic,
                name = "Anthropic",
                headers = mapOf("X-Trace" to "enabled"),
            ),
            provider(
                type = ProviderType.Ollama,
                name = "Ollama",
                baseUrl = "HTTP://10.0.2.2:11434",
                enabled = false,
            ),
        ).providerHealthStats()

        assertEquals(3, stats.totalCount)
        assertEquals(1, stats.enabledChatCount)
        assertEquals("OpenAI", stats.defaultChatProviderName)
        assertEquals(1, stats.encryptedKeyCount)
        assertEquals(1, stats.httpCount)
        assertEquals(1, stats.customHeaderCount)
        assertEquals(1, stats.unsupportedEnabledCount)
    }

    @Test
    fun validatesHeaderLines() {
        assertEquals(
            HeaderStatus(label = "无请求头", tone = StatusTone.Neutral),
            "".headerStatus(),
        )
        assertEquals(
            HeaderStatus(label = "2 个请求头", tone = StatusTone.Accent),
            """
            X-Trace: enabled
            X-Request-Id: request-1
            """.trimIndent().headerStatus(),
        )
        assertEquals(
            HeaderStatus(label = "2 个无效请求头", tone = StatusTone.Critical),
            """
            Missing separator
            Empty:
            X-Trace: enabled
            """.trimIndent().headerStatus(),
        )
        assertEquals(
            HeaderStatus(label = "2 个不允许保存", tone = StatusTone.Critical),
            """
            Authorization: Bearer test
            X-Team: mobile
            X-Trace: enabled
            """.trimIndent().headerStatus(),
        )
    }

    @Test
    fun parsesOnlyValidHeaderLines() {
        val headers = parseHeaderLines(
            """
            X-Request-Id: request-1
            Authorization: Bearer test
            Missing separator
            X-Trace: enabled
            """.trimIndent(),
        )

        assertTrue("X-Request-Id: request-1\nX-Trace: enabled".hasValidHeaderLines())
        assertFalse("X-Team:".hasValidHeaderLines())
        assertFalse("Authorization: Bearer test".hasValidHeaderLines())
        assertEquals(
            mapOf(
                "X-Request-Id" to "request-1",
                "X-Trace" to "enabled",
            ),
            headers,
        )
    }

    @Test
    fun addsManualImageModelWithoutChangingChatDefaultModel() {
        val models = listOf(
            model("gpt-5.4", text = true, imageGeneration = false),
        ).withManualImageModel("custom-image-model")

        val textModel = models.first { it.id == "gpt-5.4" }
        val imageModel = models.first { it.id == "custom-image-model" }
        assertTrue(textModel.capability?.text == true)
        assertFalse(textModel.capability?.imageGeneration == true)
        assertFalse(imageModel.capability?.text == true)
        assertTrue(imageModel.capability?.imageGeneration == true)
    }

    @Test
    fun manualImageModelOverridesDiscoveredTextCapabilityForSameModel() {
        val models = listOf(
            model("provider-image-alias", text = true, imageGeneration = false),
        ).withManualImageModel("provider-image-alias")

        val imageModel = models.single()
        assertFalse(imageModel.capability?.text == true)
        assertTrue(imageModel.capability?.imageGeneration == true)
    }

    @Test
    fun addsManualToolModelWithToolCallingCapability() {
        val models = listOf(
            model("gpt-5.4", text = true, imageGeneration = false),
        ).withManualToolModel("tool-router")

        val toolModel = models.first { it.id == "tool-router" }
        assertTrue(toolModel.capability?.text == true)
        assertTrue(toolModel.capability?.toolCalling == true)
        assertTrue(toolModel.capability?.structuredOutput == true)
        assertFalse(toolModel.capability?.imageGeneration == true)
    }

    @Test
    fun manualCodeModelOverridesDiscoveredImageCapabilityForSameModel() {
        val models = listOf(
            model("code-helper", text = false, imageGeneration = true),
        ).withManualCodeModel("code-helper")

        val codeModel = models.single()
        assertTrue(codeModel.capability?.text == true)
        assertFalse(codeModel.capability?.toolCalling == true)
        assertTrue(codeModel.capability?.structuredOutput == true)
        assertFalse(codeModel.capability?.imageGeneration == true)
    }

    private fun provider(
        type: ProviderType = ProviderType.OpenAI,
        name: String = "Provider",
        baseUrl: String = "https://example.test/v1",
        apiKeyRef: String? = null,
        headers: Map<String, String> = emptyMap(),
        model: String? = "gpt-test",
        enabled: Boolean = true,
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = name,
            type = type,
            baseUrl = baseUrl,
            apiKeyRef = apiKeyRef,
            headers = headers,
            models = emptyList(),
            defaultModel = model,
            enabled = enabled,
        )

    private fun model(
        id: String,
        text: Boolean,
        imageGeneration: Boolean,
    ): ModelConfig =
        ModelConfig(
            id = id,
            displayName = id,
            capability = ModelCapability(
                model = id,
                text = text,
                vision = text,
                imageGeneration = imageGeneration,
                toolCalling = text,
                structuredOutput = text,
                longContext = text,
                maxContextTokens = null,
            ),
        )
}
