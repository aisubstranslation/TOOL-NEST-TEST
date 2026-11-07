package com.example.tools.aiinspect

import androidx.compose.runtime.Composable
import com.example.tools.ToolModule

object AiImageInspectorModule : ToolModule {
  override val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit> = mapOf(
    TOOL_ID_AI_INSPECTOR to { onBack -> AiImageInspectorScreen(onBack = onBack) }
  )
}
