package com.example.tools.aiinspect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

object ElaEngine {

  data class ElaResult(
    val meanDifference: Float,
    val maxDifference: Float,
    val hotPixelsRatio: Float,
    val heatmapFile: File?
  )

  /**
   * Computes Error Level Analysis (ELA) for a bitmap or compressed image file.
   */
  fun analyzeBitmap(
    sourceBitmap: Bitmap,
    outputHeatmapFile: File? = null,
    scaleMultiplier: Int = 15,
    hotThreshold: Int = 40
  ): ElaResult {
    // 1. Scale down if long edge > 1600 px
    val maxEdge = max(sourceBitmap.width, sourceBitmap.height)
    val baseBmp = if (maxEdge > 1600) {
      val scale = 1600f / maxEdge.toFloat()
      val w = (sourceBitmap.width * scale).toInt().coerceAtLeast(1)
      val h = (sourceBitmap.height * scale).toInt().coerceAtLeast(1)
      Bitmap.createScaledBitmap(sourceBitmap, w, h, true)
    } else {
      sourceBitmap
    }

    val width = baseBmp.width
    val height = baseBmp.height

    // 2. Recompress at Quality 90 JPEG
    val recompressedBytes = ByteArrayOutputStream().use { baos ->
      baseBmp.compress(Bitmap.CompressFormat.JPEG, 90, baos)
      baos.toByteArray()
    }

    val recompressedBmp = BitmapFactory.decodeByteArray(recompressedBytes, 0, recompressedBytes.size)
      ?: return ElaResult(0f, 0f, 0f, null)

    // 3. Pixel delta comparison
    val srcPixels = IntArray(width * height)
    val recPixels = IntArray(width * height)
    baseBmp.getPixels(srcPixels, 0, width, 0, 0, width, height)
    recompressedBmp.getPixels(recPixels, 0, width, 0, 0, width, height)

    val deltaPixels = IntArray(width * height)
    var totalDelta = 0.0
    var maxDelta = 0
    var hotPixelsCount = 0

    for (i in 0 until (width * height)) {
      val c1 = srcPixels[i]
      val c2 = recPixels[i]

      val dr = abs(((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF))
      val dg = abs(((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF))
      val db = abs((c1 and 0xFF) - (c2 and 0xFF))

      val pixelDelta = (dr + dg + db) / 3
      totalDelta += pixelDelta
      if (pixelDelta > maxDelta) maxDelta = pixelDelta
      if (pixelDelta >= hotThreshold) hotPixelsCount++

      // Scaled heatmap brightness
      val displayR = (dr * scaleMultiplier).coerceIn(0, 255)
      val displayG = (dg * scaleMultiplier).coerceIn(0, 255)
      val displayB = (db * scaleMultiplier).coerceIn(0, 255)
      deltaPixels[i] = (0xFF shl 24) or (displayR shl 16) or (displayG shl 8) or displayB
    }

    val totalPixels = (width * height).toFloat()
    val mean = (totalDelta / totalPixels).toFloat()
    val hotRatio = hotPixelsCount / totalPixels

    // 4. Save heatmap if requested
    var savedFile: File? = null
    if (outputHeatmapFile != null) {
      val heatmapBmp = Bitmap.createBitmap(deltaPixels, width, height, Bitmap.Config.ARGB_8888)
      try {
        FileOutputStream(outputHeatmapFile).use { fos ->
          heatmapBmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        savedFile = outputHeatmapFile
      } catch (_: Exception) {
      } finally {
        heatmapBmp.recycle()
      }
    }

    if (baseBmp != sourceBitmap) {
      baseBmp.recycle()
    }
    recompressedBmp.recycle()

    return ElaResult(
      meanDifference = mean,
      maxDifference = maxDelta.toFloat(),
      hotPixelsRatio = hotRatio,
      heatmapFile = savedFile
    )
  }

  /**
   * Pure numerical difference between two pixel arrays for JVM unit testing without Android Bitmap.
   */
  fun calculatePurePixelDelta(
    originalPixels: IntArray,
    modifiedPixels: IntArray
  ): Pair<Float, Float> {
    if (originalPixels.isEmpty() || originalPixels.size != modifiedPixels.size) return 0f to 0f
    var totalDelta = 0.0
    var maxDelta = 0

    for (i in originalPixels.indices) {
      val c1 = originalPixels[i]
      val c2 = modifiedPixels[i]
      val dr = abs(((c1 shr 16) and 0xFF) - ((c2 shr 16) and 0xFF))
      val dg = abs(((c1 shr 8) and 0xFF) - ((c2 shr 8) and 0xFF))
      val db = abs((c1 and 0xFF) - (c2 and 0xFF))
      val d = (dr + dg + db) / 3
      totalDelta += d
      if (d > maxDelta) maxDelta = d
    }

    val mean = (totalDelta / originalPixels.size.toDouble()).toFloat()
    return mean to maxDelta.toFloat()
  }
}
