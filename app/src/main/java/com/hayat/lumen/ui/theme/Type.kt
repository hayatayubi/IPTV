package com.hayat.lumen.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography
import com.hayat.lumen.R

// Inter, bundled in res/font (stand-in for SF). Three weights only.
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, weight: FontWeight, lineHeight: Int, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

// Sizes are for a 10-foot UI: what reads well from the sofa, not from a phone.
val LumenTypography = Typography(
    displayLarge = style(57, FontWeight.SemiBold, 64, -0.5f),
    displayMedium = style(45, FontWeight.SemiBold, 52, -0.25f),
    displaySmall = style(36, FontWeight.SemiBold, 44),
    headlineLarge = style(32, FontWeight.SemiBold, 40),
    headlineMedium = style(28, FontWeight.Medium, 36),
    headlineSmall = style(24, FontWeight.Medium, 32),
    titleLarge = style(22, FontWeight.Medium, 28),
    titleMedium = style(18, FontWeight.Medium, 24),
    titleSmall = style(16, FontWeight.Medium, 22),
    bodyLarge = style(18, FontWeight.Normal, 26),
    bodyMedium = style(16, FontWeight.Normal, 22),
    bodySmall = style(14, FontWeight.Normal, 20),
    labelLarge = style(16, FontWeight.Medium, 20),
    labelMedium = style(14, FontWeight.Medium, 18),
    labelSmall = style(12, FontWeight.Medium, 16),
)
