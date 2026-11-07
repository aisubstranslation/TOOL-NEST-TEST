package com.example.tools.pdf

import androidx.compose.runtime.Composable
import com.example.tools.ToolModule
import com.example.tools.qr.QrCodeScreen

object PdfAndQrModule : ToolModule {

  override val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit> = mapOf(
    9 to { onBack -> QrCodeScreen(onBack = onBack) },
    10 to { onBack -> PdfMergeScreen(onBack = onBack) },
    11 to { onBack -> PdfSplitScreen(onBack = onBack) },
    15 to { onBack -> PdfRotatorScreen(onBack = onBack) }
  )
}
