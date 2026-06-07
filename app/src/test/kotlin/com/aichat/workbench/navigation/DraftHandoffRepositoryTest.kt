package com.aichat.workbench.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DraftHandoffRepositoryTest {
    @Test
    fun takeReturnsDraftOnce() {
        val repository = DraftHandoffRepository()
        val id = repository.put("long draft")

        assertEquals("long draft", repository.take(id))
        assertNull(repository.take(id))
    }

    @Test
    fun putTrimsOldDrafts() {
        val repository = DraftHandoffRepository()
        val firstId = repository.put("draft-0")
        repeat(16) { index ->
            repository.put("draft-${index + 1}")
        }

        assertNull(repository.take(firstId))
    }
}
