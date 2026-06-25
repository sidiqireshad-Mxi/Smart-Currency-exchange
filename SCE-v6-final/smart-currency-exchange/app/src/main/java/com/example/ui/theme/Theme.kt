package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MintPrimary,
    secondary = MintSecondary,
    tertiary = MintTertiary,
    background = Slate900,
    surface = Slate800,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Slate700,
    onSurfaceVariant = Color(0xFFE2E8F0),
    error = LossRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = MintPrimary,
    secondary = Color(0xFF047857),
    tertiary = MintSecondary,
    background = Slate50,
    surface = SilverSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Slate900,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,
    error = LossRed,
    onError = Color.White
)

@Composable
fun SmartCurrencyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
