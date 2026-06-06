package com.aichat.workbench.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.domain.model.Conversation
import com.aichat.workbench.domain.model.ConversationId
import com.aichat.workbench.domain.model.MessageId
import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.MessageRole
import com.aichat.workbench.domain.model.MessageStatus
import com.aichat.workbench.domain.model.PromptPresetId
import com.aichat.workbench.domain.repository.ConversationRepository
import com.aichat.workbench.domain.repository.EmptyModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.ModelRolePreferenceRepository
import com.aichat.workbench.domain.repository.PromptPresetRepository
import com.aichat.workbench.domain.repository.ProviderConfigRepository
import com.aichat.workbench.provider.ProviderRegistry
import com.aichat.workbench.provider.preferredChatModel
import com.aichat.workbench.tool.model.canonicalToolName
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val conversationRepository: ConversationRepository,
    private val providerRepository: ProviderConfigRepository,
    private val modelRolePreferenceRepository: ModelRolePreferenceRepository = EmptyModelRolePreferenceRepository,
    private val promptPresetRepository: PromptPresetRepository,
    private val conversationManager: ConversationManager,
    private val generationController: GenerationController,
    private val providerRegistry: ProviderRegistry,
    private val applicationScope: com.aichat.workbench.app.ApplicationScope,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState(draft = DraftState.fromSavedState(savedStateHandle)))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var messagesJob: Job? = null
    private var observedConversationId: ConversationId? = null
    init {
        viewModelScope.observeChatStateSources(
            conversationRepository,
            providerRepository,
            modelRolePreferenceRepository,
            promptPresetRepository,
            conversationManager,
            providerRegistry,
            currentState = { _state.value },
            updateState = ::updateState,
            observeMessages = ::observeMessages,
        )
    }
    fun selectConversation(id: ConversationId) {
        val conversation = _state.value.conversations.firstOrNull { it.id == id } ?: return
        selectConversation(conversation)
    }
    fun updateInput(value: String) = updateDraft { it.copy(input = value) }
    fun updateTitleDraft(value: String) = updateDraft { it.copy(title = value) }
    fun updateSystemPromptDraft(value: String) = updateDraft { it.copy(systemPrompt = value) }
    fun updateModelDraft(value: String) = updateDraft { it.copy(model = value) }
    fun updateTemperatureDraft(value: String) = updateDraft { it.copy(temperature = value) }
    fun updateTopPDraft(value: String) = updateDraft { it.copy(topP = value) }
    fun updateMaxTokensDraft(value: String) = updateDraft { it.copy(maxTokens = value) }
    fun updateTemporaryDraft(value: Boolean) = updateDraft { it.copy(temporary = value) }
    fun updateSensitiveDraft(value: Boolean) = updateDraft { it.copy(sensitive = value) }
    fun applyInitialDraft(input: String, temporary: Boolean) {
        val draftInput = input.trim()
        if (draftInput.isBlank() && !temporary) return
        updateDraft {
            it.copy(
                input = draftInput.ifBlank { it.input },
                temporary = temporary || it.temporary,
            )
        }
    }
    fun addImageDraft(image: MessagePart.Image) = updateState { it.copy(imageDrafts = it.imageDrafts + image, error = null) }
    fun removeImageDraft(index: Int) = updateState {
        it.copy(imageDrafts = it.imageDrafts.filterIndexed { itemIndex, _ -> itemIndex != index })
    }
    fun attachFile(uri: String) {
        val normalizedUri = uri.trim()
        if (normalizedUri.isBlank()) return
        if (!normalizedUri.startsWith("content://")) {
            updateState { it.copy(error = "只能读取通过系统文件选择器授权的 content:// 文件。") }
            return
        }
        updateState {
            it.copy(
                draft = it.draft.copy(input = it.input.appendFileReadInstruction(normalizedUri)),
                error = null,
            )
        }
    }
    fun clearAttachedFileTask() {
        updateDraft { it.copy(input = it.input.removeFileReadInstruction()) }
    }
    fun reuseImagePrompt(prompt: String) {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isBlank()) return
        updateDraft { it.copy(input = normalizedPrompt) }
    }
    fun regenerateImagePrompt(prompt: String) {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isBlank()) return
        updateDraft { it.copy(input = normalizedPrompt.toImageGenerationInstruction()) }
    }
    fun prepareSearchTask(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return
        updateDraft { it.copy(input = normalizedQuery.toWebSearchInstruction()) }
    }
    fun prepareLocalJsTask(code: String) {
        val normalizedCode = code.trim()
        if (normalizedCode.isBlank()) return
        updateDraft { it.copy(input = normalizedCode.toLocalJsInstruction()) }
    }
    fun prepareLocalJsRecoveryTask(toolResult: String?) {
        val normalizedResult = toolResult?.trim().orEmpty()
        if (normalizedResult.isBlank()) return
        updateDraft { it.copy(input = normalizedResult.toLocalJsRecoveryInstruction()) }
    }
    fun prepareToolRecoveryTask(toolName: String, toolResult: String?, reason: String) {
        val normalizedToolName = toolName.trim()
        val normalizedResult = toolResult?.trim().orEmpty()
        val normalizedReason = reason.trim()
        if (normalizedToolName.isBlank() || normalizedResult.isBlank() || normalizedReason.isBlank()) return
        updateDraft {
            it.copy(
                input = normalizedResult.toToolRecoveryInstruction(
                    toolName = normalizedToolName,
                    reason = normalizedReason,
                ),
            )
        }
    }
    fun prepareTextTransformTask(text: String) {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) return
        updateDraft { it.copy(input = normalizedText.toTextTransformInstruction()) }
    }
    fun prepareCodeDiffPreviewTask(original: String) {
        val normalizedOriginal = original.trim()
        if (normalizedOriginal.isBlank()) return
        updateDraft { it.copy(input = normalizedOriginal.toCodeDiffPreviewInstruction()) }
    }
    fun continueWithToolResult(toolName: String, toolResult: String?) {
        val normalizedToolName = toolName.trim()
        val normalizedResult = toolResult?.trim().orEmpty()
        if (normalizedToolName.isBlank() || normalizedResult.isBlank()) return
        updateDraft {
            it.copy(
                input = normalizedResult.toToolResultContinuationInstruction(
                    toolName = normalizedToolName,
                ),
            )
        }
    }
    fun reportImageInputError(message: String) = updateState { it.copy(error = message) }
    fun toggleSettingsExpanded() = updateState { it.copy(settingsExpanded = !it.settingsExpanded) }
    fun togglePromptsExpanded() = updateState { it.copy(promptsExpanded = !it.promptsExpanded) }
    fun selectProvider(id: String) {
        val provider = _state.value.providers.firstOrNull { it.id.value == id && it.enabled } ?: return
        updateState {
            val providerChanged = it.selectedProviderId != provider.id.value
            val preferredModel = provider.preferredChatModel(it.modelRolePreferences)
            it.copy(
                selectedProviderId = id,
                draft = it.draft.copy(
                    model = if (providerChanged) {
                        preferredModel
                    } else {
                        it.modelDraft.ifBlank { preferredModel }
                    },
                ),
            )
        }
    }
    fun createConversation() = createConversation(title = "新对话", temporary = false)
    fun createTemporaryConversation() = createConversation(title = "临时对话", temporary = true)
    fun renameSelectedConversation() {
        val id = _state.value.selectedConversationId ?: return
        val title = _state.value.titleDraft.trim()
        if (title.isBlank()) return
        viewModelScope.launch { conversationRepository.renameConversation(id, title) }
    }

    fun saveConversationSettings() {
        val current = _state.value
        if (conversationManager.selectedConversation(current) == null) return
        viewModelScope.launch {
            runCatching { conversationManager.saveSelectedSettings(current) }
                .onSuccess { updateState { it.copy(error = null) } }
                .onFailure { error -> updateState { it.copy(error = error.message ?: "模型参数无效。") } }
        }
    }
    fun applyPromptPreset(id: PromptPresetId) {
        val preset = _state.value.promptPresets.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            selectConversation(conversationManager.applyPromptPreset(_state.value, preset))
        }
    }
    fun archiveSelectedConversation() = runForSelected { conversationRepository.archiveConversation(it) }
    fun deleteSelectedConversation() = runForSelected { conversationRepository.deleteConversation(it) }

    fun deleteTemporaryConversationOnExit() {
        val conversation = conversationManager.selectedConversation(_state.value)
        if (conversation?.isTemporary != true) return
        // Use applicationScope so deletion survives ViewModel clearing (real exit),
        // but this is NOT called on configuration changes (see onCleared).
        applicationScope.launch {
            conversationRepository.deleteConversation(conversation.id)
        }
        clearSelection()
    }

    override fun onCleared() {
        super.onCleared()
        // onCleared only fires on genuine exit (back navigation / process death),
        // NOT on configuration changes — the correct place to clean up temp conversations.
        deleteTemporaryConversationOnExit()
    }

    fun clearContext() {
        val id = _state.value.selectedConversationId ?: return
        if (generationController.isActive) return
        viewModelScope.launch {
            conversationRepository.deleteMessages(id)
            updateState { it.copy(error = null, draft = it.draft.copy(input = "", editingMessageId = null)) }
        }
    }

    fun editMessage(id: MessageId) {
        val message = _state.value.messages.firstOrNull { it.id == id && it.role == MessageRole.User } ?: return
        updateState { it.copy(draft = it.draft.copy(input = message.content, editingMessageId = id), error = null) }
    }

    fun cancelEdit() = updateDraft { it.copy(editingMessageId = null, input = "") }

    fun sendMessage() {
        val current = _state.value
        val text = current.input.trim()
        if ((text.isBlank() && current.imageDrafts.isEmpty()) || generationController.isActive) return
        val edited = current.editingMessageId?.let { id ->
            current.messages.firstOrNull { it.id == id && it.role == MessageRole.User }
        }
        val userText = text.ifBlank { "图片消息" }
        generationController.start(viewModelScope, current, userText, edited, null, ::selectGeneratedConversation, ::updateState)
    }

    fun retryMessage(id: MessageId) {
        val failed = _state.value.messages.firstOrNull {
            it.id == id && it.role == MessageRole.Assistant && it.status == MessageStatus.Failed
        } ?: return
        if (generationController.isActive) return
        generationController.start(viewModelScope, _state.value, null, null, failed, ::selectGeneratedConversation, ::updateState)
    }

    fun stopGeneration() = generationController.stop(viewModelScope, ::updateState)
    fun confirmToolCall() = generationController.confirmToolCall()
    fun updatePendingToolArguments(arguments: String) {
        generationController.updatePendingToolArguments(arguments)
        updateState { state ->
            state.copy(
                pendingToolCall = state.pendingToolCall?.let { pending ->
                    pending.copy(toolCall = pending.toolCall.copy(arguments = arguments))
                },
            )
        }
    }
    fun denyToolCall() = generationController.denyToolCall()

    private fun createConversation(title: String, temporary: Boolean) {
        viewModelScope.launch {
            selectConversation(conversationManager.createConversation(_state.value, title, temporary))
        }
    }

    private fun runForSelected(block: suspend (ConversationId) -> Unit) {
        val id = _state.value.selectedConversationId ?: return
        viewModelScope.launch {
            block(id)
            clearSelection()
        }
    }

    private fun selectGeneratedConversation(conversation: Conversation) {
        if (_state.value.selectedConversationId != conversation.id) selectConversation(conversation)
    }

    private fun selectConversation(conversation: Conversation) {
        updateState { conversationManager.withSelectedConversation(it, it.conversations, conversation) }
        observeMessages(conversation.id)
    }

    private fun observeMessages(id: ConversationId) {
        if (observedConversationId == id) return
        observedConversationId = id
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            combine(
                conversationRepository.observeRecentMessages(id, CHAT_MESSAGE_WINDOW_SIZE),
                conversationRepository.observeMessageCount(id),
            ) { messages, count ->
                messages to count
            }.collect { (messages, count) ->
                updateState {
                    it.copy(
                        messages = messages,
                        selectedConversationMessageCount = count,
                    )
                }
            }
        }
    }

    private fun clearSelection() {
        observedConversationId = null
        messagesJob?.cancel()
        messagesJob = null
        updateState { conversationManager.clearSelection(it) }
    }

    private fun updateDraft(transform: (DraftState) -> DraftState) =
        updateState { it.copy(draft = transform(it.draft)) }

    private fun updateState(transform: (ChatUiState) -> ChatUiState) {
        var nextState: ChatUiState? = null
        _state.update { current -> transform(current).also { nextState = it } }
        nextState?.draft?.toSavedState(savedStateHandle)
    }
}

