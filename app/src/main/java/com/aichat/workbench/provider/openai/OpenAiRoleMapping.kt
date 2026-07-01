package com.aichat.workbench.provider.openai

import com.aichat.workbench.domain.model.MessageRole

internal fun MessageRole.toOpenAiRole(): String =
    when (this) {
        MessageRole.System -> "system"
        MessageRole.User -> "user"
        MessageRole.Assistant -> "assistant"
    }
