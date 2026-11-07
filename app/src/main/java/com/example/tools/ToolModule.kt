package com.example.tools

import androidx.compose.runtime.Composable

/**
 * Extension interface for tool modules.
 * Each phase implements this interface to register its screens.
 */
interface ToolModule {
  val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit>
}
