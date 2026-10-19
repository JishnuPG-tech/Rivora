package com.rivora.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.R
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(
  onFinish: () -> Unit
) {
  val pagerState = rememberPagerState(pageCount = { 3 })
  val coroutineScope = rememberCoroutineScope()

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(RivoraColor.Canvas)
      .statusBarsPadding()
      .navigationBarsPadding()
  ) {
    Column(
      modifier = Modifier.fillMaxSize()
    ) {
      // Top Navigation Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // App Identity: Logo + Brand Name
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Image(
            painter = painterResource(id = R.drawable.rivora_logo),
            contentDescription = "Rivora Logo",
            modifier = Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(8.dp))
          )
          Text(
            text = "Rivora",
            style = RivoraType.rivoraText(
              family = RivoraType.Sans,
              size = 18f,
              weight = FontWeight.Bold,
              tracking = 0.5f,
              line = 22f
            ).copy(color = RivoraColor.TextPrimary)
          )
        }

        // Minimal Skip Button
        if (pagerState.currentPage < 2) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(RivoraColor.SurfaceRaised)
              .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(8.dp))
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinish
              )
              .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "Skip",
              style = RivoraType.rivoraText(
                family = RivoraType.Sans,
                size = 12.5f,
                weight = FontWeight.Medium,
                tracking = 0.2f,
                line = 15f
              ).copy(color = RivoraColor.TextSecondary)
            )
          }
        } else {
          Spacer(modifier = Modifier.width(48.dp))
        }
      }

      // Minimal Slides Pager
      HorizontalPager(
        state = pagerState,
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
      ) { page ->
        when (page) {
          0 -> MinimalWelcomeSlide(
            icon = Icons.Rounded.Bolt,
            tag = "CONCURRENCY",
            title = "Dual-Channel Turbo",
            description = "Download over Wi-Fi and Cellular at the same time for faster throughput."
          )
          1 -> MinimalWelcomeSlide(
            icon = Icons.Rounded.Layers,
            tag = "RESUMABLE",
            title = "Dynamic Slices",
            description = "Files are split into chunks. Pause, resume, or switch networks anytime."
          )
          2 -> MinimalWelcomeSlide(
            icon = Icons.Rounded.Folder,
            tag = "STORAGE",
            title = "Direct to Downloads",
            description = "Files save directly to your Android Downloads folder, ready in your file manager."
          )
        }
      }

      // Bottom Bar: Indicators & Action Button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Page Indicator Pills
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          repeat(3) { index ->
            val isSelected = pagerState.currentPage == index
            val width by animateDpAsState(
              targetValue = if (isSelected) 24.dp else 8.dp,
              animationSpec = tween(200),
              label = "pillWidth"
            )
            val color by animateColorAsState(
              targetValue = if (isSelected) RivoraColor.OrangeBright else RivoraColor.ProgressTrack,
              animationSpec = tween(200),
              label = "pillColor"
            )

            Box(
              modifier = Modifier
                .height(6.dp)
                .width(width)
                .clip(CircleShape)
                .background(color)
                .clickable(
                  interactionSource = remember { MutableInteractionSource() },
                  indication = null,
                  onClick = {
                    coroutineScope.launch {
                      pagerState.animateScrollToPage(index)
                    }
                  }
                )
            )
          }
        }

        // Action Button: Next or Get Started
        if (pagerState.currentPage < 2) {
          Box(
            modifier = Modifier
              .height(44.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(RivoraColor.SurfaceRaised)
              .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(10.dp))
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                  coroutineScope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                  }
                }
              )
              .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = "Next",
                style = RivoraType.rivoraText(
                  family = RivoraType.Sans,
                  size = 14f,
                  weight = FontWeight.SemiBold,
                  tracking = 0f,
                  line = 18f
                ).copy(color = RivoraColor.TextPrimary)
              )
              Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Next",
                tint = RivoraColor.OrangeBright,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        } else {
          // Get Started Button
          Box(
            modifier = Modifier
              .height(44.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(
                Brush.horizontalGradient(
                  colors = listOf(
                    RivoraColor.Orange,
                    RivoraColor.OrangeBright
                  )
                )
              )
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFinish
              )
              .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = "Get Started",
                style = RivoraType.rivoraText(
                  family = RivoraType.Sans,
                  size = 14f,
                  weight = FontWeight.Bold,
                  tracking = 0.2f,
                  line = 18f
                ).copy(color = Color(0xFF0A0A0B))
              )
              Icon(
                imageVector = Icons.Rounded.RocketLaunch,
                contentDescription = "Get Started",
                tint = Color(0xFF0A0A0B),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MinimalWelcomeSlide(
  icon: ImageVector,
  tag: String,
  title: String,
  description: String
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    // Clean Minimal Icon Box
    Box(
      modifier = Modifier
        .size(76.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(RivoraColor.Surface)
        .border(1.dp, RivoraColor.BorderSubtle, RoundedCornerShape(20.dp)),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = RivoraColor.OrangeBright,
        modifier = Modifier.size(34.dp)
      )
    }

    Spacer(modifier = Modifier.height(28.dp))

    // Tag Pill
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(6.dp))
        .background(RivoraColor.OrangeDim)
        .border(1.dp, RivoraColor.OrangeBorder.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
        .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
      Text(
        text = tag,
        style = RivoraType.rivoraText(
          family = RivoraType.Mono,
          size = 10f,
          weight = FontWeight.SemiBold,
          tracking = 1.2f,
          line = 13f
        ).copy(color = RivoraColor.OrangeBright)
      )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Title
    Text(
      text = title,
      style = RivoraType.rivoraText(
        family = RivoraType.Sans,
        size = 23f,
        weight = FontWeight.Bold,
        tracking = -0.3f,
        line = 28f
      ).copy(color = RivoraColor.TextPrimary),
      textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(10.dp))

    // Short Description
    Text(
      text = description,
      style = RivoraType.rivoraText(
        family = RivoraType.Sans,
        size = 14f,
        weight = FontWeight.Normal,
        tracking = 0f,
        line = 21f
      ).copy(color = RivoraColor.TextSecondary),
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 12.dp)
    )
  }
}
