package com.aichat.workbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.aichat.workbench.navigation.AppNavHost
import com.aichat.workbench.ui.theme.AiChatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AiChatTheme {
                AppNavHost()
            }
        }
    }
}
