package com.najishab.aether

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.najishab.aether.core.AetherController
import com.najishab.aether.core.EndpointHistoryEntry
import com.najishab.aether.data.EndpointHistoryStore
import com.najishab.aether.data.ProfileStore
import com.najishab.aether.data.ThemeMode
import com.najishab.aether.data.ThemeStore
import com.najishab.aether.model.ConnectionProfile
import com.najishab.aether.model.EndpointMode
import com.najishab.aether.model.Protocol
import com.najishab.aether.ui.theme.AetherTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Endpoint Health Check & History (independent, modular screen — see
 * EndpointHistoryStore). Shows the recent endpoints the app has actually
 * completed a full self-tested connection to. Tapping one imports it into
 * Advanced Settings (protocol + Manual peer) and immediately attempts a
 * connection with it — reusing AetherController directly, so this works the
 * same whether the tunnel is currently idle or already connected to
 * something else.
 */
class EndpointHistoryActivity : AppCompatActivity() {

    private var pendingProfile: ConnectionProfile? = null

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val profile = pendingProfile
            pendingProfile = null
            if (result.resultCode == RESULT_OK && profile != null) {
                AetherController.connect(this, profile)
                openMainActivity()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeStore = ThemeStore(applicationContext)
        setContent {
            val themeMode by themeStore.mode.collectAsState(initial = ThemeMode.DARK)
            AetherTheme(themeMode = themeMode) {
                EndpointHistoryScreen(
                    onBack = { finish() },
                    onSelect = { entry -> applyAndConnect(entry) },
                )
            }
        }
    }

    private fun applyAndConnect(entry: EndpointHistoryEntry) {
        lifecycleScope.launch {
            val store = ProfileStore(applicationContext)
            val current = store.profile.first()
            val protocol = runCatching { Protocol.valueOf(entry.protocol) }.getOrDefault(current.protocol)

            // تشخیص خودکار IPv6 یا IPv4 از روی آدرس اندپوینت
            val isIpv6 = entry.endpoint.startsWith("[") ||
                    entry.endpoint.substringBeforeLast(':').contains(':')
            val targetIpVersion = if (isIpv6) com.najishab.aether.model.IpVersion.V6 else com.najishab.aether.model.IpVersion.V4

            val updated = current.copy(
                protocol = protocol,
                masqueHttp2 = protocol == Protocol.MASQUE_H2,
                ipVersion = targetIpVersion, // تنظیم خودکار نوع آی‌پی
                endpointMode = EndpointMode.MANUAL_PEER,
                manualPeer = entry.endpoint,
            )
            // ذخیره در تنظیمات
            store.save(updated)

            val consent = AetherController.prepare(this@EndpointHistoryActivity)
            if (consent != null) {
                pendingProfile = updated
                vpnPermissionLauncher.launch(consent)
            } else {
                AetherController.connect(this@EndpointHistoryActivity, updated)
                openMainActivity()
            }
        }
    }

    private fun openMainActivity() {
        // بازگشت مستقیم به صفحه خانه با بستن تمام منوهای بازشده
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
}

@Composable
private fun EndpointHistoryScreen(onBack: () -> Unit, onSelect: (EndpointHistoryEntry) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember(context) { EndpointHistoryStore(context) }
    val entries by store.history.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

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
                    text = stringResource(R.string.endpoint_history_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (entries.isNotEmpty()) {
                    IconButton(onClick = { scope.launch { store.clear() } }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.diag_clear),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NetworkCheck,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.endpoint_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(entries, key = { it.endpoint }) { entry ->
                        EndpointHistoryCard(entry = entry, onClick = { onSelect(entry) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointHistoryCard(entry: EndpointHistoryEntry, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.endpoint,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pingLabel(entry.pingMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${entry.protocol} · ${entry.network}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = relativeTime(context, entry.lastSuccessMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun pingLabel(ms: Long): String = if (ms >= 0) "${ms} ms" else "—"

private fun relativeTime(context: android.content.Context, millis: Long): String {
    if (millis <= 0L) return ""
    return DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}
