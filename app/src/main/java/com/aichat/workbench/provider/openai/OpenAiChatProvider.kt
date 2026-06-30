package com.aichat.workbench.provider.openai

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ProviderType
import com.aichat.workbench.provider.api.ChatProvider
import com.aichat.workbench.provider.api.ChatProviderRequest
import com.aichat.workbench.provider.api.ProviderStreamEvent
import com.aichat.workbench.provider.api.ProviderTextResponse
import com.aichat.workbench.provider.compatible.OpenAiChatCompletionsProtocol
import com.aichat.workbench.provider.http.awaitResponse
import com.aichat.workbench.provider.http.parseSse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

open class OpenAiChatProvider(
    private val client: OkHttpClient = OkHttpClient(),
    private val useResponsesApi: Boolean = true,
) : ChatProvider {
    override suspend fun complete(request: ChatProviderRequest): ProviderTextResponse {
        if (!useResponsesApi || request.hasImageInput()) {
            return executeChatCompletion(request, stream = false)
        }
        val response = execute(OpenAiResponsesProtocol.buildRequest(request, stream = false))
        response.use {
            if (it.code == 404 && request.provider.type == ProviderType.OpenAI) {
                return executeChatCompletion(request, stream = false)
            }
            it.requireSuccessfulProviderResponse()
            return ProviderTextResponse(content = OpenAiResponsesProtocol.parseText(it.bodyText()))
        }
    }

    override fun stream(request: ChatProviderRequest): Flow<ProviderStreamEvent> =
        flow {
            if (!useResponsesApi || request.hasImageInput()) {
                emitChatCompletionStream(request)
                return@flow
            }
            val response = execute(OpenAiResponsesProtocol.buildRequest(request, stream = true))
            response.use {
                if (it.code == 404 && request.provider.type == ProviderType.OpenAI) {
                    emitChatCompletionStream(request)
                    return@flow
                }
                it.requireSuccessfulProviderResponse()
                var terminalEventReceived = false
                for (event in parseSse(it.requireBody().byteStream())) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    OpenAiResponsesProtocol.mapSse(event.data)?.let { mapped ->
                        terminalEventReceived = terminalEventReceived || mapped.isTerminal()
                        emit(mapped)
                    }
                }
                if (!terminalEventReceived) {
                    emit(ProviderStreamEvent.Completed)
                }
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun executeChatCompletion(
        request: ChatProviderRequest,
        stream: Boolean,
    ): ProviderTextResponse {
        val response = execute(OpenAiChatCompletionsProtocol.buildRequest(request, stream))
        response.use {
            it.requireSuccessfulProviderResponse()
            return ProviderTextResponse(content = OpenAiChatCompletionsProtocol.parseText(it.bodyText()))
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<ProviderStreamEvent>.emitChatCompletionStream(
        request: ChatProviderRequest,
    ) {
        val response = execute(OpenAiChatCompletionsProtocol.buildRequest(request, stream = true))
        response.use {
            it.requireSuccessfulProviderResponse()
            var terminalEventReceived = false
            for (event in parseSse(it.requireBody().byteStream())) {
                for (mapped in OpenAiChatCompletionsProtocol.mapSse(event.data)) {
                    terminalEventReceived = terminalEventReceived || mapped.isTerminal()
                    emit(mapped)
                }
            }
            if (!terminalEventReceived) {
                emit(ProviderStreamEvent.Completed)
            }
        }
    }

    private fun ChatProviderRequest.hasImageInput(): Boolean =
        messages.any { message -> message.contentParts.any { it is MessagePart.Image } }

    private suspend fun execute(request: Request): Response =
        client.newCall(request).awaitResponse()

    private fun ProviderStreamEvent.isTerminal(): Boolean =
        this is ProviderStreamEvent.Completed || this is ProviderStreamEvent.Failed
}
