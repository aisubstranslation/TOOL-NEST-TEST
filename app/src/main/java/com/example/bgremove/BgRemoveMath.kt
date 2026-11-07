package com.example.bgremove

import kotlin.math.roundToInt

object BgRemoveMath {

  const val MODEL_INPUT_SIZE = 320
  const val MAX_IMAGE_MEGAPIXELS = 16
  const val MAX_IMAGE_PIXELS = 16 * 1024 * 1024 // 16,777,216 pixels (~4608 x 3456)
  const val MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L // 50 MB

  // ImageNet RGB normalization constants used by rembg / U2-Net
  val MEAN_RGB = floatArrayOf(0.485f, 0.456f, 0.406f)
  val STD_RGB = floatArrayOf(0.229f, 0.224f, 0.225f)

  /**
   * Checks if image dimensions are within the 16 MP limit.
   */
  fun isWithinMegapixelLimit(width: Int, height: Int, maxPixels: Int = MAX_IMAGE_PIXELS): Boolean {
    if (width <= 0 || height <= 0) return false
    val totalPixels = width.toLong() * height.toLong()
    return totalPixels <= maxPixels.toLong()
  }

  /**
   * Pre-processing for U2-Net (rembg convention):
   * 1. Finds the maximum pixel value across all RGB channels (at least 1e-6).
   * 2. Divides all RGB values by maxVal.
   * 3. Normalizes each channel: (value - mean) / std.
   * 4. Packs into NCHW FloatArray of shape [1, 3, height, width].
   *
   * @param rgbPixels IntArray of ARGB pixels where each Int is 0xAARRGGBB.
   */
  fun preprocessRgbPixels(
    rgbPixels: IntArray,
    width: Int,
    height: Int
  ): FloatArray {
    val numPixels = width * height
    require(rgbPixels.size == numPixels) { "Pixel array size (${rgbPixels.size}) must match width*height ($numPixels)" }

    // 1. Find max pixel channel value across the image
    var maxPixelVal = 0
    for (i in 0 until numPixels) {
      val c = rgbPixels[i]
      val r = (c shr 16) and 0xFF
      val g = (c shr 8) and 0xFF
      val b = c and 0xFF
      if (r > maxPixelVal) maxPixelVal = r
      if (g > maxPixelVal) maxPixelVal = g
      if (b > maxPixelVal) maxPixelVal = b
    }

    val maxVal = if (maxPixelVal > 0) maxPixelVal.toFloat() else 1.0f

    // Float tensor for NCHW [1, 3, height, width]
    val tensor = FloatArray(3 * numPixels)
    val rOffset = 0
    val gOffset = numPixels
    val bOffset = 2 * numPixels

    val meanR = MEAN_RGB[0]
    val meanG = MEAN_RGB[1]
    val meanB = MEAN_RGB[2]

    val stdR = STD_RGB[0]
    val stdG = STD_RGB[1]
    val stdB = STD_RGB[2]

    for (i in 0 until numPixels) {
      val c = rgbPixels[i]
      val r = (c shr 16) and 0xFF
      val g = (c shr 8) and 0xFF
      val b = c and 0xFF

      val rNorm = ((r.toFloat() / maxVal) - meanR) / stdR
      val gNorm = ((g.toFloat() / maxVal) - meanG) / stdG
      val bNorm = ((b.toFloat() / maxVal) - meanB) / stdB

      tensor[rOffset + i] = rNorm
      tensor[gOffset + i] = gNorm
      tensor[bOffset + i] = bNorm
    }

    return tensor
  }

  /**
   * Post-processing min-max normalization:
   * Maps values to 0.0..1.0 using: (v - min) / max(max - min, 1e-6).
   * If all values in the array are equal (max == min), returns an array of zeros.
   */
  fun minMaxNormalize(raw: FloatArray): FloatArray {
    if (raw.isEmpty()) return FloatArray(0)

    var minVal = Float.POSITIVE_INFINITY
    var maxVal = Float.NEGATIVE_INFINITY

    for (v in raw) {
      if (v < minVal) minVal = v
      if (v > maxVal) maxVal = v
    }

    val diff = maxVal - minVal
    val out = FloatArray(raw.size)

    if (diff <= 1e-6f) {
      // Constant output or negligible difference -> 0.0
      return out
    }

    for (i in raw.indices) {
      out[i] = ((raw[i] - minVal) / diff).coerceIn(0f, 1f)
    }

    return out
  }

  /**
   * Converts 0..1 normalized float array to 8-bit alpha mask byte array (0..255).
   */
  fun normalizedTo8BitMask(normalized: FloatArray): ByteArray {
    val out = ByteArray(normalized.size)
    for (i in normalized.indices) {
      val byteVal = (normalized[i] * 255f).roundToInt().coerceIn(0, 255)
      out[i] = byteVal.toByte()
    }
    return out
  }

