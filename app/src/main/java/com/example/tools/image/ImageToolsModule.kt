package com.example.tools.image

import androidx.compose.runtime.Composable
import com.example.tools.ToolModule

object ImageToolsModule : ToolModule {
  override val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit> = mapOf(
    1 to { onBack -> ImageCompressorScreen(onBack = onBack) },
    2 to { onBack -> ImageResizerScreen(onBack = onBack) },
    3 to { onBack -> ImageConverterScreen(onBack = onBack) },
    5 to { onBack -> MetadataRemoverScreen(onBack = onBack) }
  )
}
