package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SaaSDarkColorScheme = darkColorScheme(
    primary = ElectricPurple,
    onPrimary = TextWhite,
    primaryContainer = NavySurfaceVariant,
    onPrimaryContainer = ElectricPurpleLight,
    secondary = CyberBlue,
    onSecondary = NavyBackground,
    secondaryContainer = NavySurfaceVariant,
    onSecondaryContainer = CyberBlue,
    tertiary = EmeraldSuccess,
    onTertiary = NavyBackground,
    background = NavyBackground,
    onBackground = TextWhite,
    surface = NavySurface,
    onSurface = TextWhite,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = NavyCardBorder,
    error = RoseError,
    onError = TextWhite
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SaaSDarkColorScheme,
        typography = Typography,
        content = content
    )
}
