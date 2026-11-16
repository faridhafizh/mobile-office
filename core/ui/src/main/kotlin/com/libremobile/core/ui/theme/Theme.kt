package com.libremobile.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = LibrePrimary,
    onPrimary = LibreOnPrimary,
    primaryContainer = LibreOnPrimaryContainer,
    onPrimaryContainer = LibrePrimaryContainer,
    secondary = LibreSecondary,
    onSecondary = LibreOnSecondary,
    secondaryContainer = LibreOnSecondaryContainer,
    onSecondaryContainer = LibreSecondaryContainer,
    tertiary = LibreTertiary,
    onTertiary = LibreOnTertiary,
    background = LibreBackgroundDark,
    onBackground = LibreOnBackgroundDark,
    surface = LibreSurfaceDark,
    onSurface = LibreOnSurfaceDark
)

private val LightColorScheme = lightColorScheme(
    primary = LibrePrimary,
    onPrimary = LibreOnPrimary,
    primaryContainer = LibrePrimaryContainer,
    onPrimaryContainer = LibreOnPrimaryContainer,
    secondary = LibreSecondary,
    onSecondary = LibreOnSecondary,
    secondaryContainer = LibreSecondaryContainer,
    onSecondaryContainer = LibreOnSecondaryContainer,
    tertiary = LibreTertiary,
    onTertiary = LibreOnTertiary,
    background = LibreBackgroundLight,
    onBackground = LibreOnBackgroundLight,
    surface = LibreSurfaceLight,
    onSurface = LibreOnSurfaceLight
)

@Composable
fun LibreMobileTheme(
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
