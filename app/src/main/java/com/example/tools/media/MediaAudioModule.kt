package com.example.tools.media

import androidx.compose.runtime.Composable
import com.example.tools.ToolModule

object MediaAudioModule : ToolModule {
  override val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit> = mapOf(
    16 to { onBack -> VideoToAudioScreen(onBack = onBack) },
    17 to { onBack -> AudioConverterScreen(onBack = onBack) }
  )
}
