package com.aichat.workbench.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aichat.workbench.navigation.AppDestination
import com.aichat.workbench.ui.component.IconTile
import com.aichat.workbench.ui.component.SectionHeader
import com.aichat.workbench.ui.component.StatusPill
import com.aichat.workbench.ui.component.StatusTone
import com.aichat.workbench.ui.component.WorkbenchHero

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    destinations: List<AppDestination>,
    onDestinationClick: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "AI Chat")
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                WorkbenchHero(
                    eyebrow = "Local-first workbench",
                    title = "AI Chat",
                    description = "A native workspace for model routing, prompts, images, search, and sandboxed tools.",
                    icon = Icons.Filled.AutoAwesome,
                ) {
                    StatusPill(text = "Local", tone = StatusTone.Success)
                    StatusPill(text = "BYOK", tone = StatusTone.Neutral)
                    StatusPill(text = "Tools", tone = StatusTone.Accent)
                }
            }

            item {
                TrustStrip()
            }

            item {
                SectionHeader(
                    title = "Workspace",
                    description = "Start with a focused task, then tune providers and tools when needed.",
                )
            }

            items(destinations, key = { it.route }) { destination ->
                DestinationRow(
                    destination = destination,
                    onClick = { onDestinationClick(destination) },
                )
            }
        }
    }
}

@Composable
private fun TrustStrip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrustMetric(
            icon = Icons.Filled.Lock,
            label = "Local data",
            value = "Private by default",
            modifier = Modifier.weight(1f),
        )
        TrustMetric(
            icon = Icons.Filled.Shield,
            label = "Gateway",
            value = "Optional tools",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrustMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconTile(icon = icon, tone = StatusTone.Success)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DestinationRow(
    destination: AppDestination,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = destination.icon(),
                tone = destination.tone(),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = destination.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = destination.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusPill(
                text = destination.badge(),
                tone = destination.tone(),
            )
        }
    }
}

private fun AppDestination.icon(): ImageVector =
    when (this) {
        AppDestination.Chat -> Icons.AutoMirrored.Filled.Chat
        AppDestination.Providers -> Icons.Filled.Tune
        AppDestination.Prompts -> Icons.AutoMirrored.Filled.ViewList
        AppDestination.Images -> Icons.Filled.Image
        AppDestination.Tools -> Icons.Filled.Extension
        AppDestination.Settings -> Icons.Filled.Settings
        AppDestination.Home -> Icons.Filled.AutoAwesome
    }

private fun AppDestination.badge(): String =
    when (this) {
        AppDestination.Chat -> "Core"
        AppDestination.Providers -> "BYOK"
        AppDestination.Prompts -> "Local"
        AppDestination.Images -> "Creative"
        AppDestination.Tools -> "Gateway"
        AppDestination.Settings -> "Privacy"
        AppDestination.Home -> "Home"
    }

private fun AppDestination.tone(): StatusTone =
    when (this) {
        AppDestination.Chat -> StatusTone.Accent
        AppDestination.Providers -> StatusTone.Success
        AppDestination.Prompts -> StatusTone.Neutral
        AppDestination.Images -> StatusTone.Warning
        AppDestination.Tools -> StatusTone.Warning
        AppDestination.Settings -> StatusTone.Success
        AppDestination.Home -> StatusTone.Accent
    }
