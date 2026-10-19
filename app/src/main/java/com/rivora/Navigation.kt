package com.rivora

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.rivora.ui.main.MainScreen
import com.rivora.ui.screens.SplashScreen
import com.rivora.ui.screens.WelcomeScreen

@Composable
fun MainNavigation(
  onRequestNotificationPermission: () -> Unit = {}
) {
  val context = LocalContext.current
  val backStack = rememberNavBackStack(Splash)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Splash> {
          SplashScreen(
            onNavigateToWelcome = {
              backStack.clear()
              backStack.add(Welcome)
            },
            onNavigateToMain = {
              backStack.clear()
              backStack.add(Main)
              onRequestNotificationPermission()
            }
          )
        }
        entry<Welcome> {
          WelcomeScreen(
            onFinish = {
              val prefs = context.getSharedPreferences("rivora_prefs", Context.MODE_PRIVATE)
              prefs.edit().putBoolean("has_seen_onboarding_v3", true).apply()
              backStack.clear()
              backStack.add(Main)
              onRequestNotificationPermission()
            }
          )
        }
        entry<Main> {
          MainScreen()
        }
      },
  )
}
