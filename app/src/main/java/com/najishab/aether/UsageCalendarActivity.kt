package com.najishab.aether

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.najishab.aether.data.DailyUsage
import com.najishab.aether.data.ThemeMode
import com.najishab.aether.data.ThemeStore
import com.najishab.aether.data.UsageStore
import com.najishab.aether.ui.theme.AetherBlue
import com.najishab.aether.ui.theme.AetherTheme
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Daily device-wide data usage history (see [UsageStore] for what's measured
 * and its caveats), shown as a simple bar-per-day list for the last 35 days.
 */
class UsageCalendarActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeStore = ThemeStore(applicationContext)
        val usageStore = UsageStore(applicationContext)

        setContent {
            val themeMode by themeStore.mode.collectAsState(initial = ThemeMode.DARK)
            val history by usageStore.history().collectAsState(initial = emptyList())
            AetherTheme(themeMode = themeMode) {
                UsageCalendarScreen(history = history, onBack = { finish() })
            }
        }
    }
}

@Composable
private fun UsageCalendarScreen(history: List<DailyUsage>, onBack: () -> Unit) {
    val maxBytes = history.maxOfOrNull { it.bytes }?.coerceAtLeast(1L) ?: 1L

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.usage_calendar_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Text(
                text = stringResource(R.string.usage_calendar_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (history.isEmpty()) {
                    Text(
                        text = stringResource(R.string.diag_empty_logs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (day in history.asReversed()) {
                    DayRow(day = day, maxBytes = maxBytes)
                }
            }
        }
    }
}

@Composable
private fun DayRow(day: DailyUsage, maxBytes: Long) {
    val fraction = (day.bytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = formatDayLabel(day.dateKey),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(9.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxSize()
                    .background(AetherBlue, RoundedCornerShape(9.dp)),
            )
        }
        Text(
            text = formatBytes(day.bytes),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(72.dp),
        )
    }
}

private fun formatDayLabel(dateKey: String): String = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey)
    SimpleDateFormat("MMM d", Locale.getDefault()).format(parsed!!)
}.getOrDefault(dateKey)

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
