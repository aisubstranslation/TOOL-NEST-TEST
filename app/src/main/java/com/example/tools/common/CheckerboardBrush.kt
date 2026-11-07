package com.example.tools.common

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Cached transparency checkerboard. A single small tile is repeated by the GPU, so drawing it costs
 * one drawRect call instead of thousands (this is what made the brush canvas slow before).
 */
@Composable
fun rememberCheckerBrush(cell: Dp = 12.dp): ShaderBrush {
  val cellPx = with(LocalDensity.current) { cell.toPx() }.roundToInt().coerceAtLeast(4)
  return remember(cellPx) {
    val size = cellPx * 2
    val pixels = IntArray(size * size)
    val light = 0xFFFFFFFF.toInt()
    val dark = 0xFFB8B8B8.toInt()
    for (y in 0 until size) {
      for (x in 0 until size) {
        val isLight = ((x / cellPx) + (y / cellPx)) % 2 == 0
        pixels[y * size + x] = if (isLight) light else dark
      }
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bmp.setPixels(pixels, 0, size, 0, 0, size, size)
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
  }
}
