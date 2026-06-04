package com.aichat.workbench.tool.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAuthorizedFileReaderTest {
    @Test
    fun unsupportedFileReadReason_allowsTextMarkdownJsonAndCodeFiles() {
        assertNull(unsupportedFileReadReason("text/plain", "notes.txt"))
        assertNull(unsupportedFileReadReason("application/json", "data.json"))
        assertNull(unsupportedFileReadReason("application/octet-stream", "README.md"))
        assertNull(unsupportedFileReadReason(null, "Main.kt"))
        assertNull(unsupportedFileReadReason("", "script.py"))
    }

    @Test
    fun unsupportedFileReadReason_reportsImagesAsMetadataOnly() {
        assertEquals(
            "图片文件第一阶段只读取元信息；发送给模型前需要单独确认。",
            unsupportedFileReadReason("image/png", "photo.png"),
        )
    }

    @Test
    fun unsupportedFileReadReason_rejectsPdfAndOfficeDocuments() {
        assertEquals(
            "PDF 第一阶段暂不支持解析，请选择文本、Markdown、JSON 或代码文件。",
            unsupportedFileReadReason("application/pdf", "brief.pdf"),
        )
        assertEquals(
            "DOCX 等 Office 文件第一阶段暂不支持解析，请先导出为文本或 Markdown。",
            unsupportedFileReadReason(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "brief.docx",
            ),
        )
    }

    @Test
    fun unsupportedFileReadReason_rejectsUnknownTypes() {
        assertEquals(
            "无法识别文件类型，请选择文本、Markdown、JSON 或代码文件。",
            unsupportedFileReadReason(null, null),
        )
        assertEquals(
            "当前文件类型暂不支持解析，请选择文本、Markdown、JSON 或代码文件。",
            unsupportedFileReadReason("application/octet-stream", "archive.bin"),
        )
    }
}
