package com.najishab.aether.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * The app backdrop: a single, flat, static colour.
 *
 * 1.2.2 UI-SPEED FIX (final): this used to be an animated "aurora" — three
 * large radial-gradient blobs drifting across a full-screen Canvas. Even after
 * the redraw rate was capped it still rebuilt and repainted full-screen
 * gradients continuously behind every screen, for as long as the app was open,
 * which is what made the whole UI feel heavy and laggy on real devices. The
 * animation and the gradients are gone: the background is now a plain colour
 * that costs exactly one fill and never invalidates. Nothing animates behind
 * the UI any more, so every frame budget belongs to the UI itself.
 *
 * Follows MaterialTheme.colorScheme.background (dark navy / light / dynamic)
 * so the light theme setting actually repaints the home screen, not just the
 * drawer and sheets.
 *
 * The parameters are kept so callers stay unchanged; they no longer affect the
 * backdrop.
 */
@Composable
fun AmbientBackground(
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}
