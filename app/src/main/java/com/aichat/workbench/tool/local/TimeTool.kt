package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource
import java.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

class TimeTool(
    private val clock: Clock = Clock.systemUTC(),
) : LocalTool {
    override val descriptor: ToolDescriptor = TimeToolDescriptor

    override suspend fun execute(request: LocalToolRequest): LocalToolExecution =
        LocalToolExecution(
            output = ToolOutput.Json(
                localToolJson.encodeToString(TimeOutput(clock.instant().toString())),
            ),
        )
}

val TimeToolDescriptor: ToolDescriptor = ToolDescriptor(
    name = "time",
    displayName = "本机时间",
    description = "读取设备本地时间。",
    permissionLevel = ToolPermissionLevel.ReadOnly,
    inputSchemaJson = """{"type":"object","properties":{}}""",
    outputSchemaJson = """{"type":"object"}""",
    timeoutSeconds = null,
    source = ToolSource.BuiltIn,
    riskLevel = ToolRiskLevel.Low,
    requiresNetwork = false,
    requiresFileAccess = false,
    defaultPermissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
)

@Serializable
private data class TimeOutput(val currentTime: String)