  /**
   * Applies an in-place or new 1D separable box blur on an 8-bit mask.
   * Radius 0 = exact mask.
   * Radius 1..3 = horizontal pass + vertical pass box blur.
   */
  fun applyBoxBlur(mask: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
    if (radius <= 0 || width <= 0 || height <= 0 || mask.isEmpty()) {
      return mask.copyOf()
    }

    val r = radius.coerceIn(1, 3)
    val temp = IntArray(width * height)
    val result = ByteArray(width * height)

    // Horizontal pass
    for (y in 0 until height) {
      val rowOffset = y * width
      var sum = 0
      val windowSize = 2 * r + 1

      for (x in -r..r) {
        val clampedX = x.coerceIn(0, width - 1)
        sum += mask[rowOffset + clampedX].toInt() and 0xFF
      }

      for (x in 0 until width) {
        temp[rowOffset + x] = sum / windowSize

        // Slide window
        val xRemove = (x - r).coerceIn(0, width - 1)
        val xAdd = (x + r + 1).coerceIn(0, width - 1)
        sum += (mask[rowOffset + xAdd].toInt() and 0xFF) - (mask[rowOffset + xRemove].toInt() and 0xFF)
      }
    }

    // Vertical pass
    val windowSize = 2 * r + 1
    for (x in 0 until width) {
      var sum = 0
      for (y in -r..r) {
        val clampedY = y.coerceIn(0, height - 1)
        sum += temp[clampedY * width + x]
      }

      for (y in 0 until height) {
        result[y * width + x] = (sum / windowSize).coerceIn(0, 255).toByte()

        // Slide window
        val yRemove = (y - r).coerceIn(0, height - 1)
        val yAdd = (y + r + 1).coerceIn(0, height - 1)
        sum += temp[yAdd * width + x] - temp[yRemove * width + x]
      }
    }

    return result
  }

  /**
   * Generates output filename based on format.
   * Default: "<name>_cutout.<ext>"
   */
  fun generateCutoutOutputName(inputName: String, format: String): String {
    val baseName = inputName.substringBeforeLast(".").ifEmpty { "image" }
    val ext = when (format.lowercase()) {
      "png" -> "png"
      "jpg", "jpeg" -> "jpg"
      "webp" -> "webp"
      else -> "png"
    }
    return "${baseName}_cutout.$ext"
  }

  /**
   * Clamps the Before/After split fraction safely. If width is 0, negative, NaN or Infinite,
   * returns the safe default fraction (default 0.5f).
   */
  fun clampSplitFraction(
    touchX: Float,
    canvasWidth: Float,
    minFraction: Float = 0.05f,
    maxFraction: Float = 0.95f,
    defaultFraction: Float = 0.5f
  ): Float {
    if (canvasWidth <= 0f || canvasWidth.isNaN() || canvasWidth.isInfinite() || touchX.isNaN() || touchX.isInfinite()) {
      return defaultFraction
    }
    val fraction = touchX / canvasWidth
    if (fraction.isNaN() || fraction.isInfinite()) {
      return defaultFraction
    }
    return fraction.coerceIn(minFraction, maxFraction)
  }

  /**
   * Computes a unique string cache key for preview cutout bitmaps.
   */
  fun computeCutoutCacheKey(uriString: String, maskVersion: Int, edgeSoftness: Int): String {
    return "$uriString:$maskVersion:$edgeSoftness"
  }

  /**
   * Calculates a power-of-two inSampleSize for decoding background images such that
   * the long edge is at most approximately the targetLongEdge.
   * Example: (6000, 4000, target 2000) -> 2
   */
  fun calculateBackgroundSampleSize(origWidth: Int, origHeight: Int, targetLongEdge: Int): Int {
    if (origWidth <= 0 || origHeight <= 0 || targetLongEdge <= 0) return 1
    val longEdge = maxOf(origWidth, origHeight)
    var sampleSize = 1
    while ((longEdge / (sampleSize * 2)) >= targetLongEdge) {
      sampleSize *= 2
    }
    return sampleSize
  }

