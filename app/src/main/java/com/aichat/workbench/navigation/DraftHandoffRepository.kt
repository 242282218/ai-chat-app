package com.aichat.workbench.navigation

import java.util.UUID

class DraftHandoffRepository {
    private val drafts = LinkedHashMap<String, String>()

    fun put(draft: String): String {
        val id = UUID.randomUUID().toString()
        drafts[id] = draft
        trimOldDrafts()
        return id
    }

    fun take(id: String?): String? {
        if (id.isNullOrBlank()) return null
        return drafts.remove(id)
    }

    private fun trimOldDrafts() {
        while (drafts.size > MAX_DRAFTS) {
            drafts.remove(drafts.keys.first())
        }
    }

    private companion object {
        const val MAX_DRAFTS = 16
    }
}
