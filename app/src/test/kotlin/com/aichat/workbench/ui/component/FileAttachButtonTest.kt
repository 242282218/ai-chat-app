package com.aichat.workbench.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileAttachButtonTest {
    @Test
    fun fileAttachMimeTypesOnlyAdvertiseFirstPhaseTextReadableFiles() {
        assertTrue(FileAttachMimeTypes.contains("text/*"))
        assertTrue(FileAttachMimeTypes.contains("application/json"))
        assertTrue(FileAttachMimeTypes.contains("application/typescript"))
        assertTrue(FileAttachMimeTypes.contains("application/x-python-code"))
        assertTrue(FileAttachMimeTypes.contains("text/markdown"))

        assertFalse(FileAttachMimeTypes.contains("image/*"))
        assertFalse(FileAttachMimeTypes.contains("application/pdf"))
        assertFalse(FileAttachMimeTypes.contains("application/msword"))
        assertFalse(FileAttachMimeTypes.contains("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }
}