private const val CHAT_MESSAGE_WINDOW_SIZE = 200

private fun String.appendFileReadInstruction(uri: String): String {
    val currentInput = removeFileReadInstruction().trimEnd()
    val instruction = """
        请读取我刚通过系统文件选择器授权的文件，并基于文件内容继续处理。
        工具：file_read
        参数：{"uri":${uri.jsonStringLiteral()},"maxBytes":65536}
    """.trimIndent()
    return if (currentInput.isBlank()) instruction else "$currentInput\n\n$instruction"
}

internal fun String.hasFileReadInstruction(): Boolean =
    FILE_READ_INSTRUCTION_REGEX.containsMatchIn(this)

internal fun String.removeFileReadInstruction(): String {
    if (!hasFileReadInstruction()) return this
    return FILE_READ_INSTRUCTION_REGEX
        .replace(this, "\n\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private const val FILE_READ_INSTRUCTION_PREFIX =
    "请读取我刚通过系统文件选择器授权的文件，并基于文件内容继续处理。\n工具：file_read\n参数："

private val FILE_READ_INSTRUCTION_REGEX = Regex(
    "(?:^|\\n\\n)" +
        Regex.escape(FILE_READ_INSTRUCTION_PREFIX) +
        "\\{\"uri\":\"(?:\\\\.|[^\"\\\\])*\",\"maxBytes\":65536}\\s*(?=\\n\\n|$)",
)

private fun String.toImageGenerationInstruction(): String =
    """
        请重新生成这张图片，并优先使用图片生成工具；执行前确认 Provider、模型、数量和尺寸。
        这是联网且可能产生费用的调用，不要自动上传本地图片。
        工具：image_generation
        参数：{"prompt":${jsonStringLiteral()},"count":1}
    """.trimIndent()

private fun String.toWebSearchInstruction(): String =
    """
        请搜索这个主题的最新消息，保留来源链接，并总结事实、影响和待确认信息。
        回答中的关键结论必须标注对应来源 URL；如果没有结果，请明确说明没有可引用来源。
        工具：web_search_local
        参数：{"query":${jsonStringLiteral()}}
    """.trimIndent()

private fun String.toLocalJsInstruction(): String =
    """
        请使用本地 JavaScript 工具运行下面代码，并解释输出。
        只允许纯计算或文本处理；不要请求网络、文件系统、系统命令或 Android Context。
        工具：local_js
        参数：{"language":"javascript","code":${jsonStringLiteral()},"timeoutMillis":1000,"outputLimitBytes":8192}
    """.trimIndent()

private fun String.toLocalJsRecoveryInstruction(): String =
    """
        这次本地 JavaScript 工具结果不完整。请根据结果判断原因，并给出更适合重跑的代码或参数建议；如果需要再次运行，请先列出新的 local_js 参数。
        新参数仍必须遵守沙箱边界：不要请求网络、文件系统、系统命令或 Android Context。
        工具：local_js
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toToolRecoveryInstruction(
    toolName: String,
    reason: String,
): String =
    when (val canonicalName = toolName.canonicalToolName()) {
        "image_generation" -> toImageGenerationRecoveryInstruction(reason)
        "file_read" -> toFileReadRecoveryInstruction(reason)
        "local_js" -> toLocalJsRecoveryInstruction(reason)
        "web_search_local", "web_search" -> toWebSearchRecoveryInstruction(canonicalName, reason)
        "provider_connection_test" -> toProviderConnectionRecoveryInstruction(reason)
        else -> toGenericToolRecoveryInstruction(canonicalName, reason)
    }

private fun String.toGenericToolRecoveryInstruction(
    toolName: String,
    reason: String,
): String =
    """
        这次工具结果需要调整。请根据原因和上次结果重新规划工具参数；如果需要再次运行，请先列出新的 ${toolName.trim()} 参数。
        工具：${toolName.trim()}
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toFileReadRecoveryInstruction(reason: String): String =
    """
        这次文件读取工具结果需要调整。请根据原因和上次结果重新规划 file_read 参数；如果需要再次读取，请先列出新的 file_read 参数。
        必须继续使用用户通过系统文件选择器授权的 content:// URI；不要手写本地路径，不要扫描文件夹，不要自动上传图片或文件内容。
        工具：file_read
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toLocalJsRecoveryInstruction(reason: String): String =
    """
        这次本地 JavaScript 工具结果需要调整。请根据原因和上次结果重新规划 local_js 参数；如果需要再次运行，请先列出新的 local_js 参数。
        新参数仍必须遵守沙箱边界：不要请求网络、文件系统、系统命令或 Android Context；执行前确认超时和输出截断设置。
        工具：local_js
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toWebSearchRecoveryInstruction(
    toolName: String,
    reason: String,
): String =
    """
        这次搜索工具结果需要调整。请根据原因和上次结果重新规划 ${toolName.trim()} 参数；如果需要再次搜索，请先列出新的 ${toolName.trim()} 参数。
        回答中的关键结论必须标注对应来源 URL；如果没有结果，请明确说明没有可引用来源。
        工具：${toolName.trim()}
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toProviderConnectionRecoveryInstruction(reason: String): String =
    """
        这次 Provider 连接测试结果需要调整。请根据原因和上次结果重新规划 provider_connection_test 参数；如果需要再次测试，请先列出新的 provider_connection_test 参数。
        只能使用已保存的 Provider 配置，不要输出或索要 API Key 明文。
        工具：provider_connection_test
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toImageGenerationRecoveryInstruction(reason: String): String =
    """
        这次图片生成工具结果需要调整。请根据原因和上次结果重新规划 image_generation 参数；如果需要再次生成，请先列出新的 image_generation 参数。
        这是联网且可能产生费用的调用，执行前必须确认 Provider、模型、数量、尺寸和质量。
        不要自动上传本地图片；如果需要参考本地图片，先征得确认。
        工具：image_generation
        原因：${reason.trim()}
        上次工具结果：
        ```json
        $this
        ```
    """.trimIndent()

private fun String.toTextTransformInstruction(): String =
    """
        请用本地文本转换工具格式化下面内容。如果不是 JSON，请先说明无法格式化为 JSON，并建议可用的清洗方式。
        工具：text_transform
        参数：{"operation":"json_format","text":${jsonStringLiteral()}}
    """.trimIndent()

private fun String.toCodeDiffPreviewInstruction(): String =
    """
        请基于下面的原始代码准备修改版本，并用本地 Diff 预览工具展示差异，不写入文件。
        工具：code_diff_preview
        参数：{"fileName":"snippet","original":${jsonStringLiteral()},"modified":${jsonStringLiteral()}}
    """.trimIndent()

private fun String.toToolResultContinuationInstruction(toolName: String): String =
    if (toolName.canonicalToolName() == "file_read") {
        """
            请基于下面的文件读取结果继续处理，先提炼可见预览中的关键信息，再给出下一步建议。
            注意：工具结果默认只包含文件元数据和文本预览，不代表完整文件内容已发送给模型；不要编造未出现在预览中的内容。如需更多内容，请先要求重新选择文件或调整 maxBytes 后再次读取。
            工具：file_read
            工具结果：
            ```json
            $this
            ```
        """.trimIndent()
    } else {
        """
            请基于下面的工具结果继续处理，先提炼关键信息，再给出下一步建议。
            工具：${toolName.trim()}
            工具结果：
            ```json
            $this
            ```
        """.trimIndent()
    }

private fun String.jsonStringLiteral(): String =
    buildString {
        append('"')
        this@jsonStringLiteral.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
