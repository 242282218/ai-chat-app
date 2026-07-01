package com.aichat.workbench.provider.compatible

import com.aichat.workbench.provider.openai.OpenAiChatProvider
import okhttp3.OkHttpClient

// DI always provides a configured client; no default to avoid losing interceptors and connection pooling.
class OpenAiCompatibleChatProvider(
    client: OkHttpClient,
) : OpenAiChatProvider(client, useResponsesApi = false)
