package com.aichat.workbench.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.workbench.domain.model.ThemeMode
import com.aichat.workbench.domain.repository.ThemeSettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsHubUiState(
    val themeMode: ThemeMode = ThemeMode.System,
)

class SettingsHubViewModel(
    private val themeSettingsRepository: ThemeSettingsRepository,
) : ViewModel() {
    val state: StateFlow<SettingsHubUiState> =
        themeSettingsRepository.observeThemeMode()
            .map { mode -> SettingsHubUiState(themeMode = mode) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsHubUiState(),
            )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themeSettingsRepository.setThemeMode(mode)
        }
    }
}
