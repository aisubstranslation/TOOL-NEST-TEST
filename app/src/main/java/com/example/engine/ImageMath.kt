package com.example.engine

import kotlin.math.roundToInt

object ImageMath {

  const val MAX_MEGAPIXELS: Int = 48

  fun calculateMegaPixels(width: Int, height: Int): Double {
    if (width <= 0 || height <= 0) return 0.0
    return (width.toLong() * height.toLong()) / 1_000_000.0
  }

  fun isWithinMegaPixelLimit(width: Int, height: Int, maxMegaPixels: Int = MAX_MEGAPIXELS): Boolean {
    if (width <= 0 || height <= 0) return false
    return (width.toLong() * height.toLong()) <= (maxMegaPixels.toLong() * 1_000_000L)
  }

  fun calculateDimensionsByPercentage(
    originalWidth: Int,
    originalHeight: Int,
    percentage: Int
  ): Pair<Int, Int> {
    if (originalWidth <= 0 || originalHeight <= 0 || percentage <= 0) {
      return 1 to 1
    }
    val factor = percentage / 100.0
    val targetW = maxOf(1, (originalWidth * factor).roundToInt())
    val targetH = maxOf(1, (originalHeight * factor).roundToInt())
    return targetW to targetH
  }

  fun calculateDimensionsLockedAspectByWidth(
    originalWidth: Int,
    originalHeight: Int,
    targetWidth: Int
  ): Pair<Int, Int> {
    if (originalWidth <= 0 || originalHeight <= 0 || targetWidth <= 0) {
      return 1 to 1
    }
    val aspect = originalHeight.toDouble() / originalWidth.toDouble()
    val targetH = maxOf(1, (targetWidth * aspect).roundToInt())
    return targetWidth to targetH
  }

  fun calculateDimensionsLockedAspectByHeight(
    originalWidth: Int,
    originalHeight: Int,
    targetHeight: Int
  ): Pair<Int, Int> {
    if (originalWidth <= 0 || originalHeight <= 0 || targetHeight <= 0) {
      return 1 to 1
    }
    val aspect = originalWidth.toDouble() / originalHeight.toDouble()
    val targetW = maxOf(1, (targetHeight * aspect).roundToInt())
    return targetW to targetHeight
  }

  fun calculateDimensionsForLongEdge(
    originalWidth: Int,
    originalHeight: Int,
    targetLongEdge: Int
  ): Pair<Int, Int> {
    if (originalWidth <= 0 || originalHeight <= 0 || targetLongEdge <= 0) {
      return 1 to 1
    }
    return if (originalWidth >= originalHeight) {
      calculateDimensionsLockedAspectByWidth(originalWidth, originalHeight, targetLongEdge)
    } else {
      calculateDimensionsLockedAspectByHeight(originalWidth, originalHeight, targetLongEdge)
    }
  }

  /**
   * Verifies that the file header matches known magic signatures:
   * - JPEG: FF D8 FF
   * - PNG: 89 50 4E 47 0D 0A 1A 0A
   * - WebP: RIFF (bytes 0..3) + 4 bytes + WEBP (bytes 8..11)
   */
  fun verifyMagicBytes(bytes: ByteArray, format: String): Boolean {
    if (bytes.size < 4) return false

    return when (format.trim().lowercase()) {
      "jpeg", "jpg" -> {
        bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xFF) == 0xD8 &&
            (bytes[2].toInt() and 0xFF) == 0xFF
      }
      "png" -> {
        bytes.size >= 8 &&
            (bytes[0].toInt() and 0xFF) == 0x89 &&
            (bytes[1].toInt() and 0xFF) == 0x50 && // 'P'
            (bytes[2].toInt() and 0xFF) == 0x4E && // 'N'
            (bytes[3].toInt() and 0xFF) == 0x47 && // 'G'
            (bytes[4].toInt() and 0xFF) == 0x0D &&
            (bytes[5].toInt() and 0xFF) == 0x0A &&
            (bytes[6].toInt() and 0xFF) == 0x1A &&
            (bytes[7].toInt() and 0xFF) == 0x0A
      }
      "webp" -> {
        bytes.size >= 12 &&
            (bytes[0].toInt() and 0xFF) == 0x52 && // 'R'
            (bytes[1].toInt() and 0xFF) == 0x49 && // 'I'
            (bytes[2].toInt() and 0xFF) == 0x46 && // 'F'
            (bytes[3].toInt() and 0xFF) == 0x46 && // 'F'
            (bytes[8].toInt() and 0xFF) == 0x57 && // 'W'
            (bytes[9].toInt() and 0xFF) == 0x45 && // 'E'
            (bytes[10].toInt() and 0xFF) == 0x42 && // 'B'
            (bytes[11].toInt() and 0xFF) == 0x50    // 'P'
      }
      else -> false
    }
  }
}
