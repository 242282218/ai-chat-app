package com.aichat.workbench.data.settings

import android.content.Context
import com.aichat.workbench.domain.repository.ImageGenerationPreferences
import com.aichat.workbench.domain.repository.ImageGenerationPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SharedPreferencesImageGenerationPreferencesRepository(
    context: Context,
) : ImageGenerationPreferencesRepository {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val settings = MutableStateFlow(readPreferences())

    override fun observePreferences(): StateFlow<ImageGenerationPreferences> =
        settings.asStateFlow()

    override suspend fun savePreferences(providerId: String?, model: String?) {
        preferences.edit()
            .putOrRemove(KEY_PROVIDER_ID, providerId)
            .putOrRemove(KEY_MODEL, model)
            .apply()
        settings.value = readPreferences()
    }

    private fun readPreferences(): ImageGenerationPreferences =
        ImageGenerationPreferences(
            providerId = preferences.getString(KEY_PROVIDER_ID, null)?.takeIf { it.isNotBlank() },
            model = preferences.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() },
        )

    private fun android.content.SharedPreferences.Editor.putOrRemove(
        key: String,
        value: String?,
    ): android.content.SharedPreferences.Editor =
        if (value.isNullOrBlank()) remove(key) else putString(key, value.trim())

    private companion object {
        const val PREFS_NAME = "image_generation_preferences"
        const val KEY_PROVIDER_ID = "provider_id"
        const val KEY_MODEL = "model"
    }
}
