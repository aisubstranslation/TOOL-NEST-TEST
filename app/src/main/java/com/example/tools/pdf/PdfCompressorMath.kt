package com.example.tools.pdf

enum class CompressionMode {
  OPTIMIZE_IMAGES,      // Mode A: keeps text, optimizes embedded images
  PAGES_TO_IMAGES       // Mode B: renders full pages to images (maximum reduction, loses text selectability)
}

enum class CompressionPreset(
  val maxEdgePx: Int,
  val jpegQuality: Int,
  val modeBDpi: Int
) {
  HIGHER_QUALITY(maxEdgePx = 2400, jpegQuality = 85, modeBDpi = 160),
  BALANCED(maxEdgePx = 1600, jpegQuality = 70, modeBDpi = 130),
  SMALLER_FILE(maxEdgePx = 1200, jpegQuality = 55, modeBDpi = 100)
}

data class CompressionStats(
  val originalSizeBytes: Long,
  val compressedSizeBytes: Long,
  val imagesRecompressed: Int = 0,
  val imagesSkipped: Int = 0,
  val mode: CompressionMode = CompressionMode.OPTIMIZE_IMAGES
) {
  val bytesSaved: Long get() = (originalSizeBytes - compressedSizeBytes).coerceAtLeast(0L)

  val percentageReduction: Int
    get() = if (originalSizeBytes > 0 && compressedSizeBytes < originalSizeBytes) {
      (((originalSizeBytes - compressedSizeBytes).toDouble() / originalSizeBytes) * 100).toInt()
    } else 0

  val isSmaller: Boolean get() = compressedSizeBytes < originalSizeBytes
}

object PdfCompressorMath {

  fun classifyResult(origSize: Long, compressedSize: Long): String {
    return when {
      origSize <= 0L -> "INVALID_INPUT"
      compressedSize < origSize -> "REDUCED"
      compressedSize == origSize -> "EQUAL"
      else -> "LARGER"
    }
  }

  fun calculateReductionPercentage(origSize: Long, compressedSize: Long): Int {
    if (origSize <= 0L || compressedSize >= origSize) return 0
    val saved = origSize - compressedSize
    return ((saved.toDouble() / origSize) * 100).toInt()
  }

  fun getPresetParams(preset: CompressionPreset): Triple<Int, Int, Int> {
    return Triple(preset.maxEdgePx, preset.jpegQuality, preset.modeBDpi)
  }
}
