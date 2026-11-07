package com.example.tools.pdfeditor

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Makes a rendered PDF page clearer when it needs it: faded scans get their contrast stretched
 * and soft (blurry) text gets a gentle sharpen. Crisp pages are returned untouched, so the very same
 * Bitmap instance comes back when nothing had to change (callers can check with `!==`).
 *
 * Pure pixel maths, no Android UI classes, safe to call from a background thread.
 */
object PdfPageEnhancer {

  /** Page edges weaker than this (99.5th percentile of the Laplacian) count as "blurry". */
  private const val SHARP_EDGE_THRESHOLD = 70
  private const val SHARPEN_AMOUNT = 0.6f

  fun enhance(src: Bitmap): Bitmap {
    return try {
      enhanceInternal(src)
    } catch (_: Throwable) {
      // Out of memory or an unsupported bitmap: show the page as it is.
      src
    }
  }

  private fun lum(c: Int): Int =
    (77 * ((c shr 16) and 0xFF) + 150 * ((c shr 8) and 0xFF) + 29 * (c and 0xFF)) shr 8

  private fun percentile(hist: IntArray, total: Int, p: Float): Int {
    val target = (total * p).toInt()
    var acc = 0
    for (i in hist.indices) {
      acc += hist[i]
      if (acc >= target) return i
    }
    return hist.size - 1
  }

  private fun enhanceInternal(src: Bitmap): Bitmap {
    val w = src.width
    val h = src.height
    if (w < 16 || h < 16) return src

    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    var changed = false

    // 1. Contrast: only faded pages (narrow brightness range) are stretched.
    val hist = IntArray(256)
    val sampleStep = max(1, px.size / 400_000)
    var total = 0
    var i = 0
    while (i < px.size) {
      hist[lum(px[i])]++
      total++
      i += sampleStep
    }
    val lo = percentile(hist, total, 0.01f)
    val hi = percentile(hist, total, 0.99f)
    val range = hi - lo
    if (range in 40..199) {
      val lut = IntArray(256) { v -> (((v - lo) * 255f) / range).toInt().coerceIn(0, 255) }
      for (k in px.indices) {
        val c = px[k]
        val r = lut[(c shr 16) and 0xFF]
        val g = lut[(c shr 8) and 0xFF]
        val b = lut[c and 0xFF]
        px[k] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
      }
      changed = true
    }

    // 2. Sharpness: measure how strong the strongest edges are.
    val lapHist = IntArray(256)
    var lapTotal = 0
    var y = 1
    while (y < h - 1) {
      val row = y * w
      var x = 1
      while (x < w - 1) {
        val idx = row + x
        val l = 4 * lum(px[idx]) - lum(px[idx - 1]) - lum(px[idx + 1]) - lum(px[idx - w]) - lum(px[idx + w])
        lapHist[min(255, abs(l))]++
        lapTotal++
        x += 2
      }
      y += 2
    }
    var edgeCount = 0
    for (v in 12..255) edgeCount += lapHist[v]
    val hasEdges = lapTotal > 0 && edgeCount >= lapTotal * 0.002f
    val edgeStrength = if (lapTotal > 0) percentile(lapHist, lapTotal, 0.995f) else 255
    val needsSharpen = hasEdges && edgeStrength < SHARP_EDGE_THRESHOLD

    var result = px
    if (needsSharpen) {
      val out = px.copyOf()
      for (yy in 1 until h - 1) {
        val row = yy * w
        for (xx in 1 until w - 1) {
          val idx = row + xx
          val c = px[idx]
          val u = px[idx - w]
          val d = px[idx + w]
          val l = px[idx - 1]
          val r = px[idx + 1]
          val nr = sharpChannel((c shr 16) and 0xFF, (u shr 16) and 0xFF, (d shr 16) and 0xFF, (l shr 16) and 0xFF, (r shr 16) and 0xFF)
          val ng = sharpChannel((c shr 8) and 0xFF, (u shr 8) and 0xFF, (d shr 8) and 0xFF, (l shr 8) and 0xFF, (r shr 8) and 0xFF)
          val nb = sharpChannel(c and 0xFF, u and 0xFF, d and 0xFF, l and 0xFF, r and 0xFF)
          out[idx] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
      }
      result = out
      changed = true
    }

    if (!changed) return src
    return Bitmap.createBitmap(result, w, h, Bitmap.Config.ARGB_8888)
  }

  private fun sharpChannel(c: Int, u: Int, d: Int, l: Int, r: Int): Int =
    (c + SHARPEN_AMOUNT * (4 * c - u - d - l - r) + 0.5f).toInt().coerceIn(0, 255)
}
