package com.rivora.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

private val NoFontPad = PlatformTextStyle(includeFontPadding = false)
private val CenterLine = LineHeightStyle(
  alignment = LineHeightStyle.Alignment.Center,
  trim = LineHeightStyle.Trim.None
)

fun rivoraText(
  family: FontFamily = FontFamily.Default,
  size: Float,
  weight: FontWeight,
  tracking: Float = 0f,
  line: Float
) = TextStyle(
  fontFamily = family,
  fontSize = size.sp,
  fontWeight = weight,
  letterSpacing = tracking.sp,
  lineHeight = line.sp,
  platformStyle = NoFontPad,
  lineHeightStyle = CenterLine
)

object RivoraType {
  val Mono = FontFamily.Monospace
  val Sans = FontFamily.Default

  fun rivoraText(
    family: FontFamily = FontFamily.Default,
    size: Float,
    weight: FontWeight,
    tracking: Float = 0f,
    line: Float
  ) = com.rivora.theme.rivoraText(family, size, weight, tracking, line)

  val Eyebrow = rivoraText(Mono, 10.5f, FontWeight.Medium, 0.84f, 14f)
  val HeroSpeed = rivoraText(Mono, 32f, FontWeight.Bold, -0.96f, 35.2f)
  val HeroUnit = rivoraText(Mono, 13.5f, FontWeight.Normal, 0f, 18f)
  val ThroughputBig = rivoraText(Mono, 30f, FontWeight.Bold, -0.9f, 30f)
  val ThroughputUnit = rivoraText(Mono, 13f, FontWeight.Normal, 0f, 17f)
  val FileTitle = rivoraText(Sans, 14f, FontWeight.SemiBold, 0f, 19.6f)
  val BackLabel = rivoraText(Sans, 15f, FontWeight.Medium, 0f, 20f)
  val ListTitle = rivoraText(Sans, 15.5f, FontWeight.SemiBold, 0f, 21f)
  val DialogTitle = rivoraText(Sans, 15f, FontWeight.SemiBold, 0f, 20f)
  val headingCard = rivoraText(Sans, 18f, FontWeight.SemiBold, -0.2f, 24f)
  val headingScreen = rivoraText(Sans, 22f, FontWeight.SemiBold, -0.35f, 28f)
  val AdapterTitle = rivoraText(Sans, 14.5f, FontWeight.SemiBold, 0f, 20f)
  val ButtonLabel = rivoraText(Sans, 12.5f, FontWeight.Medium, 0f, 16f)
  val TabLabel = rivoraText(Sans, 12f, FontWeight.Medium, 0f, 16f)
  val RowName = rivoraText(Sans, 13.5f, FontWeight.Medium, 0f, 18f)
  val SplitName = rivoraText(Sans, 13f, FontWeight.Medium, 0f, 17f)
  val LivePill = rivoraText(Mono, 11f, FontWeight.Medium, 0f, 15f)
  val MetaLine = rivoraText(Mono, 11.5f, FontWeight.Normal, 0f, 15f)
  val RowSub = rivoraText(Mono, 11f, FontWeight.Normal, 0f, 15f)
  val GroupBar = rivoraText(Mono, 11f, FontWeight.Normal, 0.77f, 15f)
  val MiniLabel = rivoraText(Mono, 9.5f, FontWeight.Normal, 0.57f, 13f)
  val MiniValue = rivoraText(Mono, 12.5f, FontWeight.Bold, 0f, 16f)
  val AdapterSpeed = rivoraText(Mono, 15f, FontWeight.Bold, 0f, 20f)
  val AdapterStatLabel = rivoraText(Mono, 9.5f, FontWeight.Normal, 0f, 13f)
  val AdapterStatValue = rivoraText(Mono, 12.5f, FontWeight.Bold, 0f, 16f)
  val AvgPeak = rivoraText(Mono, 10.5f, FontWeight.Normal, 0f, 15.2f)
  val ChartAxis = rivoraText(Mono, 10f, FontWeight.Normal, 0f, 14f)
  val InputText = rivoraText(Mono, 12f, FontWeight.Normal, 0f, 16f)
  val PagerBtn = rivoraText(Mono, 11.5f, FontWeight.Normal, 0f, 15f)
  val FooterText = rivoraText(Mono, 11.5f, FontWeight.Normal, 0f, 15f)
  val UnitOpt = rivoraText(Mono, 10.5f, FontWeight.Normal, 0f, 14f)
}
