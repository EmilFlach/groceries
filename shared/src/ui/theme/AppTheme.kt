package com.emilflach.groceries.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warm, food-first palette — private tokens read via MaterialTheme.colorScheme, no dynamicColor.
private val Cream = Color(0xFFFBF6EC)
private val CreamSurface = Color(0xFFFFFFFF)
private val CreamVariant = Color(0xFFF0E7D6)
private val HerbGreen = Color(0xFF3E6B4F)
private val HerbGreenContainer = Color(0xFFD7E7DB)
private val Terracotta = Color(0xFFC4622D)
private val TerracottaContainer = Color(0xFFF6DCCB)
private val Amber = Color(0xFFE8A13A)
private val WarmCharcoal = Color(0xFF2B2A26)
private val WarmOutline = Color(0xFFD8CCB4)

private val NightBg = Color(0xFF1C1B18)
private val NightSurface = Color(0xFF26241F)
private val NightVariant = Color(0xFF332F27)
private val NightGreen = Color(0xFF7FB894)
private val NightGreenContainer = Color(0xFF2C4738)
private val NightTerracotta = Color(0xFFE08A5A)
private val NightTerracottaContainer = Color(0xFF4A2E1E)
private val NightAmber = Color(0xFFF0B860)
private val NightText = Color(0xFFF1EADD)
private val NightOutline = Color(0xFF4A4437)

private val LightColors = lightColorScheme(
    primary = HerbGreen,
    onPrimary = Color.White,
    primaryContainer = HerbGreenContainer,
    onPrimaryContainer = Color(0xFF17301F),
    secondary = Terracotta,
    onSecondary = Color.White,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = Color(0xFF3A1B0B),
    tertiary = Amber,
    onTertiary = Color(0xFF3A2907),
    background = Cream,
    onBackground = WarmCharcoal,
    surface = CreamSurface,
    onSurface = WarmCharcoal,
    surfaceVariant = CreamVariant,
    onSurfaceVariant = Color(0xFF5C554A),
    outline = WarmOutline,
    outlineVariant = Color(0xFFE7DCC8),
)

private val DarkColors = darkColorScheme(
    primary = NightGreen,
    onPrimary = Color(0xFF10241A),
    primaryContainer = NightGreenContainer,
    onPrimaryContainer = Color(0xFFD7E7DB),
    secondary = NightTerracotta,
    onSecondary = Color(0xFF3A1B0B),
    secondaryContainer = NightTerracottaContainer,
    onSecondaryContainer = Color(0xFFF6DCCB),
    tertiary = NightAmber,
    onTertiary = Color(0xFF3A2907),
    background = NightBg,
    onBackground = NightText,
    surface = NightSurface,
    onSurface = NightText,
    surfaceVariant = NightVariant,
    onSurfaceVariant = Color(0xFFCBC1B0),
    outline = NightOutline,
    outlineVariant = Color(0xFF3A362D),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(
            fontFamily = FontFamily.Default, fontWeight = FontWeight.Black,
            fontSize = 34.sp, letterSpacing = (-0.5).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp, letterSpacing = (-0.25).sp,
        ),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
