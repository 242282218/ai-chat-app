package com.aichat.workbench.domain.repository

import com.aichat.workbench.domain.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

interface ThemeSettingsRepository {
    fun observeThemeMode(): StateFlow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
}
