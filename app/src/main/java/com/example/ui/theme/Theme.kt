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
    primary = ElegantAccent,
    onPrimary = ElegantOnAccent,
    primaryContainer = ElegantSurfaceVariant,
    secondary = ElegantDarkSecondaryText,
    tertiary = Emerald500,
    background = ElegantDarkBg,
    surface = ElegantSurface,
    onBackground = ElegantDarkText,
    onSurface = ElegantDarkText,
    surfaceVariant = ElegantSurfaceVariant,
    onSurfaceVariant = ElegantDarkSecondaryText
  )

private val LightColorScheme = DarkColorScheme // Standardize on Elegant Dark as requested

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to Elegant Dark theme always as requested
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve branded aesthetics
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
