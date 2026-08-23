package com.najishab.aether.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.najishab.aether.R

// Custom brand fonts: a Latin face for English (and any other locale) and a
// dedicated Persian face swapped in for the fa locale so Persian text never
// falls back to the system font.
val NajiFontFamily = FontFamily(Font(R.font.najifont))
val NajiFontFamilyFa = FontFamily(Font(R.font.najifont_fa))

// Material 3 default type scale, with the brand font applied to every style.
private fun typographyWith(fontFamily: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}

val AetherTypography = typographyWith(NajiFontFamily)
val AetherTypographyFa = typographyWith(NajiFontFamilyFa)
