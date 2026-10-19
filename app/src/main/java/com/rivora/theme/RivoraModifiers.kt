package com.rivora.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.rivoraSurface(
  bg: Color = RivoraColor.Surface,
  border: Color = RivoraColor.BorderSubtle,
  radius: Dp = 14.dp,
  borderWidth: Dp = 1.dp
) = this
  .clip(RoundedCornerShape(radius))
  .background(bg)
  .border(borderWidth, border, RoundedCornerShape(radius))
