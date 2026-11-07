package com.example.tools.media

import kotlin.math.roundToInt

enum class VideoQualityPreset(val bpp: Float, val label: String) {
  HIGH(0.10f, "High"),
  BALANCED(0.07f, "Balanced"),
  SMALL(0.04f, "Small")
}

enum class VideoResolutionPreset(val shortEdge: Int?, val label: String) {
  ORIGINAL(null, "Original"),
  P1080(1080, "1080p"),
  P720(720, "720p"),
  P480(480, "480p"),
  P360(360, "360p")
}

object VideoCompressorMath {

  const val MIN_VIDEO_BITRATE_BPS = 300_000 // 300 kbps clamp
  const val DEFAULT_FRAME_RATE = 30
  const val MAX_CALC_FRAME_RATE = 60

  /**
   * Returns displayed width and height considering video rotation.
   * On 90 or 270 degrees, width and height are swapped for display.
   */
  fun getDisplayedDimensions(sourceWidth: Int, sourceHeight: Int, rotationDegrees: Int): Pair<Int, Int> {
    val normRot = ((rotationDegrees % 360) + 360) % 360
    return if (normRot == 90 || normRot == 270) {
      sourceHeight to sourceWidth
    } else {
      sourceWidth to sourceHeight
    }
  }

  /**
   * Checks whether a resolution preset is allowed (NEVER upscale).
   * Option is disabled if targetShortEdge >= displayedShortEdge.
   */
  fun isPresetAllowed(sourceWidth: Int, sourceHeight: Int, rotationDegrees: Int, targetShortEdge: Int?): Boolean {
    if (targetShortEdge == null) return true
    val (dispW, dispH) = getDisplayedDimensions(sourceWidth, sourceHeight, rotationDegrees)
    val displayedShortEdge = minOf(dispW, dispH)
    return targetShortEdge < displayedShortEdge
  }

  /**
   * Calculates target displayed dimensions (width x height) preserving aspect ratio,
   * scaled by targetShortEdge, with both numbers rounded to even integers.
   */
  fun calculateTargetDimensions(
    sourceWidth: Int,
    sourceHeight: Int,
    rotationDegrees: Int,
    targetShortEdge: Int?
  ): Pair<Int, Int> {
    val (dispW, dispH) = getDisplayedDimensions(sourceWidth, sourceHeight, rotationDegrees)
    if (targetShortEdge == null) {
      val evenW = makeEven(dispW)
      val evenH = makeEven(dispH)
      return evenW to evenH
    }

    val currentShort = minOf(dispW, dispH).toFloat()
    if (currentShort <= 0f) return makeEven(dispW) to makeEven(dispH)

    val scale = (targetShortEdge.toFloat() / currentShort).coerceAtMost(1.0f)
    val scaledW = (dispW * scale).roundToInt()
    val scaledH = (dispH * scale).roundToInt()

    return makeEven(scaledW) to makeEven(scaledH)
  }

  /**
   * Ensures dimension is an even number (required by H.264 video encoders).
   */
  fun makeEven(dimension: Int): Int {
    val v = dimension.coerceAtLeast(2)
    return if (v % 2 == 0) v else v - 1
  }

  /**
   * Computes target video bitrate in bits/sec based on preset:
   * bpp * width * height * frameRate (capped at 60),
   * clamped between 300 kbps and (sourceVideoBitrate * 0.9) when source bitrate is known.
   */
  fun calculateVideoBitrate(
    preset: VideoQualityPreset,
    outputWidth: Int,
    outputHeight: Int,
    sourceFrameRate: Int?,
    sourceVideoBitrate: Int?
  ): Int {
    val fps = (sourceFrameRate ?: DEFAULT_FRAME_RATE).coerceIn(1, MAX_CALC_FRAME_RATE)
    val rawBitrate = (preset.bpp * outputWidth.toDouble() * outputHeight.toDouble() * fps.toDouble()).roundToInt()

    val maxClamp = if (sourceVideoBitrate != null && sourceVideoBitrate > 0) {
      (sourceVideoBitrate * 0.9).roundToInt()
    } else {
      Int.MAX_VALUE
    }

    val effectiveMax = maxOf(MIN_VIDEO_BITRATE_BPS, maxClamp)
    return rawBitrate.coerceIn(MIN_VIDEO_BITRATE_BPS, effectiveMax)
  }

  /**
   * Estimates total output file size in bytes:
   * (videoBitrate + audioBitrate) * durationSec / 8
   */
  fun calculateEstimatedOutputSize(
    videoBitrateBps: Int,
    audioBitrateBps: Int,
    durationSec: Double
  ): Long {
    if (durationSec <= 0.0) return 0L
    val totalBps = (videoBitrateBps.toDouble() + audioBitrateBps.toDouble())
    val bytes = (totalBps * durationSec) / 8.0
    // Include 64 KB MP4 container overhead
    return bytes.toLong().coerceAtLeast(0L) + 64 * 1024L
  }

  /**
   * Checks if estimated output size is not clearly smaller than the input (e.g. >= 95% of input).
   */
  fun isLittleOrNoReduction(estimatedSizeBytes: Long, sourceFileSizeBytes: Long): Boolean {
    if (sourceFileSizeBytes <= 0L) return false
    return estimatedSizeBytes >= (sourceFileSizeBytes * 0.95).toLong()
  }

  /**
   * Computes reduction percentage: (original - new) / original * 100.
   * Positive means smaller, negative means larger.
   */
  fun calculateReductionPercentage(originalBytes: Long, newBytes: Long): Int {
    if (originalBytes <= 0L) return 0
    val saved = originalBytes - newBytes
    return ((saved.toDouble() / originalBytes.toDouble()) * 100.0).roundToInt()
  }

  /**
   * Generates default compressed output filename: "<name>_compressed.mp4".
   */
  fun generateCompressedOutputName(inputName: String): String {
    val base = inputName.substringBeforeLast(".")
    return "${base}_compressed.mp4"
  }
}
