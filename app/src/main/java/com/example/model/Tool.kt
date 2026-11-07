package com.example.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

// Immutable: lets Compose skip re-composing tool cards that did not change (smoother scrolling).
@Immutable
data class Tool(
  val id: Int,
  val category: Category,
  val phase: Int,
  @StringRes val nameRes: Int,
  @StringRes val descRes: Int,
  val icon: ImageVector,
  val defaultName: String,
  val keywords: List<String>
)
