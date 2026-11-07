package com.example.ui.settings

import androidx.compose.runtime.Composable

/**
 * Extension list for settings sections.
 * Later phases can contribute custom settings composables here without altering core settings.
 */
val settingsExtensions: List<@Composable () -> Unit> = listOf(
  { ImageSettingsSection() },
  { PdfSettingsSection() },
  { MediaSettingsSection() },
  { VideoSettingsSection() }
)
