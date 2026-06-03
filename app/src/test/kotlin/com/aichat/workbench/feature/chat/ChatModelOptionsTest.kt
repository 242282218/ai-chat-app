package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatModelOptionsTest {
    @Test
    fun availableChatModelsExcludesImageOnlyModels() {
        val provider = provider(
            models = listOf(
                model("gpt-5.4", text = true, imageGeneration = false),
                model("gpt-image-2", text = false, imageGeneration = true),
                ModelConfig("dall-e-3", "DALL-E 3", capability = null),
            ),
        )

        assertEquals(listOf("gpt-5.4"), provider.availableChatModels().map { it.id })
    }

    private fun provider(models: List<ModelConfig>): ProviderConfig =
        ProviderConfig(
            id = ProviderId("provider-1"),
            name = "New API",
            type = ProviderType.NewApi,
            baseUrl = "https://example.test/v1",
            apiKeyRef = "key-ref",
            headers = emptyMap(),
            models = models,
            defaultModel = "gpt-5.4",
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
