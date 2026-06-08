package com.aichat.workbench.feature.chat

internal fun chatLazyItemKey(
    prefix: String,
    seed: String,
    index: Int,
): String = "$prefix-${seed.take(CHAT_LAZY_KEY_PREVIEW_LENGTH)}-$index"

private const val CHAT_LAZY_KEY_PREVIEW_LENGTH = 32
