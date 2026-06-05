package com.aichat.workbench.tool.local

import com.aichat.workbench.domain.model.ToolOutput
import com.aichat.workbench.domain.model.ToolPermissionLevel
import com.aichat.workbench.tool.model.ToolDescriptor
import com.aichat.workbench.tool.model.ToolPermissionPolicy
import com.aichat.workbench.tool.model.ToolRiskLevel
import com.aichat.workbench.tool.model.ToolSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

class CodeDiffPreviewTool : LocalTool {
    override val descriptor: ToolDescriptor = CodeDiffPreviewToolDescriptor

    override suspend fun execute(request: LocalToolRequest): LocalToolExecution {
        val args = decodeLocalToolArguments<CodeDiffPreviewArguments>(request.toolCall.arguments)
        if (args.original.isBlank() && args.modified.isBlank()) {
            throw InvalidLocalToolArgumentsException("original 和 modified 不能同时为空。")
        }
        val originalLines = args.original.lines()
        val modifiedLines = args.modified.lines()
        val output = CodeDiffPreviewOutput(
            fileName = args.fileName.trim().ifBlank { "snippet" },
            additions = modifiedLines.countChangedFrom(originalLines),
            deletions = originalLines.countChangedFrom(modifiedLines),
            diff = unifiedDiff(args.fileName.trim().ifBlank { "snippet" }, originalLines, modifiedLines),
        )
        return LocalToolExecution(ToolOutput.Json(localToolJson.encodeToString(output)))
    }

    private fun unifiedDiff(
        fileName: String,
        originalLines: List<String>,
        modifiedLines: List<String>,
    ): String {
        if (originalLines == modifiedLines) return "No changes."
        val prefixSize = commonPrefixSize(originalLines, modifiedLines)
        val suffixSize = commonSuffixSize(originalLines, modifiedLines, prefixSize)
        val originalChanged = originalLines.changedSlice(prefixSize, suffixSize)
        val modifiedChanged = modifiedLines.changedSlice(prefixSize, suffixSize)

        return buildString {
            appendLine("--- $fileName")
            appendLine("+++ $fileName")
            appendLine("@@ -${diffRange(prefixSize, originalChanged.size)} +${diffRange(prefixSize, modifiedChanged.size)} @@")
            originalChanged.forEach { appendLine("-$it") }
            modifiedChanged.forEach { appendLine("+$it") }
        }.trimEnd()
    }

    private fun commonPrefixSize(left: List<String>, right: List<String>): Int {
        var index = 0
        while (index < left.size && index < right.size && left[index] == right[index]) {
            index += 1
        }
        return index
    }

    private fun commonSuffixSize(left: List<String>, right: List<String>, prefixSize: Int): Int {
        var count = 0
        while (
            count + prefixSize < left.size &&
            count + prefixSize < right.size &&
            left[left.lastIndex - count] == right[right.lastIndex - count]
        ) {
            count += 1
        }
        return count
    }

    private fun List<String>.changedSlice(prefixSize: Int, suffixSize: Int): List<String> {
        val end = size - suffixSize
        return if (prefixSize >= end) emptyList() else subList(prefixSize, end)
    }

    private fun diffRange(prefixSize: Int, changedCount: Int): String {
        val start = if (changedCount == 0) prefixSize else prefixSize + 1
        return "$start,$changedCount"
    }

    private fun List<String>.countChangedFrom(other: List<String>): Int {
        val prefixSize = commonPrefixSize(this, other)
        val suffixSize = commonSuffixSize(this, other, prefixSize)
        return (size - prefixSize - suffixSize).coerceAtLeast(0)
    }
}

val CodeDiffPreviewToolDescriptor: ToolDescriptor = ToolDescriptor(
    name = "code_diff_preview",
    displayName = "代码 Diff 预览",
    description = "本地生成代码差异预览，不写入文件。",
    permissionLevel = ToolPermissionLevel.ReadOnly,
    inputSchemaJson = """
        {
          "type": "object",
          "required": ["original", "modified"],
          "properties": {
            "fileName": { "type": "string" },
            "original": { "type": "string" },
            "modified": { "type": "string" }
          }
        }
    """.trimIndent(),
    outputSchemaJson = """{"type":"object"}""",
    timeoutSeconds = null,
    source = ToolSource.BuiltIn,
    riskLevel = ToolRiskLevel.Low,
    requiresNetwork = false,
    requiresFileAccess = false,
    defaultPermissionPolicy = ToolPermissionPolicy.AllowWithoutPrompt,
)

@Serializable
private data class CodeDiffPreviewArguments(
    val fileName: String = "snippet",
    val original: String = "",
    val modified: String = "",
)

@Serializable
private data class CodeDiffPreviewOutput(
    val fileName: String,
    val additions: Int,
    val deletions: Int,
    val diff: String,
)
