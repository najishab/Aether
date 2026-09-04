package com.najishab.aether

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.najishab.aether.core.DiagnosticsLog
import com.najishab.aether.core.ScoutIpVersion
import com.najishab.aether.core.ScoutMode
import com.najishab.aether.core.ScoutOptions
import com.najishab.aether.core.ScoutProtocol
import com.najishab.aether.core.ScoutResult
import com.najishab.aether.core.WarpScoutRunner
import com.najishab.aether.data.ProfileStore
import com.najishab.aether.data.ThemeMode
import com.najishab.aether.data.ThemeStore
import com.najishab.aether.model.EndpointMode
import com.najishab.aether.model.IpVersion
import com.najishab.aether.model.Noize
import com.najishab.aether.model.Protocol as ModelProtocol
import com.najishab.aether.ui.theme.AetherTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class EndpointScannerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themeStore = ThemeStore(applicationContext)
        val runner = WarpScoutRunner(applicationInfo.nativeLibraryDir, filesDir)
        val profileStore = ProfileStore(applicationContext)
        setContent {
            val themeMode by themeStore.mode.collectAsState(initial = ThemeMode.DARK)
            AetherTheme(themeMode = themeMode) {
                EndpointScannerScreen(
                    runner = runner,
                    profileStore = profileStore,
                    onBack = { finish() },
                )
            }
        }
    }
}

private enum class ScannerProtocol(val title: String) {
    MASQUE("MASQUE"),
    MASQUE_H2("MASQUE-H2"),
    WIREGUARD("WireGuard"),
    AMNEZIAWG("AmneziaWG"),
}

private enum class ScannerMode(val titleRes: Int, val subtitleRes: Int) {
    STANDARD(R.string.scanner_mode_standard, R.string.scanner_mode_standard_desc),
    DURABLE(R.string.scanner_mode_durable, R.string.scanner_mode_durable_desc),
    FULL(R.string.scanner_mode_full, R.string.scanner_mode_full_desc),
}

private enum class ScoutIpChoice(val titleRes: Int) {
    IPV4(R.string.scanner_ip_v4),
    IPV6(R.string.scanner_ip_v6),
    BOTH(R.string.scanner_ip_both),
}

private data class ScoutEndpoint(
    val endpoint: String,
    val endpointPing: String,
    val tunnelPing: String,
    val loss: String,
    val seenAs: String,
    val node: String,
    val nodeLocation: String,
    val healthy: Boolean,
)

private fun ScoutResult.toUi(): ScoutEndpoint = ScoutEndpoint(
    endpoint = endpoint,
    endpointPing = if (endpointPingMs > 0) "${endpointPingMs.toInt()}ms" else "?",
    tunnelPing = if (tunPingMeasured) "${tunPingMs.toInt()}ms" else "-",
    loss = if (tunPingMeasured) "${lossPct.toInt()}%" else "-",
    seenAs = seenAsIso.ifBlank { seenAs },
    node = node,
    nodeLocation = nodeLocation,
    healthy = working,
)

private sealed class ScanUiState {
    data object Idle : ScanUiState()
    data object Scanning : ScanUiState()
    data class Done(val endpoints: List<ScoutEndpoint>, val probed: Int) : ScanUiState()
    data class Failed(val message: String) : ScanUiState()
}

private object ScannerCache {
    private const val FILE_NAME = "cached_scout_results.json"

    fun save(context: Context, endpoints: List<ScoutEndpoint>, probed: Int) {
        runCatching {
            val root = JSONObject()
            root.put("probed", probed)
            val arr = JSONArray()
            for (ep in endpoints) {
                val obj = JSONObject().apply {
                    put("endpoint", ep.endpoint)
                    put("endpointPing", ep.endpointPing)
                    put("tunnelPing", ep.tunnelPing)
                    put("loss", ep.loss)
                    put("seenAs", ep.seenAs)
                    put("node", ep.node)
                    put("nodeLocation", ep.nodeLocation)
                    put("healthy", ep.healthy)
                }
                arr.put(obj)
            }
            root.put("endpoints", arr)
            File(context.filesDir, FILE_NAME).writeText(root.toString())
        }
    }

