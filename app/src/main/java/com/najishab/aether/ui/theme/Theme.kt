package com.najishab.aether.ui.theme

import android.app.Activity
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.najishab.aether.data.ThemeMode

// Fallback scheme: a stunning navy dark theme for devices below Android 12.
private val AetherDarkColorScheme = darkColorScheme(
    primary = AetherBlue,
    onPrimary = Color.White,
    secondary = AetherCyan,
    onSecondary = Color(0xFF04211C),
    tertiary = AetherCyan,
    background = Navy900,
    onBackground = OnDark,
    surface = Navy800,
    onSurface = OnDark,
    surfaceVariant = Navy700,
    onSurfaceVariant = OnDarkMuted,
    error = AetherError,
    onError = Color.White,
    outline = Navy600,
)

/**
 * Light theme scheme: the same brand blue/shapes, but a light backdrop and
 * dark text - a fixed palette, not the wallpaper-driven Material You dynamic
 * scheme (which is reserved for dark mode, see [AetherTheme]).
 */
private val AetherLightColorScheme = lightColorScheme(
    primary = AetherBlue,
    onPrimary = Color.White,
    secondary = AetherCyan,
    onSecondary = Color.White,
    tertiary = AetherCyan,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = AetherError,
    onError = Color.White,
    outline = LightOutline,
)

/**
 * Material You: uses the wallpaper-derived dynamic dark palette on Android 12+
 * when in dark mode, and a fixed navy/light scheme otherwise.
 *
 * [themeMode] resolves to a concrete dark/light choice: SYSTEM follows the
 * device's own day/night setting.
 */
@Composable
fun AetherTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = when {
        useDark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicDarkColorScheme(context)
        useDark -> AetherDarkColorScheme
        else -> AetherLightColorScheme
    }

    // Locale-aware typography: the Persian brand font for the fa locale,
    // the Latin brand font otherwise.
    val locale = AppCompatDelegate.getApplicationLocales()[0]?.language
        ?: java.util.Locale.getDefault().language
    val typography = if (locale == "fa") AetherTypographyFa else AetherTypography

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content,
    )
}
