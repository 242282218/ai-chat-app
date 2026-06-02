package com.aichat.workbench.feature.image

import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.ui.component.StatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageDraftsTest {
    @Test
    fun readinessRequiresSavedApiKeyForKeyProviders() {
        val missingKey = state(
            provider = provider(apiKeyRef = null),
            prompt = "Draw a cabin",
            model = "gpt-image-1",
        )

        assertEquals(
            ImageReadiness(
                label = "需要 API Key",
                tone = StatusTone.Warning,
                description = "当前模型服务需要已保存的 API Key；请先在模型连接中补全密钥。",
            ),
            missingKey.imageGenerationReadiness(),
        )
        assertFalse(missingKey.canGenerateImages())

        assertTrue(missingKey.copy(providers = listOf(provider(apiKeyRef = "key-ref"))).canGenerateImages())
    }

    @Test
    fun readinessExplainsPrimaryInvalidInputs() {
        val ready = state(
            provider = provider(apiKeyRef = "key-ref"),
            prompt = "Draw a cabin",
            model = "gpt-image-1",
        )

        assertEquals("就绪", ready.imageGenerationReadiness().label)
        assertEquals("需要模型服务", ready.copy(selectedProviderId = null).imageGenerationReadiness().label)
        assertEquals("需要提示词", ready.copy(prompt = "").imageGenerationReadiness().label)
        assertEquals("需要模型", ready.copy(model = "").imageGenerationReadiness().label)
        assertEquals("数量无效", ready.copy(count = "5").imageGenerationReadiness().label)
        assertEquals("生成中", ready.copy(isGenerating = true).imageGenerationReadiness().label)
    }

    @Test
    fun readinessRejectsUnsupportedSelectedModel() {
        val provider = provider(
            apiKeyRef = "key-ref",
            models = listOf(
                ModelConfig(
                    id = "text-only",
                    displayName = "Text only",
                    capability = ModelCapability(
                        model = "text-only",
                        text = true,
                        vision = false,
                        imageGeneration = false,
                        toolCalling = false,
                        structuredOutput = false,
                        longContext = false,
                        maxContextTokens = null,
                    ),
                ),
            ),
        )
        val state = state(provider = provider, prompt = "Draw a cabin", model = "text-only")

        assertEquals("模型不支持", state.imageGenerationReadiness().label)
        assertFalse(state.canGenerateImages())
    }

    @Test
    fun countAndModelLabelsDescribeDraftState() {
        val ready = state(provider = provider(apiKeyRef = "key-ref"), prompt = "Draw", model = "gpt-image-1")

        assertEquals(1, ready.imageCountOrNull())
        assertEquals("1 张图片", ready.imageCountLabel())
        assertEquals(StatusTone.Success, ready.imageCountTone())
        assertEquals("需要数量", ready.copy(count = "").imageCountLabel())
        assertEquals(StatusTone.Warning, ready.copy(count = "").imageCountTone())
        assertEquals("数量无效", ready.copy(count = "many").imageCountLabel())
        assertEquals(StatusTone.Critical, ready.copy(count = "many").imageCountTone())
        assertEquals("模型就绪", ready.imageModelLabel())
        assertEquals("需要模型", ready.copy(model = "").imageModelLabel())
    }

    private fun state(
        provider: ProviderConfig?,
        prompt: String,
        model: String,
    ): ImageGenerationUiState =
        ImageGenerationUiState(
            providers = listOfNotNull(provider),
            selectedProviderId = provider?.id?.value,
            prompt = prompt,
            model = model,
            size = "1024x1024",
            quality = "auto",
            count = "1",
        )

    private fun provider(
        apiKeyRef: String?,
        models: List<ModelConfig> = emptyList(),
    ): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = "OpenAI",
            type = ProviderType.OpenAI,
            baseUrl = "https://api.openai.com/v1",
            apiKeyRef = apiKeyRef,
            headers = emptyMap(),
            models = models,
            defaultModel = "gpt-image-1",
            enabled = true,
        )
}
