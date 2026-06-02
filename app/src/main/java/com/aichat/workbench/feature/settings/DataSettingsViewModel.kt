package com.aichat.workbench.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.data.backup.AppBackupService
import com.aichat.workbench.data.backup.BackupImportSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataSettingsUiState(
    val includeChats: Boolean = false,
    val exportJson: String = "",
    val importJson: String = "",
    val importPreviewSummary: BackupImportSummary? = null,
    val importSummary: BackupImportSummary? = null,
    val isBusy: Boolean = false,
    val status: String? = null,
)

class DataSettingsViewModel(
    private val backupService: AppBackupService,
) : ViewModel() {
    private val _state = MutableStateFlow(DataSettingsUiState())
    val state: StateFlow<DataSettingsUiState> = _state.asStateFlow()

    fun updateIncludeChats(value: Boolean) {
        _state.update { it.copy(includeChats = value) }
    }

    fun updateImportJson(value: String) {
        _state.update {
            it.copy(
                importJson = value,
                importPreviewSummary = null,
                importSummary = null,
                status = null,
            )
        }
    }

    fun previewImportJson(value: String) {
        if (value.isBlank()) {
            _state.update { it.copy(importPreviewSummary = null, status = "导入 JSON 不能为空。") }
            return
        }
        viewModelScope.launch {
            runCatching {
                backupService.previewImportJson(value)
            }.onSuccess { summary ->
                _state.update { it.copy(importPreviewSummary = summary, status = null) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        importPreviewSummary = null,
                        status = error.message ?: "导入预览失败。",
                    )
                }
            }
        }
    }

    fun updateStatus(message: String) {
        _state.update { it.copy(status = message) }
    }

    fun createExport() {
        val includeChats = _state.value.includeChats
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, status = null) }
            runCatching {
                backupService.exportJson(includeChats)
            }.onSuccess { json ->
                _state.update {
                    it.copy(
                        exportJson = json,
                        status = "导出就绪",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "导出失败。") }
            }
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun importCurrentJson() {
        importJson(_state.value.importJson)
    }

    fun importJson(value: String) {
        if (value.isBlank()) {
            _state.update { it.copy(status = "导入 JSON 不能为空。") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, status = null, importSummary = null) }
            runCatching {
                backupService.importJson(value)
            }.onSuccess { summary ->
                _state.update {
                    it.copy(
                        importSummary = summary,
                        importPreviewSummary = null,
                        status = "导入完成",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "导入失败。") }
            }
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun clearChatHistory() {
        runClear("聊天历史已清空") {
            backupService.clearChatHistory()
        }
    }

    fun clearProvidersAndApiKeys() {
        runClear("模型连接和 API Key 已清空") {
            backupService.clearProvidersAndApiKeys()
        }
    }

    fun clearPromptsModelsAndImages() {
        runClear("提示词、模型偏好和图片已清空") {
            backupService.clearPromptsModelsAndImages()
        }
    }

    fun clearAllData() {
        runClear("全部本地数据已清空") {
            backupService.clearAllData()
        }
    }

    private fun runClear(
        successMessage: String,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, status = null) }
            runCatching {
                block()
            }.onSuccess {
                _state.update {
                    it.copy(
                        exportJson = "",
                        importPreviewSummary = null,
                        importSummary = null,
                        status = successMessage,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "清空失败。") }
            }
            _state.update { it.copy(isBusy = false) }
        }
    }
}
