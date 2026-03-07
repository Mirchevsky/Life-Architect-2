package com.mirchevsky.lifearchitect2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary            = BrandGreen,
    onPrimary          = DarkOnBackground,
    primaryContainer   = DarkSurface,
    secondary          = BrandCyan,
    onSecondary        = DarkOnBackground,
    background         = DarkBackground,
    onBackground       = DarkOnBackground,
    surface            = DarkSurface,
    onSurface          = DarkOnSurface,
    surfaceVariant     = DarkSurfaceVariant,
    onSurfaceVariant   = DarkOnSurfaceVariant,
    error              = BrandError,
    onError            = DarkOnBackground,
    outline            = DarkSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary            = BrandGreen,
    onPrimary          = LightBackground,
    primaryContainer   = BrandGreenLight,
    secondary          = BrandGreenDark,
    onSecondary        = LightBackground,
    background         = LightBackground,
    onBackground       = LightOnBackground,
    surface            = LightSurface,
    onSurface          = LightOnSurface,
    surfaceVariant     = LightSurfaceVariant,
    onSurfaceVariant   = LightOnSurfaceVariant,
    error              = BrandError,
    onError            = LightBackground,
    outline            = LightBorderLight
)

/**
 * The root composable theme wrapper for the entire application.
 *
 * @param darkTheme When true, applies the dark color scheme. Defaults to the system setting.
 * @param content The composable content to be themed.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
