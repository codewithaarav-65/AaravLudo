package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RoyalDarkColorScheme = darkColorScheme(
    primary = ImperialGold,
    onPrimary = Color(0xFF1A1200),
    primaryContainer = RoyalCardElevated,
    onPrimaryContainer = ImperialGoldLight,
    secondary = LudoBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1B345B),
    onSecondaryContainer = LudoBlueSoft,
    tertiary = LudoGreen,
    onTertiary = Color.White,
    background = RoyalObsidian,
    onBackground = TextIvory,
    surface = RoyalSurface,
    onSurface = TextIvory,
    surfaceVariant = RoyalCard,
    onSurfaceVariant = TextMuted,
    error = RubyAlert,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RoyalDarkColorScheme,
        typography = Typography,
        content = content
    )
}