  /**
   * Pure Kotlin bilinear interpolation for upscaling/downscaling a mask ByteArray (0..255).
   * Aligns centers: u = (x + 0.5f) * srcW / dstW - 0.5f
   */
  fun bilinearUpscaleMask(src: ByteArray, srcW: Int, srcH: Int, dstW: Int, dstH: Int): ByteArray {
    if (dstW <= 0 || dstH <= 0 || srcW <= 0 || srcH <= 0 || src.isEmpty()) {
      return ByteArray(maxOf(0, dstW * dstH))
    }
    if (srcW == dstW && srcH == dstH) {
      return src.copyOf()
    }

    val dst = ByteArray(dstW * dstH)
    val scaleX = srcW.toFloat() / dstW.toFloat()
    val scaleY = srcH.toFloat() / dstH.toFloat()

    for (y in 0 until dstH) {
      val srcY = (y + 0.5f) * scaleY - 0.5f
      val y0 = srcY.toInt().coerceIn(0, srcH - 1)
      val y1 = (y0 + 1).coerceIn(0, srcH - 1)
      val fy = (srcY - y0).coerceIn(0f, 1f)

      val rowOffset0 = y0 * srcW
      val rowOffset1 = y1 * srcW
      val dstRowOffset = y * dstW

      for (x in 0 until dstW) {
        val srcX = (x + 0.5f) * scaleX - 0.5f
        val x0 = srcX.toInt().coerceIn(0, srcW - 1)
        val x1 = (x0 + 1).coerceIn(0, srcW - 1)
        val fx = (srcX - x0).coerceIn(0f, 1f)

        val v00 = src[rowOffset0 + x0].toInt() and 0xFF
        val v10 = src[rowOffset0 + x1].toInt() and 0xFF
        val v01 = src[rowOffset1 + x0].toInt() and 0xFF
        val v11 = src[rowOffset1 + x1].toInt() and 0xFF

        val top = v00 + fx * (v10 - v00)
        val bottom = v01 + fx * (v11 - v01)
        val interp = (top + fy * (bottom - top)).roundToInt().coerceIn(0, 255)

        dst[dstRowOffset + x] = interp.toByte()
      }
    }
    return dst
  }

  /**
   * Stamps a circle with anti-aliasing / soft edge directly into mask ByteArray.
   * ERASE sets alpha to 0; RESTORE sets alpha to 255 (or blends towards target).
   */
  fun stampCircle(
    mask: ByteArray,
    width: Int,
    height: Int,
    cx: Float,
    cy: Float,
    radius: Float,
    mode: BrushMode
  ) {
    if (radius <= 0f || width <= 0 || height <= 0 || mask.size != width * height) return

    val minX = (cx - radius - 1f).toInt().coerceIn(0, width - 1)
    val maxX = (cx + radius + 1f).toInt().coerceIn(0, width - 1)
    val minY = (cy - radius - 1f).toInt().coerceIn(0, height - 1)
    val maxY = (cy + radius + 1f).toInt().coerceIn(0, height - 1)

    val rSquared = radius * radius
    val innerR = (radius - 1f).coerceAtLeast(0f)
    val innerRSquared = innerR * innerR

    for (y in minY..maxY) {
      val dy = y - cy
      val dy2 = dy * dy
      val rowOffset = y * width
      for (x in minX..maxX) {
        val dx = x - cx
        val dist2 = dx * dx + dy2
        if (dist2 <= innerRSquared) {
          // Fully inside circle
          mask[rowOffset + x] = if (mode == BrushMode.ERASE) 0.toByte() else 255.toByte()
        } else if (dist2 <= rSquared) {
          // Antialiased boundary pixel (fractional falloff)
          val dist = kotlin.math.sqrt(dist2)
          val frac = (radius - dist).coerceIn(0f, 1f)
          val current = mask[rowOffset + x].toInt() and 0xFF
          val target = if (mode == BrushMode.ERASE) 0 else 255
          val updated = if (mode == BrushMode.ERASE) {
            (current * (1f - frac)).roundToInt().coerceIn(0, 255)
          } else {
            (current + (target - current) * frac).roundToInt().coerceIn(0, 255)
          }
          mask[rowOffset + x] = updated.toByte()
        }
      }
    }
  }

  /**
   * Stamps a continuous line segment by placing circles with spacing <= radius / 3.
   */
  fun stampLine(
    mask: ByteArray,
    width: Int,
    height: Int,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    radius: Float,
    mode: BrushMode
  ) {
    val dx = x1 - x0
    val dy = y1 - y0
    val dist = kotlin.math.hypot(dx, dy)
    val step = (radius / 3f).coerceIn(1f, 10f)

    if (dist <= step) {
      stampCircle(mask, width, height, x1, y1, radius, mode)
      return
    }

    val numSteps = (dist / step).toInt().coerceAtLeast(1)
    for (i in 0..numSteps) {
      val t = i.toFloat() / numSteps.toFloat()
      val px = x0 + t * dx
      val py = y0 + t * dy
      stampCircle(mask, width, height, px, py, radius, mode)
    }
  }

  /**
   * Explicit pixel math compositing (no PorterDuff):
   * out = (maskAlpha shl 24) or (origPixel and 0x00FFFFFF)
   */
  fun compositePixels(
    origRgb: IntArray,
    mask: ByteArray,
    outPixels: IntArray,
    offset: Int,
    length: Int
  ) {
    for (i in 0 until length) {
      val idx = offset + i
      val alpha = mask[idx].toInt() and 0xFF
      val rgb = origRgb[idx] and 0x00FFFFFF
      outPixels[idx] = (alpha shl 24) or rgb
    }
  }
}
