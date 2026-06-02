package com.aichat.workbench.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.data.backup.BackupImportSummary
import com.aichat.workbench.data.backup.BackupService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DataSettingsUiState(
    val includeChats: Boolean = false,
    val exportJson: String = "",
    val importJson: String = "",
    val importPreviewJson: String? = null,
    val importPreviewSummary: BackupImportSummary? = null,
    val importSummary: BackupImportSummary? = null,
    val isBusy: Boolean = false,
    val status: String? = null,
)

class DataSettingsViewModel(
    private val backupService: BackupService,
) : ViewModel() {
    private val _state = MutableStateFlow(DataSettingsUiState())
    val state: StateFlow<DataSettingsUiState> = _state.asStateFlow()
    private var importPreviewVersion: Int = 0

    fun updateIncludeChats(value: Boolean) {
        _state.update { it.copy(includeChats = value) }
    }

    fun updateImportJson(value: String) {
        importPreviewVersion += 1
        _state.update {
            it.copy(
                importJson = value,
                importPreviewJson = null,
                importPreviewSummary = null,
                importSummary = null,
                status = null,
            )
        }
    }

    fun previewImportJson(value: String) {
        if (_state.value.isBusy) return
        val previewVersion = ++importPreviewVersion
        if (value.isBlank()) {
            _state.update {
                it.copy(
                    importPreviewJson = null,
                    importPreviewSummary = null,
                    status = "导入 JSON 不能为空。",
                )
            }
            return
        }
        _state.update {
            it.copy(
                importPreviewJson = null,
                importPreviewSummary = null,
                status = "正在预览导入内容…",
                isBusy = true,
            )
        }
        viewModelScope.launch {
            try {
                runCatching {
                    backupService.previewImportJson(value)
                }.onSuccess { summary ->
                    updatePreviewStateIfCurrent(previewVersion, value) {
                        copy(
                            importPreviewJson = value,
                            importPreviewSummary = summary,
                            status = null,
                        )
                    }
                }.onFailure { error ->
                    updatePreviewStateIfCurrent(previewVersion, value) {
                        copy(
                            importPreviewJson = null,
                            importPreviewSummary = null,
                            status = error.message ?: "导入预览失败。",
                        )
                    }
                }
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    fun updateStatus(message: String) {
        _state.update { it.copy(status = message) }
    }

    fun createExport() {
        val includeChats = _state.value.includeChats
        launchBusyOperation(
            beforeStart = { it.copy(status = null) },
        ) {
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
        }
    }

    fun importCurrentJson() {
        importJson(_state.value.importJson)
    }

    fun importJson(value: String) {
        if (_state.value.isBusy) return
        if (value.isBlank()) {
            _state.update { it.copy(status = "导入 JSON 不能为空。") }
            return
        }
        val current = _state.value
        if (current.importPreviewJson != value || current.importPreviewSummary == null) {
            _state.update { it.copy(status = "请先预览并确认导入摘要。") }
            return
        }
        launchBusyOperation(
            beforeStart = { it.copy(status = null, importSummary = null) },
        ) {
            runCatching {
                backupService.importJson(value)
            }.onSuccess { summary ->
                _state.update {
                    it.copy(
                        importSummary = summary,
                        importPreviewJson = null,
                        importPreviewSummary = null,
                        status = "导入完成",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "导入失败。") }
            }
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
        launchBusyOperation(
            beforeStart = { it.copy(status = null) },
        ) {
            runCatching {
                block()
            }.onSuccess {
                _state.update {
                    it.copy(
                        exportJson = "",
                        importPreviewJson = null,
                        importPreviewSummary = null,
                        importSummary = null,
                        status = successMessage,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "清空失败。") }
            }
        }
    }

    private fun launchBusyOperation(
        beforeStart: (DataSettingsUiState) -> DataSettingsUiState,
        block: suspend () -> Unit,
    ) {
        if (_state.value.isBusy) return
        importPreviewVersion += 1
        _state.update { beforeStart(it).copy(isBusy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun updatePreviewStateIfCurrent(
        previewVersion: Int,
        value: String,
        transform: DataSettingsUiState.() -> DataSettingsUiState,
    ) {
        _state.update { current ->
            if (previewVersion == importPreviewVersion && current.importJson == value) {
                current.transform()
            } else {
                current
            }
        }
    }
}
