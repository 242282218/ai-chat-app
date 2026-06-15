package com.aichat.workbench.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aichat.workbench.app.AppDispatchers
import com.aichat.workbench.domain.repository.ImageGenerationPreferences
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.imageGenerationPreferencesDataStore by preferencesDataStore(
    name = IMAGE_GENERATION_PREFERENCES_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, IMAGE_GENERATION_PREFERENCES_NAME))
    },
)

class DataStoreImageGenerationPreferencesRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : ImageGenerationPreferencesRepository {
    constructor(
        context: Context,
        dispatchers: AppDispatchers,
    ) : this(
        dataStore = context.applicationContext.imageGenerationPreferencesDataStore,
        scope = CoroutineScope(SupervisorJob() + dispatchers.io),
    )

    private val preferences: StateFlow<ImageGenerationPreferences> =
        dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { values ->
                ImageGenerationPreferences(
                    providerId = values[ProviderIdKey]?.takeIf { it.isNotBlank() },
                )
            }
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ImageGenerationPreferences(),
            )

    override fun observePreferences(): StateFlow<ImageGenerationPreferences> =
        preferences

    override suspend fun saveSelectedProvider(providerId: String?) {
        dataStore.edit { values ->
            values.putOrRemove(ProviderIdKey, providerId)
        }
    }

    private fun MutablePreferences.putOrRemove(
        key: Preferences.Key<String>,
        value: String?,
    ) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank()) {
            remove(key)
        } else {
            this[key] = trimmed
        }
    }
}

private const val IMAGE_GENERATION_PREFERENCES_NAME = "image_generation_preferences"
private val ProviderIdKey = stringPreferencesKey("provider_id")
