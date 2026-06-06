package com.aichat.workbench.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

data class AppDispatchers(
    val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    val io: CoroutineDispatcher = Dispatchers.IO,
    val default: CoroutineDispatcher = Dispatchers.Default,
)

/**
 * Application-lifetime CoroutineScope for work that must outlive a ViewModel's
 * viewModelScope (e.g. cleanup in onCleared, which runs after viewModelScope
 * is already cancelled). Survives configuration changes; only dies with the process.
 */
class ApplicationScope(
    dispatchers: AppDispatchers = AppDispatchers(),
) : CoroutineScope by CoroutineScope(SupervisorJob() + dispatchers.io)
