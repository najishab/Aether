package com.najishab.aether.ui.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import com.najishab.aether.R
import com.najishab.aether.core.EngineMeta
import com.najishab.aether.core.Formatters
import com.najishab.aether.core.HevTunnel
import com.najishab.aether.core.IpEndpoint
import com.najishab.aether.core.NetProbe
import com.najishab.aether.core.PingMonitor
import com.najishab.aether.core.ShareBridge
import com.najishab.aether.ui.theme.AetherBlue
import com.najishab.aether.ui.theme.AetherDanger
import com.najishab.aether.ui.theme.AetherSuccess
import com.najishab.aether.ui.theme.AetherSuccessGradientEnd
import com.najishab.aether.ui.theme.CardSubSurface
import com.najishab.aether.ui.theme.CardSurfaceBottom
import com.najishab.aether.ui.theme.CardSurfaceTop
import com.najishab.aether.ui.theme.CardTextDim
import com.najishab.aether.ui.theme.CardTextMuted
import com.najishab.aether.ui.theme.CardTextPrimary

/**
 * THE bottom block of the home screen (new in 1.2.6).
 *
 * Until 1.2.5 the area under the power button was four separate floating
 * surfaces - status text, traffic meter, IP badge and the protocol/endpoint/
 * latency row - each with its own color, radius and padding. They read as
 * clutter on a phone screen and nothing tied them together.
 *
 * This is one cohesive glass morphia card instead, with a single surface color
 * system and a fixed vertical hierarchy:
 *
 *   1. connection status  (large, red/green by state, with a quiet "tap to
 *      disconnect" line)
 *   2. session timer      ("Connected for" + HH:MM:SS in a mono/digital face)
 *   3. server IP pill     (label + country flag + address)
 *   4. speed strip        (live down/up rate and session totals)
 *   5. protocol strip     (Protocol | Endpoint | Latency, three equal columns)
 *
 * Nothing floats outside the block: every subsection is a child container of
 * the same card, drawn from the same palette.
 *
 * COLOURS ARE DELIBERATELY NOT FROM MaterialTheme. The app runs Material You
 * (`dynamicDarkColorScheme`) on Android 12+, which repaints every themed
 * surface from the user's wallpaper - a purple or brown wallpaper turned this
 * card into something that no longer looked like Aether. The card is pinned to
 * the brand "Dark Tech" palette instead: deep navy background (#070B17), a
 * neon green accent (#22C55E) once connected, coral red (#FF4D67) otherwise,
 * one slate glass surface for everything inside it.
 *
 * CONNECTED-STATE ANIMATION. While the tunnel is up, the card edge carries a
 * light show: segments of green travel around the border and breathe in
 * length, width and intensity like an audio equalizer, so the card feels alive
 * rather than blinking. Implementation notes, because this app has a history of
 * animations eating the frame budget (see AmbientBackground):
 *  - it is ONE closed path, measured once per size change and cached;
 *  - the animation state is read inside the draw lambda, so a frame costs a
 *    redraw of the border only - never a recomposition of the card;
 *  - the infinite transition is composed ONLY while connected, so a disconnected
 *    app subscribes to no frame callbacks at all;
 *  - each band is three strokes (halo, mid, core) in additive blend, which fakes
 *    a soft bloom without a blur pass or a second layer.
 */
@Composable
fun ConnectionCard(
    connected: Boolean,
    statusTitle: String,
    statusCaption: String,
    connectedSince: Long?,
    ipInfo: IpEndpoint?,
    ipLoading: Boolean,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = when {
        error -> ERROR_ACCENT
        connected -> AetherSuccess
        else -> IDLE_ACCENT
    }

    // Only alive while connected: no frame subscription when there is nothing
    // to show off.
    val pulse = if (connected) rememberGlowPulse() else null

    Box(
        modifier = modifier
            .fillMaxWidth()
            // clip = false so the glow may bloom past the card edge.
            .shadow(
                elevation = 26.dp,
                shape = CARD_SHAPE,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .background(
                brush = Brush.verticalGradient(listOf(CardSurfaceTop, CardSurfaceBottom)),
                shape = CARD_SHAPE,
            )
            .glassEdge(accent = accent, pulse = pulse)
            .padding(horizontal = 18.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusBlock(title = statusTitle, caption = statusCaption, accent = accent)
            TimerBlock(connectedSince = connectedSince, connected = connected)
            ServerIpPill(connected = connected, ipInfo = ipInfo, ipLoading = ipLoading)
            SpeedStrip(connectedSince = connectedSince, connected = connected)
            ProtocolStrip(connected = connected)
        }
    }
}

// --------------------------------------------------------------- 1. status

@Composable
private fun StatusBlock(title: String, caption: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedContent(
            targetState = title,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "cardStatus",
        ) { value ->
            Text(
                text = value,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(4.dp))
        AnimatedContent(
            targetState = caption,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "cardCaption",
        ) { value ->
            Text(
                text = value,
                fontSize = 13.sp,
                color = CardTextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- 2. timer

@Composable
private fun TimerBlock(connectedSince: Long?, connected: Boolean) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedSince) {
        if (connectedSince == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1.seconds)
        }
    }

    val elapsed = if (connectedSince == null) 0L else (now - connectedSince).coerceAtLeast(0L) / 1000L
    val text = String.format(
        Locale.US,
        "%02d:%02d:%02d",
        elapsed / 3600,
        (elapsed % 3600) / 60,
        elapsed % 60,
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.connected_for),
            fontSize = 11.sp,
            letterSpacing = 1.2.sp,
            color = CardTextMuted,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 38.sp,
            letterSpacing = 1.5.sp,
            color = if (connected) CardTextPrimary else CardTextDim,
        )
    }
}

