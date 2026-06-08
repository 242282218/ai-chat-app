package com.aichat.workbench.tool.runtime

import com.aichat.workbench.domain.model.MessagePart
import com.aichat.workbench.domain.model.ToolOutput

internal data class ExecutedToolOutput(
    val output: ToolOutput,
    val contentParts: List<MessagePart> = emptyList(),
)
