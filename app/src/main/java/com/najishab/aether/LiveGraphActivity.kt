package com.najishab.aether

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.najishab.aether.core.TrafficMonitor
import com.najishab.aether.data.ThemeMode
import com.najishab.aether.data.ThemeStore
import com.najishab.aether.ui.theme.AetherBlue
import com.najishab.aether.ui.theme.AetherSuccess
import com.najishab.aether.ui.theme.AetherTheme
import java.util.Locale

/**
 * Real-time download/upload speed. Sampling itself runs continuously in
 * [TrafficMonitor] from process start (see [AetherApp]), not from this
 * Activity, so the graph and the "usage this connection" total keep going
 * across the screen being closed and reopened instead of resetting to zero
 * each time - only a NEW VPN connection resets them.
 */
class LiveGraphActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeStore = ThemeStore(applicationContext)
        setContent {
            val themeMode by themeStore.mode.collectAsState(initial = ThemeMode.DARK)
            AetherTheme(themeMode = themeMode) {
                LiveGraphScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun LiveGraphScreen(onBack: () -> Unit) {
    val downHistory by TrafficMonitor.downHistory.collectAsState()
    val upHistory by TrafficMonitor.upHistory.collectAsState()
    val downSpeed by TrafficMonitor.downSpeedBps.collectAsState()
    val upSpeed by TrafficMonitor.upSpeedBps.collectAsState()
    val sessionBytes by TrafficMonitor.sessionBytes.collectAsState()

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
                    text = stringResource(R.string.live_graph_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    SpeedReadout(
                        label = stringResource(R.string.traffic_download),
                        bps = downSpeed,
                        color = AetherSuccess,
                    )
                    SpeedReadout(
                        label = stringResource(R.string.traffic_upload),
                        bps = upSpeed,
                        color = AetherBlue,
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.4f),
                ) {
                    SpeedGraph(
                        down = downHistory,
                        up = upHistory,
                        downColor = AetherSuccess,
                        upColor = AetherBlue,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.live_graph_session_usage, formatBytes(sessionBytes)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SpeedReadout(label: String, bps: Long, color: Color) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = formatSpeed(bps), style = MaterialTheme.typography.headlineSmall, color = color)
    }
}

@Composable
private fun SpeedGraph(
    down: List<Float>,
    up: List<Float>,
    downColor: Color,
    upColor: Color,
    modifier: Modifier = Modifier,
) {
    val maxValue = (down + up).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Canvas(modifier = modifier) {
        val stepX = size.width / (TrafficMonitor.HISTORY_SIZE - 1).coerceAtLeast(1)

        fun drawSeries(samples: List<Float>, color: Color) {
            if (samples.size < 2) return
            val path = androidx.compose.ui.graphics.Path()
            samples.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - (v / maxValue) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = color, style = Stroke(width = 4f, cap = StrokeCap.Round))
        }

        drawSeries(down, downColor)
        drawSeries(up, upColor)
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    val bitsPerSec = bytesPerSec * 8
    return when {
        bitsPerSec >= 1_000_000 -> String.format(Locale.US, "%.1f Mbps", bitsPerSec / 1_000_000.0)
        bitsPerSec >= 1_000 -> String.format(Locale.US, "%.0f Kbps", bitsPerSec / 1_000.0)
        else -> "$bitsPerSec bps"
    }
}

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