// ------------------------------------------------------------ 3. server IP

@Composable
private fun ServerIpPill(connected: Boolean, ipInfo: IpEndpoint?, ipLoading: Boolean) {
    val label = stringResource(
        if (connected) R.string.ip_server_label else R.string.ip_your_label,
    )
    val flag = NetProbe.flagEmoji(ipInfo?.countryCode)
    val value = when {
        ipLoading && ipInfo == null -> stringResource(R.string.ip_checking)
        ipInfo != null -> ipInfo.ip
        else -> stringResource(R.string.ip_unavailable)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(0.76f)
            .background(brush = PILL_SURFACE, shape = CircleShape)
            .subEdge(CircleShape, PILL_BORDER)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text = label, fontSize = 14.sp, color = CardTextMuted, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        if (ipInfo != null) {
            Text(text = flag, fontSize = 17.sp)
            Spacer(Modifier.width(10.dp))
        }
        AnimatedContent(
            targetState = value,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "cardIp",
        ) { shown ->
            Text(
                text = shown,
                // BiDi: an address is LTR technical text even in the Persian UI.
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CardTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// --------------------------------------------------------------- 4. speeds

@Composable
private fun SpeedStrip(connectedSince: Long?, connected: Boolean) {
    val stats = rememberTrafficStats(connectedSince = connectedSince, connected = connected)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = PANEL_SURFACE, shape = SUB_SHAPE)
            .subEdge(SUB_SHAPE, PANEL_BORDER)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpeedCell(
            icon = Icons.Rounded.ArrowDownward,
            tint = DOWNLOAD_ACCENT,
            label = stringResource(R.string.traffic_download),
            rate = stats.downRate,
            total = stats.downTotal,
            modifier = Modifier.weight(1f),
        )
        CellDivider()
        SpeedCell(
            icon = Icons.Rounded.ArrowUpward,
            tint = AetherSuccess,
            label = stringResource(R.string.traffic_upload),
            rate = stats.upRate,
            total = stats.upTotal,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SpeedCell(
    icon: ImageVector,
    tint: Color,
    label: String,
    rate: Long,
    total: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.background(color = tint.copy(alpha = 0.14f), shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier
                    .padding(7.dp)
                    .size(18.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 9.dp)) {
            Text(
                text = Formatters.formatRate(rate),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = CardTextPrimary,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.traffic_total, Formatters.formatBytes(total)),
                fontSize = 12.sp,
                color = CardTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------- 5. protocol

@Composable
private fun ProtocolStrip(connected: Boolean) {
    val meta by EngineMeta.state.collectAsState()
    val ping by PingMonitor.state.collectAsState()

    // Live latency, exactly like the desktop edition: one cheap TCP handshake
    // through the tunnel every few seconds, serialized by PingMonitor.
    LaunchedEffect(connected) {
        while (connected) {
            PingMonitor.pingOnce(viaTunnel = true)
            delay(LATENCY_REFRESH)
        }
    }

    val dash = "\u2014"
    val protocol = if (connected) meta.protocol ?: dash else dash
    val endpoint = if (connected) meta.endpoint ?: "\u2026" else dash
    val latency = when {
        !connected -> dash
        ping.ms >= 0 -> "${ping.ms} ms"
        ping.running -> "\u2026"
        else -> dash
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(brush = PANEL_SURFACE, shape = SUB_SHAPE)
            .subEdge(SUB_SHAPE, PANEL_BORDER)
            .padding(horizontal = 8.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetaCell(Icons.Rounded.Security, stringResource(R.string.meta_protocol), protocol, Modifier.weight(1f))
        CellDivider()
        MetaCell(Icons.Rounded.Hub, stringResource(R.string.meta_endpoint), endpoint, Modifier.weight(1f))
        CellDivider()
        MetaCell(Icons.Rounded.Speed, stringResource(R.string.meta_latency), latency, Modifier.weight(1f))
    }
}

@Composable
private fun MetaCell(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = META_ICON,
            modifier = Modifier.size(27.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            letterSpacing = 0.6.sp,
            color = CardTextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = CardTextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
        )
    }
}

@Composable
private fun CellDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(36.dp)
            .background(DIVIDER),
    )
}

// ------------------------------------------------------------ traffic feed

/** Instantaneous rates + session totals, polled once per second. */
private data class TrafficStats(
    val downRate: Long = 0L,
    val upRate: Long = 0L,
    val downTotal: Long = 0L,
    val upTotal: Long = 0L,
)

/**
 * Sums BOTH possible traffic paths so the meter works in every mode:
 *  - hev-socks5-tunnel's direction-corrected counters (system-VPN mode; null in
 *    proxy mode, where there is no TUN),
 *  - ShareBridge: bytes relayed through the local SOCKS5/HTTP listeners (the
 *    only source in proxy mode, plus LAN clients in system-VPN mode).
 *
 * Rates come from deltas against a monotonic clock, so a wall-clock jump cannot
 * invent a spike. A negative delta (core restart during auto-reconnect, or a
 * fresh sharing session resetting the bridge counters) is clamped to zero and
 * the baseline rebases itself.
 */
@Composable
private fun rememberTrafficStats(connectedSince: Long?, connected: Boolean): TrafficStats {
    var stats by remember(connectedSince) { mutableStateOf(TrafficStats()) }

    LaunchedEffect(connectedSince, connected) {
        if (!connected) return@LaunchedEffect
        var lastDown = -1L
        var lastUp = -1L
        var lastAt = 0L
        while (true) {
            val hev = HevTunnel.traffic()
            val share = ShareBridge.traffic()
            if (hev != null || ShareBridge.active.value) {
                val down = (hev?.downloadBytes ?: 0L) + share.downloadBytes
                val up = (hev?.uploadBytes ?: 0L) + share.uploadBytes
                val at = SystemClock.elapsedRealtime()
                var downRate = stats.downRate
                var upRate = stats.upRate
                if (lastAt > 0L && at > lastAt) {
                    val dt = at - lastAt
                    downRate = ((down - lastDown).coerceAtLeast(0L) * 1000L) / dt
                    upRate = ((up - lastUp).coerceAtLeast(0L) * 1000L) / dt
                }
                stats = TrafficStats(downRate, upRate, down, up)
                lastDown = down
                lastUp = up
                lastAt = at
            }
            delay(1.seconds)
        }
    }

    return stats
}

// ------------------------------------------------------- the animated edge

/** The two animated states of the border light show. */
private class GlowPulse(val phase: State<Float>, val breath: State<Float>)

@Composable
private fun rememberGlowPulse(): GlowPulse {
    val transition = rememberInfiniteTransition(label = "cardGlow")
    // Travels the perimeter linearly, so the loop is seamless.
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(GLOW_TRAVEL_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    // Overall intensity, so the whole edge breathes instead of only flickering.
    val breath = transition.animateFloat(
        initialValue = 0.68f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    return remember(phase, breath) { GlowPulse(phase, breath) }
}

/**
 * One equalizer band: where it sits on the perimeter, how long it is, and which
 * harmonic of the travel phase drives its intensity. The harmonics are WHOLE
 * numbers on purpose - a fractional one would jump when the phase wraps from 1
 * back to 0 and the whole edge would visibly stutter once per cycle.
 */
private class GlowBand(
    val offset: Float,
    val span: Float,
    val harmonic: Int,
    val skew: Float,
    val tint: Float,
)

private val GLOW_BANDS = listOf(
    GlowBand(offset = 0.00f, span = 0.15f, harmonic = 2, skew = 0.00f, tint = 0.00f),
    GlowBand(offset = 0.13f, span = 0.08f, harmonic = 3, skew = 0.34f, tint = 0.45f),
    GlowBand(offset = 0.28f, span = 0.13f, harmonic = 5, skew = 0.11f, tint = 0.20f),
    GlowBand(offset = 0.43f, span = 0.06f, harmonic = 7, skew = 0.61f, tint = 0.85f),
    GlowBand(offset = 0.56f, span = 0.14f, harmonic = 3, skew = 0.79f, tint = 0.35f),
    GlowBand(offset = 0.70f, span = 0.09f, harmonic = 5, skew = 0.24f, tint = 0.65f),
    GlowBand(offset = 0.85f, span = 0.12f, harmonic = 2, skew = 0.50f, tint = 1.00f),
)

/**
 * The card edge: a soft inner glow, a hairline teal border, and - while
 * connected - the traveling equalizer light.
 */
private fun Modifier.glassEdge(accent: Color, pulse: GlowPulse?): Modifier = drawWithCache {
    val hairline = 1.dp.toPx()
    val inset = hairline / 2f
    val radius = CARD_RADIUS.toPx()
    val outline = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(inset, inset, size.width - inset, size.height - inset),
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
    val measure = PathMeasure().apply { setPath(outline, true) }
    val perimeter = measure.length
    val band = Path()
    val innerGlow = Brush.radialGradient(
        colors = listOf(accent.copy(alpha = 0.10f), Color.Transparent),
        center = Offset(size.width / 2f, 0f),
        radius = size.width * 0.95f,
    )

    onDrawBehind {
        drawPath(outline, brush = innerGlow)
        drawPath(
            outline,
            color = accent.copy(alpha = if (pulse == null) 0.10f else 0.16f),
            style = Stroke(hairline),
        )
        if (pulse == null) return@onDrawBehind

        val phase = pulse.phase.value
        val breath = pulse.breath.value
        for (spec in GLOW_BANDS) {
            val amp = 0.5f + 0.5f * sin(TWO_PI * (spec.harmonic * phase + spec.skew))
            val length = perimeter * spec.span * (0.30f + 0.95f * amp)
            val start = ((phase + spec.offset) % 1f) * perimeter
            val colour = lerp(AetherSuccess, AetherSuccessGradientEnd, (spec.tint * 0.6f + amp * 0.4f))
            val width = hairline * (1.1f + 2.3f * amp)
            val alpha = (0.20f + 0.80f * amp) * breath

            band.reset()
            measure.appendSegment(band, start, length, perimeter)

            // halo -> mid -> core, additively blended: a soft bloom without a
            // blur pass or an extra layer.
            drawGlowStroke(band, colour, alpha * 0.09f, width * 4.4f)
            drawGlowStroke(band, colour, alpha * 0.26f, width * 2.1f)
            drawGlowStroke(band, colour, alpha, width)
        }
    }
}

private fun DrawScope.drawGlowStroke(path: Path, colour: Color, alpha: Float, width: Float) {
    drawPath(
        path = path,
        color = colour.copy(alpha = alpha.coerceIn(0f, 1f)),
        style = Stroke(width = width, cap = StrokeCap.Round),
        blendMode = BlendMode.Plus,
    )
}

/** Copies a piece of the perimeter, wrapping around the corner if it overruns. */
private fun PathMeasure.appendSegment(dst: Path, start: Float, length: Float, perimeter: Float) {
    val end = start + length
    if (end <= perimeter) {
        getSegment(start, end, dst, true)
    } else {
        getSegment(start, perimeter, dst, true)
        getSegment(0f, end - perimeter, dst, true)
    }
}

/** The 1px low-opacity teal rim shared by every sub-container in the card. */
private fun Modifier.subEdge(shape: CornerBasedShape, color: Color = SUB_BORDER): Modifier = drawWithCache {
    val hairline = 1.dp.toPx()
    val inset = hairline / 2f
    val radius = shape.topStart.toPx(size, this)
    val outline = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(inset, inset, size.width - inset, size.height - inset),
                cornerRadius = CornerRadius(radius),
            ),
        )
    }
    onDrawBehind { drawPath(outline, color = color, style = Stroke(hairline)) }
}

// ---------------------------------------------------------------- helpers

private val CARD_RADIUS = 26.dp
private val CARD_SHAPE = RoundedCornerShape(CARD_RADIUS)
private val SUB_SHAPE = RoundedCornerShape(18.dp)
private val PILL_SURFACE = Brush.horizontalGradient(
    listOf(Color(0xD51B3D7A), Color(0xE8223E78), Color(0xD51A3569)),
)
private val PANEL_SURFACE = Brush.verticalGradient(
    listOf(Color(0xEC203D78), Color(0xF0173264)),
)
// Sub-container hairline stays brand blue regardless of state - only the
// status text, the power button and the animated card edge switch color.
private val SUB_BORDER = AetherBlue.copy(alpha = 0.12f)
private val PILL_BORDER = AetherBlue.copy(alpha = 0.18f)
private val PANEL_BORDER = AetherBlue.copy(alpha = 0.20f)
private val DIVIDER = Color(0x1FFFFFFF)
private val DOWNLOAD_ACCENT = Color(0xFFFF5D84)
private val META_ICON = Color(0xCCD7DEEA)
private val IDLE_ACCENT = AetherDanger
private val ERROR_ACCENT = AetherDanger
private const val GLOW_TRAVEL_MS = 5_200
private val LATENCY_REFRESH = 4.seconds
private const val TWO_PI = 6.2831855f
