package com.example.tools.pdf

import androidx.compose.runtime.Composable
import com.example.tools.ToolModule

object PdfAdvancedModule : ToolModule {
  override val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit> = mapOf(
    12 to { onBack -> PdfCompressorScreen(onBack = onBack) },
    13 to { onBack -> PdfToImagesScreen(onBack = onBack) },
    14 to { onBack -> ImagesToPdfScreen(onBack = onBack) }
  )
}
