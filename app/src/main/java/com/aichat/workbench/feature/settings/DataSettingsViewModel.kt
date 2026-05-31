package com.aichat.workbench.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.app.AppGraph
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
        _state.update { it.copy(importJson = value, importSummary = null, status = null) }
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
                        status = "Export ready",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "Export failed.") }
            }
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun importCurrentJson() {
        importJson(_state.value.importJson)
    }

    fun importJson(value: String) {
        if (value.isBlank()) {
            _state.update { it.copy(status = "Import JSON must not be blank.") }
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
                        status = "Import complete",
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "Import failed.") }
            }
            _state.update { it.copy(isBusy = false) }
        }
    }

    fun clearChatHistory() {
        runClear("Chat history cleared") {
            backupService.clearChatHistory()
        }
    }

    fun clearProvidersAndApiKeys() {
        runClear("Providers and API keys cleared") {
            backupService.clearProvidersAndApiKeys()
        }
    }

    fun clearPromptsModelsAndImages() {
        runClear("Prompts, model preferences, and images cleared") {
            backupService.clearPromptsModelsAndImages()
        }
    }

    fun clearAllData() {
        runClear("All local data cleared") {
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
                        importSummary = null,
                        status = successMessage,
                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(status = error.message ?: "Clear failed.") }
            }
            _state.update { it.copy(isBusy = false) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                DataSettingsViewModel(AppGraph.backupService) as T
        }
    }
}