    fun load(context: Context): Pair<List<ScoutEndpoint>, Int>? {
        return runCatching {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists() || file.length() == 0L) return null
            val root = JSONObject(file.readText())
            val probed = root.optInt("probed", 0)
            val arr = root.optJSONArray("endpoints") ?: return null
            val list = mutableListOf<ScoutEndpoint>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ScoutEndpoint(
                        endpoint = o.getString("endpoint"),
                        endpointPing = o.optString("endpointPing", "?"),
                        tunnelPing = o.optString("tunnelPing", "-"),
                        loss = o.optString("loss", "-"),
                        seenAs = o.optString("seenAs", ""),
                        node = o.optString("node", ""),
                        nodeLocation = o.optString("nodeLocation", ""),
                        healthy = o.optBoolean("healthy", true),
                    )
                )
            }
            list to probed
        }.getOrNull()
    }
}

private val ScreenBg = Color(0xFF070B14)
private val CardBg = Color(0xFF0C1322)
private val CardBorder = Color(0xFF16233B)
private val AccentBlue = Color(0xFF3865F6)
private val UnselectedBg = Color(0xFF0E1729)
private val SubtextColor = Color(0xFF8896B3)

@Composable
private fun CardContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = SubtextColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun EndpointScannerScreen(
    runner: WarpScoutRunner,
    profileStore: ProfileStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val cachedData = remember { ScannerCache.load(context) }

    var protocol by remember { mutableStateOf(ScannerProtocol.MASQUE_H2) }
    var scanMode by remember { mutableStateOf(ScannerMode.STANDARD) }
    var ipChoice by remember { mutableStateOf(ScoutIpChoice.IPV4) }
    var port by remember { mutableStateOf("") }
    var timeoutSec by remember { mutableIntStateOf(5) }
    var parallelJobs by remember { mutableIntStateOf(50) }

    var uiState by remember {
        mutableStateOf<ScanUiState>(
            if (cachedData != null && cachedData.first.isNotEmpty()) {
                ScanUiState.Done(cachedData.first, cachedData.second)
            } else {
                ScanUiState.Idle
            }
        )
    }
    var progressPercent by remember { mutableIntStateOf(0) }
    var liveEndpoints by remember {
        mutableStateOf<List<ScoutEndpoint>>(cachedData?.first.orEmpty())
    }
    var probedCount by remember {
        mutableIntStateOf(cachedData?.second ?: 0)
    }

    val scope = rememberCoroutineScope()
    var scanJob by remember { mutableStateOf<Job?>(null) }

    val currentEndpoints = if (uiState is ScanUiState.Scanning) liveEndpoints else (uiState as? ScanUiState.Done)?.endpoints.orEmpty()
    val working = currentEndpoints.count { it.healthy }
    val countries = currentEndpoints.filter { it.healthy }.map { it.seenAs }.distinct().count()
    val bestPing = currentEndpoints.filter { it.healthy }
        .mapNotNull { it.tunnelPing.removeSuffix("ms").toIntOrNull() ?: it.endpointPing.removeSuffix("ms").toIntOrNull() }
        .minOrNull()?.let { "${it}ms" } ?: "-"
    val scanning = uiState is ScanUiState.Scanning

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scope.launch(Dispatchers.IO) {
            runner.cancel()
        }
        // مرتب‌سازی نتایج بر اساس کمترین پینگ هنگام توقف دستی
        val sorted = liveEndpoints.sortedByPing()
        liveEndpoints = sorted
        ScannerCache.save(context, sorted, probedCount)
        uiState = ScanUiState.Done(sorted, probedCount)
        DiagnosticsLog.i("scout", "Scan stopped by user.")
    }

    fun startScan() {
        if (scanning) return
        uiState = ScanUiState.Scanning
        progressPercent = 0
        liveEndpoints = emptyList()
        probedCount = 0

        scanJob = scope.launch {
            val opts = ScoutOptions(
                protocol = when (protocol) {
                    ScannerProtocol.WIREGUARD -> ScoutProtocol.WIREGUARD
                    ScannerProtocol.AMNEZIAWG -> ScoutProtocol.AMNEZIAWG
                    ScannerProtocol.MASQUE -> ScoutProtocol.MASQUE
                    ScannerProtocol.MASQUE_H2 -> ScoutProtocol.MASQUE_H2
                },
                mode = when (scanMode) {
                    ScannerMode.STANDARD -> ScoutMode.STANDARD
                    ScannerMode.DURABLE -> ScoutMode.DURABLE
                    ScannerMode.FULL -> ScoutMode.FULL
                },
                ipVersion = when (ipChoice) {
                    ScoutIpChoice.IPV4 -> ScoutIpVersion.V4
                    ScoutIpChoice.IPV6 -> ScoutIpVersion.V6
                    ScoutIpChoice.BOTH -> ScoutIpVersion.BOTH
                },
                port = port.toIntOrNull(),
                timeoutSec = timeoutSec,
                parallelJobs = parallelJobs,
            )

            val outcome = withContext(Dispatchers.IO) {
                runner.ensureRegistered()
                    .onFailure { DiagnosticsLog.e("scout", "register failed: ${it.message}") }
                    .mapCatching {
                        runner.scan(
                            opts,
                            onProgress = { done, total ->
                                probedCount = done
                                if (total > 0) {
                                    progressPercent = ((done.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
                                }
                            },
                            onEndpointFound = { result ->
                                val ep = result.toUi()
                                val updated = (liveEndpoints.filter { it.endpoint != ep.endpoint } + ep)
                                liveEndpoints = updated
                                ScannerCache.save(context, updated, probedCount)
                            },
                        ).getOrThrow()
                    }
            }

            uiState = outcome.fold(
                onSuccess = { report ->
                    // مرتب‌سازی خودکار نتایج از کمترین پینگ به بیشترین
                    val finalEndpoints = report.results.map { it.toUi() }.sortedByPing()
                    liveEndpoints = finalEndpoints
                    ScannerCache.save(context, finalEndpoints, report.probedCount)
                    ScanUiState.Done(finalEndpoints, report.probedCount)
                },
                onFailure = { error ->
                    ScanUiState.Failed(formatScannerError(context, error.message))
                },
            )
            scanJob = null
        }
    }

    // با کلیک روی اندپوینت، نوع آی‌پی (IPv4 یا IPv6) به طور خودکار تشخیص داده شده و ذخیره می‌شود
    fun useEndpoint(endpoint: ScoutEndpoint) {
        scope.launch {
            val current = profileStore.profile.first()

            val isIpv6 = endpoint.endpoint.startsWith("[") ||
                    endpoint.endpoint.substringBeforeLast(':').contains(':')
            val targetIpVersion = if (isIpv6) IpVersion.V6 else IpVersion.V4

            profileStore.save(
                current.copy(
                    // اکنون MASQUE_H2 مستقیماً به عنوان پروتکل اصلی ست می‌شود
                    protocol = when (protocol) {
                        ScannerProtocol.MASQUE -> ModelProtocol.MASQUE
                        ScannerProtocol.MASQUE_H2 -> ModelProtocol.MASQUE_H2 // این خط اصلاح شد
                        ScannerProtocol.WIREGUARD, ScannerProtocol.AMNEZIAWG -> ModelProtocol.WIREGUARD
                    },
                    masqueHttp2 = protocol == ScannerProtocol.MASQUE_H2,
                    ipVersion = targetIpVersion,
                    noize = if (protocol == ScannerProtocol.AMNEZIAWG && current.noize == Noize.OFF) {
                        Noize.FIREWALL
                    } else {
                        current.noize
                    },
                    endpointMode = EndpointMode.MANUAL_PEER,
                    manualPeer = endpoint.endpoint,
                ),
            )

            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.scanner_settings_applied),
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                val intent = android.content.Intent(context, MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
                (context as? android.app.Activity)?.finish()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = ScreenBg) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                HeaderWithRadar(onBack = onBack)
            }

            item {
                ProtocolSelectionCard(
                    selected = protocol,
                    onSelect = { protocol = it },
                )
            }

            item {
                ScanSettingsCard(
                    scanMode = scanMode,
                    onScanModeChange = { scanMode = it },
                    ipChoice = ipChoice,
                    onIpChoiceChange = { ipChoice = it },
                    port = port,
                    onPortChange = { port = it.filter { c -> c in '0'..'9' }.take(5) },
                    timeoutSec = timeoutSec,
                    onTimeoutChange = { timeoutSec = it.coerceIn(1, 60) },
                    parallelJobs = parallelJobs,
                    onParallelJobsChange = { parallelJobs = it.coerceIn(1, 250) },
                )
            }

            item {
                val probed = if (scanning) probedCount else ((uiState as? ScanUiState.Done)?.probed ?: probedCount)
                StatsRow(tested = probed, working = working, countries = countries, bestPing = bestPing)
            }

            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Column {
                        if (uiState is ScanUiState.Failed) {
                            Text(
                                text = (uiState as ScanUiState.Failed).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF4D67),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        ScanActionButtons(
                            scanning = scanning,
                            percent = progressPercent,
                            onStart = { startScan() },
                            onStop = { stopScan() },
                        )
                    }
                }
            }

            items(currentEndpoints, key = { it.endpoint }) { endpoint ->
                EndpointCard(
                    endpoint = endpoint,
                    onUse = { useEndpoint(endpoint) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderWithRadar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF0F2C59), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.scanner_hero_desc),
                            color = Color(0xFF6B8AB8),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.scanner_hero_title),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                RadarGraphic(modifier = Modifier.size(130.dp))
            }
        }
    }
}

