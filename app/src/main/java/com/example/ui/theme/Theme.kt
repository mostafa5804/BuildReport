package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkAccentTeal,
    secondary = DarkAccentCyan,
    tertiary = DarkAccentAmber,
    background = DarkBgDeep,
    surface = DarkBgSurface,
    onPrimary = Color.White,
    onSecondary = Color(0xFF000000),
    onTertiary = Color(0xFF000000),
    onBackground = DarkTextMain,
    onSurface = DarkTextMain,
    surfaceVariant = DarkBgGlass,
    onSurfaceVariant = DarkTextMuted,
    primaryContainer = Color(0x333B82F6),
    onPrimaryContainer = DarkAccentTeal,
    outline = DarkBorderSubtle,
    outlineVariant = DarkBorderStrong
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LightAccentTeal,
    secondary = LightAccentCyan,
    tertiary = LightAccentAmber,
    background = LightBgDeep,
    surface = LightBgSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = LightTextMain,
    onSurface = LightTextMain,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = LightTextMuted,
    primaryContainer = LightAccentTealBg,
    onPrimaryContainer = LightAccentTeal,
    outline = LightBorderSubtle,
    outlineVariant = LightBorderStrong
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Turned off to enforce clean professional branding
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
