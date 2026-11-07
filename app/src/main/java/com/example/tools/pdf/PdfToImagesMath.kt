package com.example.tools.pdf

import android.graphics.Bitmap
import com.example.file.FileNameSanitizer

object PdfToImagesMath {
  const val MAX_PIXELS_PER_PAGE = 16_000_000L // 16 Megapixels

  val DPI_PRESETS = listOf(72, 100, 150, 200, 300)

  fun calculatePixelDimensions(pageWidthPt: Int, pageHeightPt: Int, dpi: Int): Pair<Int, Int> {
    val pixelW = (pageWidthPt.toFloat() * dpi / 72f).toInt().coerceAtLeast(1)
    val pixelH = (pageHeightPt.toFloat() * dpi / 72f).toInt().coerceAtLeast(1)
    return pixelW to pixelH
  }

  fun checkExceedsMaxPixels(pageWidthPt: Int, pageHeightPt: Int, dpi: Int): Boolean {
    val (w, h) = calculatePixelDimensions(pageWidthPt, pageHeightPt, dpi)
    return (w.toLong() * h.toLong()) > MAX_PIXELS_PER_PAGE
  }

  fun formatPageImageName(rawName: String, pageIndex: Int, formatExt: String): String {
    val base = if (rawName.contains('.')) rawName.substringBeforeLast('.') else rawName
    val cleanBase = FileNameSanitizer.sanitizeFileName(base).replace(' ', '_')
    val pageNum = pageIndex + 1
    val padded = String.format("%03d", pageNum)
    val ext = formatExt.removePrefix(".")
    return "${cleanBase}_page_$padded.$ext"
  }

  /**
   * Fast grid check to detect if an image consists of a single solid uniform color (e.g. completely blank white page).
   */
  fun isUniformColor(bitmap: Bitmap): Boolean {
    if (bitmap.width <= 0 || bitmap.height <= 0) return true
    val sampleStepX = (bitmap.width / 16).coerceAtLeast(1)
    val sampleStepY = (bitmap.height / 16).coerceAtLeast(1)
    val firstColor = bitmap.getPixel(0, 0)

    for (x in 0 until bitmap.width step sampleStepX) {
      for (y in 0 until bitmap.height step sampleStepY) {
        if (bitmap.getPixel(x, y) != firstColor) {
          return false
        }
      }
    }
    return true
  }
}