@Composable
private fun RadarGraphic(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.55f, size.height * 0.45f)
        val maxRadius = size.minDimension * 0.48f

        for (i in 1..4) {
            drawCircle(
                color = Color(0xFF13233F),
                radius = maxRadius * (i / 4f),
                center = center,
                style = Stroke(width = 1.2.dp.toPx()),
            )
        }

        drawArc(
            brush = Brush.sweepGradient(
                listOf(Color.Transparent, Color(0xFF3B82F6).copy(alpha = 0.55f)),
            ),
            startAngle = sweep,
            sweepAngle = 45f,
            useCenter = true,
            topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
            size = Size(maxRadius * 2, maxRadius * 2),
        )

        val dots = listOf(
            Offset(center.x + maxRadius * 0.55f, center.y - maxRadius * 0.65f),
            Offset(center.x - maxRadius * 0.70f, center.y - maxRadius * 0.20f),
            Offset(center.x - maxRadius * 0.35f, center.y + maxRadius * 0.30f),
            Offset(center.x + maxRadius * 0.15f, center.y + maxRadius * 0.70f),
            Offset(center.x + maxRadius * 0.75f, center.y - maxRadius * 0.10f),
        )
        dots.forEach { dot ->
            drawCircle(Color(0xFF22C55E), 3.5.dp.toPx(), dot)
        }

        drawLine(
            color = Color(0xFF60A5FA),
            start = center,
            end = Offset(
                center.x + kotlin.math.cos(Math.toRadians(sweep.toDouble())).toFloat() * maxRadius,
                center.y + kotlin.math.sin(Math.toRadians(sweep.toDouble())).toFloat() * maxRadius,
            ),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ProtocolSelectionCard(
    selected: ScannerProtocol,
    onSelect: (ScannerProtocol) -> Unit,
) {
    CardContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Security, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.scanner_section_protocol), style = MaterialTheme.typography.titleMedium, color = Color.White)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ScannerProtocol.entries.forEach { p ->
                val isSelected = p == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentBlue else UnselectedBg)
                        .border(1.dp, if (isSelected) AccentBlue else CardBorder, RoundedCornerShape(10.dp))
                        .clickable { onSelect(p) }
                        .padding(horizontal = 2.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = p.title,
                        color = if (isSelected) Color.White else SubtextColor,
                        fontSize = 11.sp,
                        letterSpacing = (-0.5).sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = SubtextColor, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.scanner_tip_masque_h2),
                color = SubtextColor,
                fontSize = 11.5.sp,
            )
        }
    }
}

