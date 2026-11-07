package com.example.tools.pdfeditor

import androidx.compose.runtime.Composable
import com.example.tools.ToolModule

object PdfEditorModule : ToolModule {
  override val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit> = mapOf(
    TOOL_ID_PDF_EDITOR to { onBack -> PdfEditorScreen(onBack = onBack) }
  )
}
