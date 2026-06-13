package com.aichat.workbench.feature.chat

import com.aichat.workbench.domain.model.ModelCapability
import com.aichat.workbench.domain.model.ModelConfig
import com.aichat.workbench.domain.model.ProviderConfig
import com.aichat.workbench.domain.model.ProviderId
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.feature.provider.availableChatModels
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatModelOptionsTest {
    @Test
    fun availableChatModelsExcludesImageOnlyModels() {
        val models = listOf(
            model("gpt-5.4", text = true, imageGeneration = false),
            model("gpt-image-2", text = false, imageGeneration = true),
            ModelConfig("dall-e-3", "DALL-E 3", capability = null),
        )

        assertEquals(listOf("gpt-5.4"), models.availableChatModels().map { it.id })
    }

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
                maxContextTokens = null,
            ),
        )
}
