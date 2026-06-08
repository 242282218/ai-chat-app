package com.aichat.workbench.tool.runtime

internal class InvalidToolArgumentsException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

internal class GatewaySettingsException(
    val code: String,
    message: String,
) : RuntimeException(message)
