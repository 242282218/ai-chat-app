package com.aichat.workbench.feature.image

import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationId
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.ui.component.StatusTone
import java.time.Instant
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

        assertFalse(
            missingKey.copy(
                providers = listOf(provider(apiKeyRef = "key-ref")),
                providerApiKeyAvailable = mapOf("provider-1" to false),
            ).canGenerateImages(),
        )
        assertTrue(
            missingKey.copy(
                providers = listOf(provider(apiKeyRef = "key-ref")),
                providerApiKeyAvailable = mapOf("provider-1" to true),
            ).canGenerateImages(),
        )
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

    @Test
    fun availableImageModelsExcludesTextOnlyModels() {
        val state = state(
            provider = provider(
                apiKeyRef = "key-ref",
                models = listOf(
                    model("gpt-5.4", text = true, imageGeneration = false),
                    model("gpt-image-2", text = false, imageGeneration = true),
                    ModelConfig("flux-pro", "Flux Pro", capability = null),
                ),
            ),
            prompt = "Draw",
            model = "gpt-image-2",
        )

        assertEquals(listOf("gpt-image-2", "flux-pro"), state.availableImageModels().map { it.id })
    }

    @Test
    fun imageGenerationChatReferenceDraftPreservesUsefulContext() {
        val draft = ImageGeneration(
            id = ImageGenerationId("image-1"),
            conversationId = null,
            prompt = "Draw a cabin",
            providerId = ProviderId("provider-1"),
            model = "gpt-image-1",
            size = "1024x1024",
            quality = "auto",
            count = 1,
            originalPath = "/data/user/0/app/files/images/originals/image-1.png",
            thumbnailPath = null,
            status = ImageGenerationStatus.Completed,
            errorSummary = null,
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
        ).toChatReferenceDraft()

        assertTrue(draft.contains("不要自动上传本地文件"))
        assertTrue(draft.contains("Provider：provider-1"))
        assertTrue(draft.contains("图片提示词：Draw a cabin"))
        assertTrue(draft.contains("模型：gpt-image-1"))
        assertTrue(draft.contains("本地图片路径：/data/user/0/app/files/images/originals/image-1.png"))
    }

    @Test
    fun failedImageGenerationChatDraftPreparesRecoveryInsteadOfReferencingMissingImage() {
        val draft = ImageGeneration(
            id = ImageGenerationId("image-2"),
            conversationId = null,
            prompt = "Draw a cabin",
            providerId = ProviderId("provider-1"),
            model = "gpt-image-1",
            size = "1024x1024",
            quality = "auto",
            count = 1,
            originalPath = null,
            thumbnailPath = null,
            status = ImageGenerationStatus.Failed,
            errorSummary = "HTTP 429: Rate limit exceeded",
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
        ).toChatReferenceDraft()

        assertFalse(draft.contains("请基于这张图片继续处理"))
        assertTrue(draft.contains("准备一个可重新发起的 image_generation 工具调用"))
        assertTrue(draft.contains("不要假设图片已生成"))
        assertTrue(draft.contains("工具：image_generation"))
        assertTrue(
            draft.contains(
                """参数：{"prompt":"Draw a cabin","model":"gpt-image-1","size":"1024x1024","quality":"auto","count":1}""",
            ),
        )
        assertTrue(draft.contains("Provider：provider-1"))
        assertTrue(draft.contains("图片提示词：Draw a cabin"))
        assertTrue(draft.contains("错误：HTTP 429: Rate limit exceeded"))
    }

    @Test
    fun failedImageGenerationChatDraftEscapesPromptInToolInputJson() {
        val draft = ImageGeneration(
            id = ImageGenerationId("image-3"),
            conversationId = null,
            prompt = "Draw \"quoted\"\ncat",
            providerId = ProviderId("provider-1"),
            model = null,
            size = null,
            quality = null,
            count = 9,
            originalPath = null,
            thumbnailPath = null,
            status = ImageGenerationStatus.Failed,
            errorSummary = null,
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
        ).toChatReferenceDraft()

        assertTrue(draft.contains("""参数：{"prompt":"Draw \"quoted\"\ncat","count":4}"""))
        assertTrue(draft.contains("模型：未记录"))
        assertTrue(draft.contains("错误：未记录"))
    }

    @Test
    fun connectionTestChatDraftKeepsDiagnosticAndApiKeyBoundary() {
        val draft = """
            图片模型连接测试
            Provider：OpenAI
            模型：gpt-image-1
            结果：连接失败
            HTTP：401
            消息：Unauthorized
        """.trimIndent().toConnectionTestChatDraft()

        assertTrue(draft.contains("图片模型连接测试诊断"))
        assertTrue(draft.contains("Provider：OpenAI"))
        assertTrue(draft.contains("HTTP：401"))
        assertTrue(draft.contains("只能基于诊断字段分析"))
        assertTrue(draft.contains("不要要求我粘贴 API Key"))
        assertTrue(draft.contains("不要输出或推测 API Key 明文"))
    }

    private fun state(
        provider: ProviderConfig?,
        prompt: String,
        model: String,
    ): ImageGenerationUiState =
        ImageGenerationUiState(
            providers = listOfNotNull(provider),
            providerApiKeyAvailable = provider
                ?.let { mapOf(it.id.value to (it.apiKeyRef != null)) }
                .orEmpty(),
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
