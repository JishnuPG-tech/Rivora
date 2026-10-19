package com.rivora.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType

/**
 * Standard Card Container (22dp radius, subtle border, optional ambient orange aura)
 */
@Composable
fun RivoraCard(
  modifier: Modifier = Modifier,
  hasAura: Boolean = false,
  content: @Composable ColumnScope.() -> Unit
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(22.dp))
      .background(RivoraColor.Surface)
      .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(22.dp))
  ) {
    if (hasAura) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .background(
            Brush.radialGradient(
              colors = listOf(
                RivoraColor.Orange.copy(alpha = 0.22f),
                RivoraColor.OrangeDeep.copy(alpha = 0.10f),
                Color.Transparent
              ),
              center = Offset(850f, -50f),
              radius = 550f
            )
          )
      )
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      content = content
    )
  }
}

/**
 * Inset Box Container (16dp radius, #222225 bg, #27272A border)
 */
@Composable
fun RivoraInsetBox(
  modifier: Modifier = Modifier,
  padding: Dp = 14.dp,
  content: @Composable () -> Unit
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(RivoraColor.CardInner)
      .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(16.dp))
      .padding(padding)
  ) {
    content()
  }
}

/**
 * Button Variants matching authentic Rivora specs
 */
enum class RivoraButtonVariant {
  DEFAULT,
  DARK,
  AMBER,
  DANGER,
  AMBER_OUTLINE
}

@Composable
fun RivoraButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  variant: RivoraButtonVariant = RivoraButtonVariant.DEFAULT,
  height: Dp = 38.dp,
  enabled: Boolean = true,
  icon: (@Composable () -> Unit)? = null
) {
  val (bg, border, textColor, fontWeight) = when (variant) {
    RivoraButtonVariant.DEFAULT -> Quad(RivoraColor.Surface, RivoraColor.BorderDefault, RivoraColor.TextPrimary, FontWeight.Medium)
    RivoraButtonVariant.DARK -> Quad(RivoraColor.ButtonDark, RivoraColor.ButtonDarkBorder, RivoraColor.TextPrimary, FontWeight.Medium)
    RivoraButtonVariant.AMBER -> Quad(RivoraColor.Orange, RivoraColor.Orange, Color.White, FontWeight.SemiBold)
    RivoraButtonVariant.DANGER -> Quad(RivoraColor.DangerBg, RivoraColor.DangerBorder, RivoraColor.DangerText, FontWeight.Medium)
    RivoraButtonVariant.AMBER_OUTLINE -> Quad(RivoraColor.ButtonDark, RivoraColor.OrangeBorder, RivoraColor.Orange, FontWeight.Medium)
  }

  val interactionSource = remember { MutableInteractionSource() }

  Box(
    modifier = modifier
      .height(height)
      .clip(RoundedCornerShape(8.dp))
      .background(if (enabled) bg else bg.copy(alpha = 0.35f))
      .border(1.dp, if (enabled) border else border.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick
      )
      .padding(horizontal = 12.dp),
    contentAlignment = Alignment.Center
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      if (icon != null) {
        icon()
        Spacer(modifier = Modifier.width(6.dp))
      }
      Text(
        text = text,
        style = RivoraType.ButtonLabel.copy(
          color = if (enabled) textColor else textColor.copy(alpha = 0.35f),
          fontWeight = fontWeight
        ),
        maxLines = 1
      )
    }
  }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/**
 * Custom Pill Switch matching authentic Rivora orange toggle with white thumb
 */
@Composable
fun RivoraSwitch(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  activeTrackColor: Color = RivoraColor.Orange,
  enabled: Boolean = true
) {
  val thumbOffset by animateDpAsState(
    targetValue = if (checked) 16.dp else 2.dp,
    animationSpec = tween(150),
    label = "thumbOffset"
  )
  val trackColor by animateColorAsState(
    targetValue = when {
      !enabled -> Color(0xFF1E1E22)
      checked -> activeTrackColor
      else -> Color(0xFF26262F)
    },
    animationSpec = tween(150),
    label = "trackColor"
  )
  val thumbColor by animateColorAsState(
    targetValue = when {
      !enabled -> Color(0xFF505058)
      checked -> Color.White
      else -> Color(0xFF8E8E99)
    },
    animationSpec = tween(150),
    label = "thumbColor"
  )

  Box(
    modifier = modifier
      .size(44.dp, 44.dp) // Touch target
      .toggleable(
        value = checked,
        role = Role.Switch,
        enabled = enabled,
        onValueChange = onCheckedChange
      ),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .size(34.dp, 20.dp)
        .clip(RoundedCornerShape(999.dp))
        .background(trackColor)
        .border(1.dp, if (checked && enabled) activeTrackColor else Color(0xFF383844), RoundedCornerShape(999.dp))
    ) {
      Box(
        modifier = Modifier
          .offset(x = thumbOffset, y = 2.dp)
          .size(14.dp)
          .clip(CircleShape)
          .background(thumbColor)
      )
    }
  }
}

/**
 * Custom 18x18dp Checkbox matching Rivora orange check
 */
