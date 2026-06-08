package com.aichat.workbench.agent.skill

import com.aichat.workbench.domain.model.Skill
import com.aichat.workbench.domain.model.SkillId

class SkillFileParser {
    fun parse(id: SkillId, source: String): Skill {
        val parsed = source.parseSkillSource()
        val name = parsed.metadata["name"]?.takeIf(String::isNotBlank)
            ?: id.value
        val description = parsed.metadata["description"]?.takeIf(String::isNotBlank)
            ?: name
        val prompt = parsed.body.trim()
        require(prompt.isNotBlank()) { "Skill ${id.value} prompt 不能为空。" }
        return Skill(
            id = id,
            name = name,
            description = description,
            summary = parsed.metadata["summary"]?.takeIf(String::isNotBlank)
                ?: prompt.toSkillSummary(),
            prompt = prompt,
        )
    }

    private fun String.parseSkillSource(): ParsedSkillSource {
        val normalized = replace("\r\n", "\n")
        if (!normalized.startsWith("---\n")) {
            return ParsedSkillSource(metadata = emptyMap(), body = normalized)
        }
        val end = normalized.indexOf("\n---", startIndex = 4)
        if (end < 0) {
            return ParsedSkillSource(metadata = emptyMap(), body = normalized)
        }
        val metadata = normalized.substring(4, end)
            .lineSequence()
            .mapNotNull { line -> line.toMetadataEntry() }
            .toMap()
        val bodyStart = end + "\n---".length
        return ParsedSkillSource(
            metadata = metadata,
            body = normalized.substring(bodyStart).trimStart('\n'),
        )
    }

    private fun String.toMetadataEntry(): Pair<String, String>? {
        val separator = indexOf(':')
        if (separator <= 0) return null
        val key = take(separator).trim()
        val value = drop(separator + 1).trim().trim('"')
        if (key.isBlank()) return null
        return key to value
    }

    private fun String.toSkillSummary(): String =
        lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .take(SUMMARY_LIMIT)

    private data class ParsedSkillSource(
        val metadata: Map<String, String>,
        val body: String,
    )

    private companion object {
        const val SUMMARY_LIMIT = 280
    }
}
