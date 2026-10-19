package com.rivora.theme

import androidx.compose.ui.graphics.Color

object RivoraColor {
  // Core Surfaces & Deep Carbon Theme from UI Kit
  val Canvas = Color(0xFF0A0A0B)
  val Screen = Color(0xFF0E0E10)
  val Surface = Color(0xFF17171A)
  val SurfaceRaised = Color(0xFF222225)
  val SurfaceMid = Color(0xFF1D1D21)
  val SurfaceInset = Color(0xFF202022)

  val Outline = Color(0xFF27272A)
  val OutlineSoft = Color(0xFF343437)

  // Typography Colors
  val TextPrimary = Color(0xFFEDEDEF)
  val TextSecondary = Color(0xFFA0A0A7)
  val TextMuted = Color(0xFF85858D)
  val TextDisabled = Color(0xFF69696E)

  // Authentic Brand Accents
  val Orange = Color(0xFFF36712)
  val OrangeBright = Color(0xFFFF7A29)
  val OrangeDeep = Color(0xFF9F4814)
  val OrangeDim = Color(0x33F36712)
  val OrangeGlow = Color(0x4DF36712)
  val OrangeBorder = Color(0x80F36712)

  // Secondary Stream: Crisp Silver / Off-White
  val StreamSecondary = Color(0xFFC5C5CB)
  val StreamSecondaryDim = Color(0x33C5C5CB)
  val StreamSecondaryBorder = Color(0x66C5C5CB)

  // Chart & Progress
  val ChartNeutral = Color(0xFF6F6F74)
  val ProgressTrack = Color(0xFF333336)
  val White = Color(0xFFFFFFFF)

  // App Level mappings
  val App = Canvas
  val Elevated = SurfaceRaised
  val CardInner = SurfaceRaised
  val Modal = Surface
  val BorderSubtle = Outline
  val BorderDefault = OutlineSoft

  // Legacy mappings for backwards-compatibility
  val Teal = Orange
  val TealBright = OrangeBright
  val TealDim = OrangeDim
  val TealBorder = OrangeBorder

  val Slate = StreamSecondary
  val SlateDim = StreamSecondaryDim

  val Amber = Orange
  val AmberHover = OrangeBright
  val AmberText = White
  val AmberDim = OrangeDim
  val AmberBorder = OrangeBorder
  val AmberOutline = OrangeBorder

  val DangerBg = Color(0xFF381A1C)
  val DangerBorder = Color(0xFF5C2629)
  val DangerText = Color(0xFFFF6B6E)
  val Scrim = Color(0xD0000000)

  // Extended Surfaces
  val TabTrack = Color(0xFF121215)
  val AppFooter = Color(0xFF0F0F12)
  val PagerFooter = Color(0xFF131317)
  val ListGroupBar = Color(0xFF131317)
  val DialogFooter = Color(0xFF151518)
  val ButtonDark = SurfaceRaised
  val ButtonDarkHover = Color(0xFF2B2B30)
  val ButtonDarkBorder = Outline
  val GridLine = Color(0xFF222227)
  val ChartBorder = Outline
  val ChunkEmpty = Color(0xFF1C1C22)
  val ChunkBorder = Color(0xFF27272F)
  val CheckboxUnchecked = Color(0xFF3E3E48)
  val AxisText = TextMuted
}