@Composable
fun RivoraCheckbox(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  checkColor: Color = RivoraColor.Orange,
  checkmarkColor: Color = Color(0xFF0A0A0B),
  touchTargetSize: Dp = 20.dp
) {
  Box(
    modifier = modifier
      .size(touchTargetSize)
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = { onCheckedChange(!checked) }
      ),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .size(16.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(if (checked) checkColor else Color.Transparent)
        .border(1.2.dp, if (checked) checkColor else RivoraColor.CheckboxUnchecked, RoundedCornerShape(4.dp)),
      contentAlignment = Alignment.Center
    ) {
      if (checked) {
        Canvas(modifier = Modifier.size(9.dp)) {
          val p = Path().apply {
            moveTo(1.2.dp.toPx(), 4.5.dp.toPx())
            lineTo(3.5.dp.toPx(), 6.8.dp.toPx())
            lineTo(7.8.dp.toPx(), 2.0.dp.toPx())
          }
          drawPath(
            path = p,
            color = checkmarkColor,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
          )
        }
      }
    }
  }
}

/**
 * Live Status Pill with animated pulsing dot
 */
@Composable
fun LiveStatusPill(
  text: String,
  isPaused: Boolean = false,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val alpha by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = 0.35f,
    animationSpec = infiniteRepeatable(
      animation = tween(750),
      repeatMode = RepeatMode.Reverse
    ),
    label = "alpha"
  )

  val bg = if (isPaused) Color(0x1F8E8E99) else RivoraColor.OrangeDim
  val border = if (isPaused) Color(0x4D8E8E99) else RivoraColor.OrangeBorder
  val textColor = if (isPaused) RivoraColor.TextSecondary else RivoraColor.OrangeBright

  Row(
    modifier = modifier
      .clip(RoundedCornerShape(999.dp))
      .background(bg)
      .border(1.dp, border, RoundedCornerShape(999.dp))
      .padding(horizontal = 10.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Box(
      modifier = Modifier
        .size(6.dp)
        .clip(CircleShape)
        .background(
          if (isPaused) RivoraColor.TextMuted else RivoraColor.OrangeBright.copy(alpha = alpha)
        )
    )
    Text(
      text = text,
      style = RivoraType.LivePill.copy(color = textColor),
      maxLines = 1
    )
  }
}

/**
 * File Icon Tile (42x42dp rounded tile)
 */
@Composable
fun FileTile(
  extension: String,
  modifier: Modifier = Modifier,
  color: Color = Color(0xFFC0C0CC)
) {
  Box(
    modifier = modifier
      .size(42.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(Color(0xFF22222A))
      .border(1.dp, Color(0xFF33333F), RoundedCornerShape(10.dp)),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = extension.uppercase().take(4),
      style = RivoraType.rivoraText(RivoraType.Mono, 11f, FontWeight.SemiBold, 0f, 14f).copy(color = color)
    )
  }
}

/**
 * Large File Progress Bar with Mixed Dual-Stream Support (Unified single bar blending Orange + White)
 */
@Composable
fun LargeProgressBar(
  progress: Float,
  modifier: Modifier = Modifier,
  secondaryProgress: Float = 0f,
  height: Dp = 6.dp
) {
  val totalRatio = (progress + secondaryProgress).coerceIn(0f, 1f)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(999.dp))
      .background(RivoraColor.ProgressTrack)
  ) {
    if (totalRatio > 0f) {
      val brush = if (secondaryProgress > 0.02f) {
        // Mixed single bar blending Orange Wi-Fi and White/Silver secondary
        Brush.horizontalGradient(
          colors = listOf(
            RivoraColor.Orange,
            RivoraColor.OrangeBright,
            Color(0xFFE8E8EE),
            Color(0xFFC8C8CE)
          )
        )
      } else {
        Brush.horizontalGradient(
          colors = listOf(RivoraColor.Orange, RivoraColor.OrangeBright)
        )
      }
      Box(
        modifier = Modifier
          .fillMaxWidth(totalRatio)
          .fillMaxHeight()
          .clip(RoundedCornerShape(999.dp))
          .background(brush)
      )
    }
  }
}

/**
 * Mini Stat Box (3-column summary items)
 */
@Composable
fun MiniStatBox(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  valueColor: Color = RivoraColor.TextPrimary
) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(RivoraColor.CardInner)
      .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
      .padding(horizontal = 8.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Text(
      text = label.uppercase(),
      style = RivoraType.MiniLabel.copy(color = RivoraColor.TextSecondary),
      maxLines = 1
    )
    Text(
      text = value,
      style = RivoraType.MiniValue.copy(color = valueColor),
      maxLines = 1
    )
  }
}

/**
 * Glowing Halo Network Dot
 */
@Composable
fun NetworkDot(
  color: Color,
  modifier: Modifier = Modifier,
  size: Dp = 10.dp,
  hasHalo: Boolean = true
) {
  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    if (hasHalo) {
      Box(
        modifier = Modifier
          .size(size)
          .clip(CircleShape)
          .background(color.copy(alpha = 0.28f))
      )
    }
    Box(
      modifier = Modifier
        .size(if (hasHalo) (size * 0.6f) else size)
        .clip(CircleShape)
        .background(color)
    )
  }
}
