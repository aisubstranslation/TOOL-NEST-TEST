package com.example.tools.fileandcrop

import androidx.compose.runtime.Composable
import com.example.tools.ToolModule
import com.example.tools.crop.ImageCropperScreen
import com.example.tools.file.FileInformationScreen
import com.example.tools.hash.Sha256Screen
import com.example.tools.zip.ZipCreatorScreen

object FileAndCropModule : ToolModule {
  override val screens: Map<Int, @Composable (onBack: () -> Unit) -> Unit> = mapOf(
    4 to { onBack -> ImageCropperScreen(onBack = onBack) },
    6 to { onBack -> FileInformationScreen(onBack = onBack) },
    7 to { onBack -> Sha256Screen(onBack = onBack) },
    8 to { onBack -> ZipCreatorScreen(onBack = onBack) }
  )
}
