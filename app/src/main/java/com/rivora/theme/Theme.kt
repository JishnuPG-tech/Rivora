package com.rivora.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val DarkColorScheme = darkColorScheme(
  primary = RivoraColor.Amber,
  secondary = RivoraColor.Teal,
  tertiary = RivoraColor.Slate,
  background = RivoraColor.App,
  surface = RivoraColor.Surface,
  onPrimary = RivoraColor.AmberText,
  onSecondary = RivoraColor.TextPrimary,
  onBackground = RivoraColor.TextPrimary,
  onSurface = RivoraColor.TextPrimary
)

@Composable
fun RivoraTheme(content: @Composable () -> Unit) {
  val d = LocalDensity.current
  CompositionLocalProvider(
    LocalDensity provides Density(d.density, fontScale = 1.0f)
  ) {
    MaterialTheme(
      colorScheme = DarkColorScheme,
      content = content
    )
  }
}
