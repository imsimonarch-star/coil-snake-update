package com.coilsnake.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.coilsnake.game.R

object CoilColors {
    val Ink = Color(0xFF16122E)
    val InkOverlay = Color(0xE60E0B22)
    val Cream = Color(0xFFFFF5E3)
    val CreamMuted = Color(0xFFD6CBB5)
    val Teal = Color(0xFF22C7B2)
    val TealDark = Color(0xFF138A7B)
    val Gold = Color(0xFFFFC23A)
    val GoldDark = Color(0xFFC4850F)
    val Coral = Color(0xFFFF5A6A)
    val CoralDark = Color(0xFFA32937)
    val Pink = Color(0xFFFF5FA8)
    val GreenReady = Color(0xFF5DFF9A)
    val HeartRed = Color(0xFFFF4D6D)
    val Blaze = Color(0xFFFF7A2F)
    val Frost = Color(0xFF58D2FF)
    val Ghost = Color(0xFFB08CFF)
}

val BungeeFontFamily = FontFamily(
    Font(R.font.bungee, FontWeight.Normal)
)

val RubikFontFamily = FontFamily(
    Font(R.font.rubik, FontWeight.Normal)
)

private val CoilDarkColorScheme = darkColorScheme(
    primary = CoilColors.Gold,
    onPrimary = CoilColors.Ink,
    secondary = CoilColors.Teal,
    onSecondary = CoilColors.Ink,
    tertiary = CoilColors.Coral,
    onTertiary = CoilColors.Cream,
    background = CoilColors.Ink,
    onBackground = CoilColors.Cream,
    surface = Color(0xFF1F1A3D),
    onSurface = CoilColors.Cream,
    surfaceVariant = Color(0xFF2B2452),
    onSurfaceVariant = CoilColors.CreamMuted,
    error = CoilColors.Coral,
    onError = CoilColors.Cream
)

val CoilTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BungeeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 48.sp,
        lineHeight = 50.sp
    ),
    displayMedium = TextStyle(
        fontFamily = BungeeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = BungeeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = BungeeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BungeeFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = RubikFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = RubikFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = RubikFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp
    )
)

@Composable
fun CoilSnakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CoilDarkColorScheme,
        typography = CoilTypography,
        content = content
    )
}
