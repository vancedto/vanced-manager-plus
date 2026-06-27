package com.revanced.net.revancedmanager.presentation.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.revanced.net.revancedmanager.domain.model.ThemeMode
import com.revanced.net.revancedmanager.ui.theme.Typography

// ── Dark theme (bento-inspired) ──────────────────────────────────────────────
private val DarkBackground    = Color(0xFF0C0E15)
private val DarkSurface       = Color(0xFF13151F)
private val DarkSurfaceVar    = Color(0xFF1B1E2C)
private val DarkPrimary       = Color(0xFF7857FF)
private val DarkSecondary     = Color(0xFFA3A8C0)
private val DarkOnBackground  = Color(0xFFF0F2FF)
private val DarkOnSurface     = Color(0xFFE2E6F8)
private val DarkOnSurfaceVar  = Color(0xFF8890A8)
private val DarkError         = Color(0xFFFF6B6B)
private val DarkOnError       = Color(0xFF1A0A0A)

// ── Light theme ───────────────────────────────────────────────────────────────
private val LightBackground   = Color(0xFFF0F2F8)
private val LightSurface      = Color(0xFFFFFFFF)
private val LightSurfaceVar   = Color(0xFFE8ECF8)
private val LightPrimary      = Color(0xFF5B3BF0)
private val LightSecondary    = Color(0xFF64748B)
private val LightOnBackground = Color(0xFF0F1226)
private val LightOnSurface    = Color(0xFF1A1F3C)
private val LightOnSurfaceVar = Color(0xFF475569)
private val LightError        = Color(0xFFD32F2F)
private val LightOnError      = Color(0xFFFFFFFF)

// ── Action button colors ──────────────────────────────────────────────────────
private val DownloadDark   = Color(0xFF3B82F6)
private val DownloadLight  = Color(0xFF2563EB)
private val UpdateDark     = Color(0xFFF59E0B)
private val UpdateLight    = Color(0xFFD97706)
private val OpenDark       = Color(0xFF10B981)
private val OpenLight      = Color(0xFF059669)
private val UninstallDark  = Color(0xFFEF4444)
private val UninstallLight = Color(0xFFDC2626)

private val DarkColorScheme = darkColorScheme(
    primary            = DarkPrimary,
    onPrimary          = Color.White,
    primaryContainer   = DarkPrimary.copy(alpha = 0.18f),
    onPrimaryContainer = DarkOnBackground,
    secondary          = DarkSecondary,
    onSecondary        = Color.White,
    tertiary           = DownloadDark,
    onTertiary         = Color.White,
    background         = DarkBackground,
    onBackground       = DarkOnBackground,
    surface            = DarkSurface,
    onSurface          = DarkOnSurface,
    surfaceVariant     = DarkSurfaceVar,
    onSurfaceVariant   = DarkOnSurfaceVar,
    error              = DarkError,
    onError            = DarkOnError,
    outline            = DarkOnSurface.copy(alpha = 0.12f),
)

private val LightColorScheme = lightColorScheme(
    primary            = LightPrimary,
    onPrimary          = Color.White,
    primaryContainer   = LightPrimary.copy(alpha = 0.12f),
    onPrimaryContainer = LightOnBackground,
    secondary          = LightSecondary,
    onSecondary        = Color.White,
    tertiary           = DownloadLight,
    onTertiary         = Color.White,
    background         = LightBackground,
    onBackground       = LightOnBackground,
    surface            = LightSurface,
    onSurface          = LightOnSurface,
    surfaceVariant     = LightSurfaceVar,
    onSurfaceVariant   = LightOnSurfaceVar,
    error              = LightError,
    onError            = LightOnError,
    outline            = LightOnSurface.copy(alpha = 0.12f),
)

@Composable
fun RevancedManagerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}

// ── Extension properties for action button colors ─────────────────────────────
val ColorScheme.downloadColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) DownloadDark else DownloadLight

val ColorScheme.updateColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) UpdateDark else UpdateLight

val ColorScheme.openColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) OpenDark else OpenLight

val ColorScheme.uninstallColor: Color
    @Composable @ReadOnlyComposable
    get() = if (isSystemInDarkTheme()) UninstallDark else UninstallLight
