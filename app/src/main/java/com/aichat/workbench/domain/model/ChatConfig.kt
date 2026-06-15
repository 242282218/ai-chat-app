package com.aichat.workbench.domain.model

data class ChatConfig(
    val messageWindowSize: Int = DEFAULT_MESSAGE_WINDOW_SIZE,
    val flushDeltaCount: Int = DEFAULT_FLUSH_DELTA_COUNT,
    val flushIntervalMillis: Long = DEFAULT_FLUSH_INTERVAL_MILLIS,
) {
    companion object {
        const val DEFAULT_MESSAGE_WINDOW_SIZE = 200
        const val DEFAULT_FLUSH_DELTA_COUNT = 10
        const val DEFAULT_FLUSH_INTERVAL_MILLIS = 500L
    }
}
