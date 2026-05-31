package com.aichat.workbench.feature.image

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.workbench.domain.model.ImageGeneration
import com.aichat.workbench.domain.model.ImageGenerationStatus
import com.aichat.workbench.ui.component.MetadataRow
import com.aichat.workbench.ui.component.SectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchConfirmDialog
import com.aichat.workbench.ui.component.WorkbenchPanel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenerationScreen(
    onBack: () -> Unit,
    onOpenProviders: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImageGenerationViewModel = viewModel(factory = ImageGenerationViewModel.Factory),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var confirmClearHistory by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Images") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { confirmClearHistory = true },
                        enabled = state.generations.isNotEmpty() && !state.isGenerating,
                    ) {
                        Icon(imageVector = Icons.Filled.ClearAll, contentDescription = "Clear history")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ImageGenerationForm(
                    state = state,
                    onOpenProviders = onOpenProviders,
                    viewModel = viewModel,
                )
            }
            item {
                SectionHeader(
                    title = "History",
                    description = "Generated images are listed from local thumbnail files.",
                )
            }
            if (state.generations.isEmpty()) {
                item {
                    WorkbenchPanel(
                        title = "No image history",
                        description = "Completed generations will appear here with reuse, save, and share actions.",
                        icon = Icons.Filled.Image,
                    ) {
                        StatusPill(text = "Local thumbnails", tone = StatusTone.Success)
                    }
                }
            } else {
                items(state.generations, key = { it.id.value }) { generation ->
                    ImageGenerationRow(
                        generation = generation,
                        onReusePrompt = { viewModel.reusePrompt(generation.prompt) },
                        onRegenerate = { viewModel.regenerate(generation.prompt) },
                        onSave = { generation.originalPath?.let { saveImage(context, generation.id.value, it) } },
                        onShare = { generation.originalPath?.let { shareImage(context, it) } },
                    )
                }
            }
        }
    }

    if (confirmClearHistory) {
        WorkbenchConfirmDialog(
            title = "Clear image history?",
            message = "This removes ${state.generations.size} local image generation records and their stored files.",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClearHistory = false
                viewModel.clearHistory()
            },
            onDismiss = { confirmClearHistory = false },
        )
    }
}

@Composable
private fun ImageGenerationForm(
    state: ImageGenerationUiState,
    onOpenProviders: () -> Unit,
    viewModel: ImageGenerationViewModel,
) {
    WorkbenchPanel(
        title = "Image generation",
        description = "Create image requests directly through a configured provider.",
        icon = Icons.Filled.Image,
        trailing = {
            StatusPill(
                text = if (state.isGenerating) "Generating" else "Ready",
                tone = if (state.isGenerating) StatusTone.Accent else StatusTone.Success,
            )
        },
    ) {
        if (state.providers.none { it.enabled }) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "No enabled provider",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Configure a provider before generating images.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onOpenProviders,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Configure provider")
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.providers, key = { it.id.value }) { provider ->
                    AssistChip(
                        onClick = { viewModel.selectProvider(provider.id.value) },
                        label = {
                            Text(
                                text = provider.name,
                                modifier = Modifier.widthIn(max = 180.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            if (state.selectedProviderId == provider.id.value) {
                                Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        enabled = provider.enabled,
                    )
                }
            }
        }
        ImageFormSummary(state)

        OutlinedTextField(
            value = state.prompt,
            onValueChange = viewModel::updatePrompt,
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp),
            label = { Text(text = "Prompt") },
        )
        OutlinedTextField(
            value = state.model,
            onValueChange = viewModel::updateModel,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = "Model") },
            singleLine = true,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.size,
                onValueChange = viewModel::updateSize,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Size") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.quality,
                onValueChange = viewModel::updateQuality,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Quality") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.count,
                onValueChange = viewModel::updateCount,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Count") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        }
        if (state.selectedModelUnsupported) {
            ImageFormFeedback(
                label = "Model support",
                message = "Selected model does not advertise image generation support.",
                tone = StatusTone.Warning,
            )
        }
        Button(
            onClick = viewModel::generate,
            enabled = state.canGenerateImages(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Filled.Image, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = if (state.isGenerating) "Generating" else "Generate")
        }
        state.error?.let {
            ImageFormFeedback(
                label = "Generation error",
                message = it,
                tone = StatusTone.Critical,
            )
        }
    }
}

