package com.najishab.aether.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.najishab.aether.ui.theme.AetherBlue
import com.najishab.aether.ui.theme.AetherDanger
import com.najishab.aether.ui.theme.AetherDangerGradientEnd
import com.najishab.aether.ui.theme.AetherSuccessGradientEnd
import com.najishab.aether.ui.theme.AetherSuccessGradientStart
import com.najishab.aether.ui.theme.Navy800

enum class ButtonMode { IDLE, BUSY, CONNECTED, ERROR }

/**
 * The centrepiece action: a large circular power button with a glowing halo, an
 * animated progress ring while busy, and colour that reflects the current mode.
 */
@Composable
fun ConnectButton(
    mode: ButtonMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Disconnected/error and connected are deliberately different colour
    // families (coral red vs neon green) so the button alone tells the user
    // whether the tunnel is up - it no longer sits on the same static blue
    // regardless of state. Busy stays brand blue: it is neither state yet.
    val accent = when (mode) {
        ButtonMode.IDLE -> AetherDanger
        ButtonMode.BUSY -> AetherBlue
        ButtonMode.CONNECTED -> AetherSuccessGradientStart
        ButtonMode.ERROR -> AetherDanger
    }
    val gradientEnd = when (mode) {
        ButtonMode.CONNECTED -> AetherSuccessGradientEnd
        ButtonMode.IDLE, ButtonMode.ERROR -> AetherDangerGradientEnd
        ButtonMode.BUSY -> AetherBlue
    }
    val animatedAccent by animateColorAsState(accent, tween(600), label = "accent")
    val animatedGradientEnd by animateColorAsState(gradientEnd, tween(600), label = "accentEnd")

    val transition = rememberInfiniteTransition(label = "connect")
    val sweepRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "sweep",
    )
    val haloPulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "halo",
    )
    val haloScale by animateFloatAsState(
        targetValue = if (mode == ButtonMode.CONNECTED) haloPulse else 1f,
        animationSpec = tween(400),
        label = "haloScale",
    )
    val iconSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "iconSpin",
    )

    val interaction = remember { MutableInteractionSource() }

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(220.dp)) {
        // Soft glowing halo behind the button - a gentle red bloom when idle,
        // a green one once connected (per the "Dark Tech" palette).
        Canvas(modifier = Modifier.size(220.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedAccent.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension / 2f * haloScale,
                ),
                radius = size.minDimension / 2f * haloScale,
            )
        }

        // Inner gradient disc.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            animatedAccent.copy(alpha = 0.30f),
                            Navy800,
                        ),
                    ),
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            // A thin neon ring traces the disc edge whenever the state is
            // settled (idle/connected/error), so the button reads at a glance
            // even before the eye reaches the status text below it.
            if (mode != ButtonMode.BUSY) {
                Canvas(modifier = Modifier.size(146.dp)) {
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(animatedAccent, animatedGradientEnd, animatedAccent)),
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }

            // Progress ring while busy.
            if (mode == ButtonMode.BUSY) {
                Canvas(modifier = Modifier.size(132.dp).rotate(sweepRotation)) {
                    drawArc(
                        color = animatedAccent,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }

            val icon = when (mode) {
                ButtonMode.CONNECTED -> Icons.Rounded.Bolt
                ButtonMode.BUSY -> Icons.Rounded.Autorenew
                else -> Icons.Rounded.PowerSettingsNew
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = animatedAccent,
                modifier = Modifier
                    .size(58.dp)
                    .then(if (mode == ButtonMode.BUSY) Modifier.rotate(iconSpin) else Modifier),
            )
        }
    }
}
