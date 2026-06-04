package com.aichat.workbench.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aichat.workbench.ui.theme.Accent
import com.aichat.workbench.ui.theme.TextSecondary

@Composable
fun AppBottomBar(
    currentRoute: String?,
    onTabSelected: (AppDestination) -> Unit,
) {
    NavigationBar(
        tonalElevation = 0.dp,
        modifier = Modifier.height(64.dp),
    ) {
        bottomTabItems.forEach { tab ->
            val selected = currentRoute == tab.destination.route
            val tint by animateColorAsState(
                targetValue = if (selected) Accent else TextSecondary,
                label = "tab_tint",
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab.destination) },
                icon = { Icon(tab.icon, contentDescription = tab.label, tint = tint) },
                label = { Text(tab.label, color = tint) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }
    }
}