@Composable
private fun ImageFormSummary(state: ImageGenerationUiState) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            StatusPill(
                text = if (state.selectedProvider != null) "Provider ready" else "Provider needed",
                tone = if (state.selectedProvider != null) StatusTone.Success else StatusTone.Warning,
            )
        }
        item {
            StatusPill(
                text = if (state.prompt.isBlank()) "Prompt required" else "Prompt ready",
                tone = if (state.prompt.isBlank()) StatusTone.Warning else StatusTone.Success,
            )
        }
        item {
            StatusPill(
                text = state.imageCountLabel(),
                tone = state.imageCountTone(),
            )
        }
        item {
            StatusPill(
                text = state.imageModelLabel(),
                tone = state.imageModelTone(),
            )
        }
    }
}

@Composable
private fun ImageFormFeedback(
    label: String,
    message: String,
    tone: StatusTone,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusPill(text = label, tone = tone)
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImageGenerationRow(
    generation: ImageGeneration,
    onReusePrompt: () -> Unit,
    onRegenerate: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
) {
    WorkbenchPanel(
        title = generation.prompt.preview(72),
        description = imageGenerationMetadata(generation),
        icon = Icons.Filled.Image,
        trailing = {
            StatusPill(
                text = generation.status.name,
                tone = generation.statusTone(),
            )
        },
    ) {
        generation.thumbnailPath?.let { path ->
            LocalThumbnail(path = path)
        }
        MetadataRow(label = "Prompt", value = generation.prompt.preview(96))
        generation.errorSummary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onReusePrompt,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Reuse prompt")
            }
            OutlinedButton(
                onClick = onRegenerate,
                enabled = generation.status == ImageGenerationStatus.Completed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Replay, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Regenerate")
            }
            OutlinedButton(
                onClick = onShare,
                enabled = generation.status == ImageGenerationStatus.Completed &&
                    generation.originalPath != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Share")
            }
            OutlinedButton(
                onClick = onSave,
                enabled = generation.status == ImageGenerationStatus.Completed &&
                    generation.originalPath != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Filled.SaveAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Save")
            }
        }
    }
}

private fun imageGenerationMetadata(generation: ImageGeneration): String =
    listOfNotNull(
        generation.model,
        generation.size,
        generation.quality,
    ).joinToString(" / ").ifBlank { "No model metadata" }

private fun ImageGeneration.statusTone(): StatusTone =
    when (status) {
        ImageGenerationStatus.Completed -> StatusTone.Success
        ImageGenerationStatus.Failed -> StatusTone.Critical
        ImageGenerationStatus.Pending -> StatusTone.Accent
    }

private fun ImageGenerationUiState.canGenerateImages(): Boolean {
    val imageCount = imageCountOrNull()
    return !isGenerating &&
        selectedProvider != null &&
        prompt.isNotBlank() &&
        model.isNotBlank() &&
        imageCount != null &&
        imageCount in 1..4 &&
        !selectedModelUnsupported
}

private fun ImageGenerationUiState.imageCountOrNull(): Int? =
    count.trim().toIntOrNull()

private fun ImageGenerationUiState.imageCountLabel(): String {
    val parsedCount = imageCountOrNull()
    return when {
        count.isBlank() -> "Count required"
        parsedCount == null -> "Count invalid"
        parsedCount in 1..4 -> "$parsedCount image${if (parsedCount == 1) "" else "s"}"
        else -> "Count 1-4"
    }
}

private fun ImageGenerationUiState.imageCountTone(): StatusTone {
    val parsedCount = imageCountOrNull()
    return when {
        count.isBlank() -> StatusTone.Warning
        parsedCount != null && parsedCount in 1..4 -> StatusTone.Success
        else -> StatusTone.Critical
    }
}

private fun ImageGenerationUiState.imageModelLabel(): String =
    when {
        model.isBlank() -> "Model required"
        selectedModelUnsupported -> "Model unsupported"
        else -> "Model ready"
    }

private fun ImageGenerationUiState.imageModelTone(): StatusTone =
    when {
        model.isBlank() -> StatusTone.Warning
        selectedModelUnsupported -> StatusTone.Critical
        else -> StatusTone.Success
    }

@Composable
private fun LocalThumbnail(path: String) {
    val bitmap = remember(path) {
        BitmapFactory.decodeFile(path)?.asImageBitmap()
    }
    if (bitmap == null) {
        Text(
            text = "Thumbnail unavailable",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "Generated image preview",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun saveImage(context: Context, id: String, path: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        shareImage(context, path)
        return
    }
    val file = File(path)
    if (!file.exists()) return
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$id.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AI Chat")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return
    context.contentResolver.openOutputStream(uri)?.use { output ->
        file.inputStream().use { input -> input.copyTo(output) }
    }
}

private fun shareImage(context: Context, path: String) {
    val file = File(path)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share image"))
}

private fun String.preview(maxLength: Int): String {
    val normalized = trim()
    return if (normalized.length <= maxLength) {
        normalized
    } else {
        "${normalized.take(maxLength - 3)}..."
    }
}
