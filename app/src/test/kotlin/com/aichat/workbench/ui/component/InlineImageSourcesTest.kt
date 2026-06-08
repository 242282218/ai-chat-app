package com.aichat.workbench.ui.component

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineImageSourcesTest {

    @Test
    fun resolveInlineImageSource_recognizesDataImages() {
        val source = resolveInlineImageSource("data:image/png;base64,AAAA")

        assertEquals(
            InlineImageSource.LocalContent("data:image/png;base64,AAAA"),
            source,
        )
    }

    @Test
    fun resolveInlineImageSource_recognizesRemoteUrls() {
        val source = resolveInlineImageSource("https://example.com/image.png")

        assertEquals(
            InlineImageSource.RemoteUrl("https://example.com/image.png"),
            source,
        )
    }

    @Test
    fun resolveInlineImageSource_recognizesExistingLocalFiles() {
        val tempFile = File.createTempFile("inline-image", ".png")
        try {
            val source = resolveInlineImageSource(tempFile.absolutePath)

            assertEquals(
                InlineImageSource.LocalContent(tempFile.absolutePath),
                source,
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun resolveInlineImageSource_rejectsUnsupportedStrings() {
        val source = resolveInlineImageSource("not-an-image")

        assertTrue(source === InlineImageSource.Unsupported)
    }
}