@Composable
private fun ScanSettingsCard(
    scanMode: ScannerMode,
    onScanModeChange: (ScannerMode) -> Unit,
    ipChoice: ScoutIpChoice,
    onIpChoiceChange: (ScoutIpChoice) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    timeoutSec: Int,
    onTimeoutChange: (Int) -> Unit,
    parallelJobs: Int,
    onParallelJobsChange: (Int) -> Unit,
) {
    var modeDropdownExpanded by remember { mutableStateOf(false) }

    CardContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.scanner_section_settings), style = MaterialTheme.typography.titleMedium, color = Color.White)
        }

        Spacer(Modifier.height(14.dp))

        FieldLabel(stringResource(R.string.scanner_test_type))
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(UnselectedBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                    .clickable { modeDropdownExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, contentDescription = null, tint = SubtextColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(text = stringResource(scanMode.titleRes), color = Color.White, fontSize = 14.sp)
                }
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = SubtextColor)
            }

            DropdownMenu(
                expanded = modeDropdownExpanded,
                onDismissRequest = { modeDropdownExpanded = false },
                modifier = Modifier.background(CardBg),
            ) {
                ScannerMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(stringResource(mode.titleRes), color = Color.White) },
                        onClick = {
                            onScanModeChange(mode)
                            modeDropdownExpanded = false
                        },
                    )
                }
            }
        }
        Text(text = stringResource(scanMode.subtitleRes), color = SubtextColor, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(14.dp))

        FieldLabel(stringResource(R.string.scanner_ip_protocol))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(UnselectedBg)
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
        ) {
            ScoutIpChoice.entries.forEach { choice ->
                val isSelected = choice == ipChoice
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AccentBlue else Color.Transparent)
                        .clickable { onIpChoiceChange(choice) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(choice.titleRes),
                        color = if (isSelected) Color.White else SubtextColor,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        FieldLabel(stringResource(R.string.scanner_port_label))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(UnselectedBg)
                .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Memory, contentDescription = null, tint = SubtextColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (port.isEmpty()) {
                    Text(
                        text = stringResource(R.string.scanner_port_placeholder),
                        color = SubtextColor.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                }
                BasicTextField(
                    value = port,
                    onValueChange = onPortChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(AccentBlue),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(text = stringResource(R.string.scanner_port_helper), color = SubtextColor, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp))

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FieldLabel(stringResource(R.string.scanner_timeout_label))
                StepperBox(
                    icon = Icons.Rounded.Schedule,
                    value = timeoutSec,
                    onMinus = { onTimeoutChange(timeoutSec - 1) },
                    onPlus = { onTimeoutChange(timeoutSec + 1) },
                )
                Text(
                    text = stringResource(R.string.scanner_timeout_helper),
                    color = SubtextColor,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                FieldLabel(stringResource(R.string.scanner_parallel_label))
                StepperBox(
                    icon = Icons.AutoMirrored.Rounded.ShowChart,
                    value = parallelJobs,
                    onMinus = { onParallelJobsChange(parallelJobs - 5) },
                    onPlus = { onParallelJobsChange(parallelJobs + 5) },
                )
                Text(
                    text = stringResource(R.string.scanner_parallel_helper),
                    color = SubtextColor,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StepperBox(
    icon: ImageVector,
    value: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(UnselectedBg)
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SubtextColor, modifier = Modifier.size(16.dp))
        Text(
            text = value.toString(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinus, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Rounded.Remove, contentDescription = "Minus", tint = SubtextColor, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onPlus, modifier = Modifier.size(26.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "Plus", tint = SubtextColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun StatsRow(tested: Int, working: Int, countries: Int, bestPing: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatItem(
            icon = Icons.Rounded.Security,
            iconBg = Color(0xFF231F46),
            iconTint = Color(0xFFA855F7),
            value = tested.toString(),
            label = stringResource(R.string.scanner_stat_tested),
            modifier = Modifier.weight(1f),
        )
        StatItem(
            icon = Icons.Rounded.Favorite,
            iconBg = Color(0xFF0F352E),
            iconTint = Color(0xFF10B981),
            value = working.toString(),
            label = stringResource(R.string.scanner_stat_healthy),
            modifier = Modifier.weight(1f),
        )
        StatItem(
            icon = Icons.Rounded.Public,
            iconBg = Color(0xFF0F2E4A),
            iconTint = Color(0xFF0EA5E9),
            value = countries.toString(),
            label = stringResource(R.string.scanner_stat_countries),
            modifier = Modifier.weight(1f),
        )
        StatItem(
            icon = Icons.Rounded.Star,
            iconBg = Color(0xFF382914),
            iconTint = Color(0xFFF59E0B),
            value = bestPing,
            label = stringResource(R.string.scanner_stat_best),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(iconBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(text = label, color = SubtextColor, fontSize = 10.5.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ScanActionButtons(
    scanning: Boolean,
    percent: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val buttonBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF3B5BFA), Color(0xFF4C3AE8), Color(0xFF5A2CE2)),
    )

    if (!scanning) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(buttonBrush)
                .clickable(onClick = onStart),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Radar, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(text = stringResource(R.string.scanner_btn_start), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(26.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = AccentBlue,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.scanner_btn_searching, percent),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFF4D67), Color(0xFFE11D48)),
                        ),
                    )
                    .clickable(onClick = onStop)
                    .padding(horizontal = 24.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Stop,
                        contentDescription = "Stop",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.scanner_btn_stop),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EndpointCard(
    endpoint: ScoutEndpoint,
    onUse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .clickable(enabled = endpoint.healthy, onClick = onUse)
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(if (endpoint.healthy) Color(0xFF22C55E) else Color(0xFFFFB020), CircleShape),
                )
                Text(
                    text = endpoint.endpoint,
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                    fontFamily = FontFamily.Monospace,
                    color = Color.White,
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .weight(1f),
                )
                Icon(Icons.Rounded.Bolt, contentDescription = null, tint = AccentBlue)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                MetricItem(stringResource(R.string.scanner_metric_ep_ping), endpoint.endpointPing)
                MetricItem(stringResource(R.string.scanner_metric_tun_ping), endpoint.tunnelPing)
                MetricItem(stringResource(R.string.scanner_metric_loss), endpoint.loss)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${endpoint.seenAs}  ${endpoint.node}".trim(),
                    fontSize = 12.sp,
                    color = AccentBlue,
                )
                Text(
                    text = endpoint.nodeLocation,
                    fontSize = 12.sp,
                    color = SubtextColor,
                )
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column {
        Text(text = label, fontSize = 10.5.sp, color = SubtextColor)
        Text(text = value, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}
private fun formatScannerError(context: Context, rawError: String?): String {
    val err = rawError.orEmpty()
    return when {
        err.contains("ERR_UDP_BLOCKED") -> context.getString(R.string.scanner_err_udp_blocked)
        err.contains("ERR_REG_TIMEOUT") -> context.getString(R.string.scanner_err_reg_timeout)
        err.startsWith("ERR_REG_FAILED:") -> {
            val detail = err.removePrefix("ERR_REG_FAILED:")
            context.getString(R.string.scanner_err_reg_failed, detail)
        }
        err.contains("ERR_SCAN_TIMEOUT") -> context.getString(R.string.scanner_err_timeout)
        err.contains("ERR_BINARY_MISSING") -> context.getString(R.string.scanner_err_binary_missing)
        err.startsWith("ERR_SCAN_FAILED:") -> {
            val detail = err.removePrefix("ERR_SCAN_FAILED:")
            context.getString(R.string.scanner_err_failed, detail)
        }
        else -> context.getString(R.string.scanner_err_failed, err.ifBlank { "Unknown" })
    }
}
/**
 * مرتب‌سازی اندپوینت‌ها:
 * ۱. ابتدا سرورهای سالم (Healthy)
 * ۲. سپس بر اساس کمترین پینگ (در حالت Full/Durable پینگ تانل و در حالت Standard پینگ اندپوینت)
 */
private fun List<ScoutEndpoint>.sortedByPing(): List<ScoutEndpoint> =
    sortedWith(
        compareBy<ScoutEndpoint> { !it.healthy }
            .thenBy { ep ->
                val tPing = ep.tunnelPing.removeSuffix("ms").trim().toIntOrNull()
                if (tPing != null && tPing > 0) tPing
                else ep.endpointPing.removeSuffix("ms").trim().toIntOrNull() ?: Int.MAX_VALUE
            }
    )