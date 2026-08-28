package com.najishab.aether.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.najishab.aether.R
import com.najishab.aether.data.ThemeMode

/**
 * "More" bottom sheet content, reachable from the Tune button on the home
 * screen: theme choice, and shortcuts to Live Graph / Usage Calendar.
 * Advanced connection settings live in the Drawer only (see HomeScreen).
 */
@Composable
fun MorePanel(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenLiveGraph: () -> Unit,
    onOpenUsageCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.more_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.theme_setting),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        ThemeOptionRow(
            label = stringResource(R.string.theme_dark),
            selected = themeMode == ThemeMode.DARK,
            onSelect = { onThemeModeChange(ThemeMode.DARK) },
        )
        ThemeOptionRow(
            label = stringResource(R.string.theme_light),
            selected = themeMode == ThemeMode.LIGHT,
            onSelect = { onThemeModeChange(ThemeMode.LIGHT) },
        )
        ThemeOptionRow(
            label = stringResource(R.string.theme_system),
            selected = themeMode == ThemeMode.SYSTEM,
            onSelect = { onThemeModeChange(ThemeMode.SYSTEM) },
        )

        Spacer(Modifier.height(20.dp))

        MoreRow(
            icon = Icons.AutoMirrored.Rounded.ShowChart,
            title = stringResource(R.string.live_graph_title),
            subtitle = stringResource(R.string.live_graph_subtitle),
            onClick = onOpenLiveGraph,
        )

        Spacer(Modifier.height(12.dp))

        MoreRow(
            icon = Icons.Rounded.CalendarMonth,
            title = stringResource(R.string.usage_calendar_title),
            subtitle = stringResource(R.string.usage_calendar_subtitle),
            onClick = onOpenUsageCalendar,
        )
    }
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun MoreRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
