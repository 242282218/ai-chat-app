package com.aichat.workbench.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLazyItemKeysTest {

    @Test
    fun chatLazyItemKey_distinguishesDuplicateImagePrefixes() {
        val seed = "data:image/png;base64," + "A".repeat(200)

        val first = chatLazyItemKey(prefix = "message-image", seed = seed, index = 0)
        val second = chatLazyItemKey(prefix = "message-image", seed = seed, index = 1)

        assertTrue(first.startsWith("message-image-"))
        assertTrue(second.startsWith("message-image-"))
        assertNotEquals(first, second)
    }

    @Test
    fun chatLazyItemKey_preservesPreviewPrefixForDiagnostics() {
        val seed = "https://example.com/repeated-result"

        val key = chatLazyItemKey(prefix = "search-citation", seed = seed, index = 3)

        assertEquals("search-citation-${seed.take(32)}-3", key)
    }
}
