package com.aichat.workbench.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aichat.workbench.app.AppDispatchers
import com.aichat.workbench.domain.model.ThemeMode
import com.aichat.workbench.domain.repository.ThemeSettingsRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.themeSettingsDataStore by preferencesDataStore(
    name = THEME_SETTINGS_NAME,
)

class DataStoreThemeSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : ThemeSettingsRepository {
    constructor(
        context: Context,
        dispatchers: AppDispatchers,
    ) : this(
        dataStore = context.applicationContext.themeSettingsDataStore,
        scope = CoroutineScope(SupervisorJob() + dispatchers.io),
    )

    private val themeMode: StateFlow<ThemeMode> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { values -> ThemeMode.fromStorage(values[ThemeModeKey]) }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = ThemeMode.System,
            )

    override fun observeThemeMode(): StateFlow<ThemeMode> =
        themeMode

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { values ->
            if (mode == ThemeMode.System) {
                values.remove(ThemeModeKey)
            } else {
                values[ThemeModeKey] = mode.name
            }
        }
    }
}

private const val THEME_SETTINGS_NAME = "theme_settings"
private val ThemeModeKey = stringPreferencesKey("theme_mode")
