package com.example.tools.media

import androidx.compose.runtime.Composable
import com.example.tools.ToolModule

object MediaVideoTrimModule : ToolModule {
  override val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit> = mapOf(
    18 to { onBack -> VideoCompressorScreen(onBack = onBack) },
    19 to { onBack -> AudioTrimmerScreen(onBack = onBack) }
  )
}
