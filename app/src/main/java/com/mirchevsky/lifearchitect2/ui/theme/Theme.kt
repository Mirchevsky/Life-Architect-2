package com.mirchevsky.lifearchitect2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary            = BrandGreen,          // #10B981 - Add button, checked checkbox
    onPrimary          = Color.White,           // #FFFFFF - icon/text on primary button
    primaryContainer   = DarkSurface,          // #141A22
    secondary          = Purple,              // #7C3AED - Calendar button
    onSecondary        = Color.White,           // #FFFFFF
    background         = DarkBackground,       // #0B0F14
    onBackground       = DarkOnBackground,     // #F3F4F6
    surface            = DarkSurface,          // #141A22
    onSurface          = DarkOnSurface,        // #F3F4F6
    surfaceVariant     = DarkSurfaceVariant,   // #1F2933
    onSurfaceVariant   = DarkOnSurfaceVariant, // #9CA3AF
    surfaceTint        = Color.Transparent,    // disable tonal elevation tint globally
    error              = Color(0xFFF87171),    // #F87171 - dark mode urgent/error
    onError            = Color.White,           // #FFFFFF
    outline            = DarkSurfaceVariant    // #1F2933
)

private val LightColorScheme = lightColorScheme(
    primary            = BrandGreen,           // #10B981 - Add button, checked checkbox
    onPrimary          = Color.White,            // #FFFFFF
    primaryContainer   = BrandGreenLight,       // #D1FAE5
    secondary          = Purple,               // #7C3AED - Calendar button
    onSecondary        = Color.White,            // #FFFFFF
    background         = LightBackground,       // #F3F4F6
    onBackground       = LightOnBackground,     // #111827
    surface            = LightSurface,          // #FFFFFF
    onSurface          = LightOnSurface,        // #111827
    surfaceVariant     = LightSurfaceVariant,   // #E5E7EB
    onSurfaceVariant   = LightOnSurfaceVariant, // #6B7280
    surfaceTint        = Color.Transparent,     // disable tonal elevation tint globally
    error              = BrandError,            // #EF4444 - light mode urgent/error
    onError            = Color.White,            // #FFFFFF
    outline            = LightBorderLight       // #E5E7EB
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
