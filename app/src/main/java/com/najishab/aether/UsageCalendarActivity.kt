package com.najishab.aether

import android.app.Application
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
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.najishab.aether.core.AppCalendar
import com.najishab.aether.core.Formatters
import com.najishab.aether.data.DailyUsage
import com.najishab.aether.data.ThemeMode
import com.najishab.aether.data.ThemeStore
import com.najishab.aether.data.UsageStore
import com.najishab.aether.ui.theme.AetherBlue
import com.najishab.aether.ui.theme.AetherTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

class UsageCalendarActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeStore = ThemeStore(applicationContext)
        val viewModel = androidx.lifecycle.ViewModelProvider(this)[UsageCalendarViewModel::class.java]

        setContent {
            val themeMode by themeStore.mode.collectAsState(initial = ThemeMode.DARK)
            val state by viewModel.state.collectAsState()
            AetherTheme(themeMode = themeMode) {
                UsageCalendarScreen(state = state, onBack = { finish() })
            }
        }
    }
}

enum class UsageCalendarMode {
    THIS_MONTH,
    DOWNLOAD,
    UPLOAD,
    SESSIONS,
    THIS_WEEK,
}

data class UsageCalendarUiState(
    val days: List<DailyUsage> = emptyList(),
    val todayBytes: Long = 0L,
    val thisWeekTotalBytes: Long = 0L,
    val dailyAverageBytes: Long = 0L,
    val highestDay: DailyUsage? = null,
    val approximateSessions: Int = 0,
)

class UsageCalendarViewModel(app: Application) : AndroidViewModel(app) {
    val state: StateFlow<UsageCalendarUiState> = UsageStore(app).summary().map { summary ->
        UsageCalendarUiState(
            days = summary.history,
            todayBytes = summary.today.totalBytes,
            thisWeekTotalBytes = summary.thisWeekTotalBytes,
            dailyAverageBytes = summary.dailyAverageBytes,
            highestDay = summary.highestDay,
            approximateSessions = summary.sessionsByDay.values.sumOf { it.size },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UsageCalendarUiState())
}

@Composable
private fun UsageCalendarScreen(state: UsageCalendarUiState, onBack: () -> Unit) {
    var mode by remember { mutableStateOf(UsageCalendarMode.THIS_MONTH) }
    val locale = remember { Locale.getDefault() }
    val visibleDays = remember(mode, state.days, locale) {
        when (mode) {
            UsageCalendarMode.THIS_WEEK -> {
                val (start, end) = AppCalendar.thisWeekRange(locale)
                state.days.filter { it.dateKey in start..end }
            }
            UsageCalendarMode.THIS_MONTH -> {
                val (start, end) = AppCalendar.thisMonthRange(locale)
                state.days.filter { it.dateKey in start..end }
            }
            else -> state.days
        }
    }
    val maxValue = visibleDays.maxOfOrNull { it.metricValue(mode) }?.coerceAtLeast(1L) ?: 1L

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
                    modifier = Modifier.weight(1f),
                )
                ModeMenu(mode = mode, onModeChange = { mode = it })
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryGrid(state)
                Text(
                    text = stringResource(R.string.usage_calendar_approx_sessions),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (visibleDays.isEmpty()) {
                    Text(
                        text = stringResource(R.string.diag_empty_logs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (day in visibleDays.asReversed()) {
                    DayRow(day = day, mode = mode, maxValue = maxValue)
                }
            }
        }
    }
}

@Composable
private fun ModeMenu(mode: UsageCalendarMode, onModeChange: (UsageCalendarMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(text = mode.label())
            Icon(imageVector = Icons.Rounded.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UsageCalendarMode.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label()) },
                    onClick = {
                        expanded = false
                        onModeChange(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun SummaryGrid(state: UsageCalendarUiState) {
    val locale = remember { Locale.getDefault() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryItem(stringResource(R.string.usage_today), Formatters.formatBytes(state.todayBytes), Modifier.weight(1f))
            SummaryItem(stringResource(R.string.usage_last_7_total), Formatters.formatBytes(state.thisWeekTotalBytes), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryItem(stringResource(R.string.usage_daily_average), Formatters.formatBytes(state.dailyAverageBytes), Modifier.weight(1f))
            SummaryItem(
                stringResource(R.string.usage_highest_day),
                state.highestDay?.let { "${AppCalendar.formatDayLabel(it.dateKey, locale)} · ${Formatters.formatBytes(it.totalBytes)}" } ?: Formatters.formatBytes(0L),
                Modifier.weight(1f),
            )
        }
        SummaryItem(
            stringResource(R.string.usage_calendar_approx_sessions),
            state.approximateSessions.toString(),
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DayRow(day: DailyUsage, mode: UsageCalendarMode, maxValue: Long) {
    val locale = remember { Locale.getDefault() }
    val value = day.metricValue(mode)
    val fraction = (value.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = AppCalendar.formatDayLabel(day.dateKey, locale),
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
            text = if (mode == UsageCalendarMode.SESSIONS) {
                stringResource(R.string.usage_sessions_value, day.sessionCount, Formatters.formatBytes(day.totalBytes))
            } else {
                Formatters.formatBytes(value)
            },
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(104.dp),
        )
    }
}

@Composable
private fun UsageCalendarMode.label(): String = when (this) {
    UsageCalendarMode.THIS_MONTH -> stringResource(R.string.usage_mode_total)
    UsageCalendarMode.DOWNLOAD -> stringResource(R.string.usage_mode_download)
    UsageCalendarMode.UPLOAD -> stringResource(R.string.usage_mode_upload)
    UsageCalendarMode.SESSIONS -> stringResource(R.string.usage_mode_sessions)
    UsageCalendarMode.THIS_WEEK -> stringResource(R.string.usage_mode_last_7_days)
}

private fun DailyUsage.metricValue(mode: UsageCalendarMode): Long = when (mode) {
    UsageCalendarMode.THIS_MONTH,
    UsageCalendarMode.THIS_WEEK -> totalBytes
    UsageCalendarMode.DOWNLOAD -> downloadBytes
    UsageCalendarMode.UPLOAD -> uploadBytes
    UsageCalendarMode.SESSIONS -> sessionCount.toLong()
}
