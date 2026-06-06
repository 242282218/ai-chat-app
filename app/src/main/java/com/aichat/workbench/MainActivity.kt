package com.aichat.workbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aichat.workbench.domain.repository.ThemeSettingsRepository
import com.aichat.workbench.navigation.AppNavHost
import com.aichat.workbench.ui.theme.AiChatTheme
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeRepository = koinInject<ThemeSettingsRepository>()
            val themeMode = themeRepository.observeThemeMode().collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()
            AiChatTheme(
                darkTheme = themeMode.value.useDarkTheme(systemDarkTheme),
            ) {
                AppNavHost()
            }
        }
    }
}
