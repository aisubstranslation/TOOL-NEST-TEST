package com.example.bgremove

import androidx.compose.runtime.Composable
import com.example.tools.ToolModule

object BackgroundRemoverModule : ToolModule {
  override val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit> = mapOf(
    20 to { onBack -> BackgroundRemoverScreen(onBack = onBack) }
  )
}
