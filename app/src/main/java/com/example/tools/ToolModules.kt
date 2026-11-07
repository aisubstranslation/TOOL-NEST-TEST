package com.example.tools

import androidx.compose.runtime.Composable

/**
 * Registry of all implemented tool modules.
 * Each subsequent phase adds exactly one module here.
 */
val allToolModules: List<ToolModule> = listOf(
  com.example.tools.image.ImageToolsModule,
  com.example.tools.fileandcrop.FileAndCropModule,
  com.example.tools.pdf.PdfAndQrModule,
  com.example.tools.pdf.PdfAdvancedModule,
  com.example.tools.media.MediaAudioModule,
  com.example.tools.media.MediaVideoTrimModule,
  com.example.bgremove.BackgroundRemoverModule,
  com.example.tools.pdfeditor.PdfEditorModule,
  com.example.tools.aiinspect.AiImageInspectorModule
)

object ToolRouter {

  fun getToolScreen(toolId: Int): (@Composable (onBack: () -> Unit) -> Unit)? {
    for (module in allToolModules) {
      val screen = module.screens[toolId]
      if (screen != null) {
        return screen
      }
    }
    return null
  }

  fun isToolAvailable(toolId: Int): Boolean {
    return getToolScreen(toolId) != null
  }
}
