package com.rivora.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivora.R
import com.rivora.theme.RivoraColor
import com.rivora.theme.RivoraType
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
  onNavigateToWelcome: () -> Unit,
  onNavigateToMain: () -> Unit
) {
  val context = LocalContext.current

  LaunchedEffect(Unit) {
    delay(1100)
    val prefs = context.getSharedPreferences("rivora_prefs", Context.MODE_PRIVATE)
    val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding_v3", false)
    if (hasSeenOnboarding) {
      onNavigateToMain()
    } else {
      onNavigateToWelcome()
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(RivoraColor.Canvas)
      .statusBarsPadding()
      .navigationBarsPadding(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.padding(horizontal = 32.dp)
    ) {
      // Minimal Logo
      Image(
        painter = painterResource(id = R.drawable.rivora_logo),
        contentDescription = "Rivora Logo",
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(14.dp))
      )

      Spacer(modifier = Modifier.height(18.dp))

      // Clean App Name
      Text(
        text = "Rivora",
        style = RivoraType.rivoraText(
          family = RivoraType.Sans,
          size = 28f,
          weight = FontWeight.Bold,
          tracking = -0.5f,
          line = 34f
        ).copy(color = RivoraColor.TextPrimary),
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(6.dp))

      // Short Tagline
      Text(
        text = "Dual-Channel Downloader",
        style = RivoraType.rivoraText(
          family = RivoraType.Sans,
          size = 14f,
          weight = FontWeight.Normal,
          tracking = 0f,
          line = 20f
        ).copy(color = RivoraColor.TextSecondary),
        textAlign = TextAlign.Center
      )
    }
  }
}
