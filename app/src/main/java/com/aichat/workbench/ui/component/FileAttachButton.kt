package com.aichat.workbench.ui.component

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.aichat.workbench.ui.theme.TextSecondary

@Composable
fun FileAttachButton(onFilePicked: (Uri) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onFilePicked)
    }
    IconButton(onClick = { launcher.launch(FileAttachMimeTypes) }) {
        Icon(Icons.Outlined.AttachFile, contentDescription = "附件", tint = TextSecondary)
    }
}

private val FileAttachMimeTypes = arrayOf(
    "text/*",
    "application/json",
    "application/xml",
    "application/javascript",
    "application/x-javascript",
    "application/x-sh",
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "image/*",
)
