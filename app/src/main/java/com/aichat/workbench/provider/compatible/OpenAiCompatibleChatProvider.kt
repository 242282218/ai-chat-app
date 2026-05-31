package com.aichat.workbench.provider.compatible

import com.aichat.workbench.provider.openai.OpenAiChatProvider
import okhttp3.OkHttpClient

class OpenAiCompatibleChatProvider(
    client: OkHttpClient = OkHttpClient(),
) : OpenAiChatProvider(client, useResponsesApi = false)
