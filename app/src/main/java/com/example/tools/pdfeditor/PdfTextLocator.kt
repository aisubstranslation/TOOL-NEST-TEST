package com.example.tools.pdfeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.example.file.FileLayer
import com.example.pdf.PdfFileHelper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * One piece of text on a page (usually one line, or one table cell) that the user can tap to edit.
 * All coordinates are normalized to the page (0..1, origin top-left).
 */
data class TextSegment(
  val text: String,
  /** Area that must be covered to hide the old text. */
  val coverBounds: RectF,
  val leftX: Float,
  val rightX: Float,
  /** Baseline of the text, from the top of the page. */
  val baselineY: Float,
  val fontFamily: String,
  val isBold: Boolean,
  val isItalic: Boolean,
  val fontSizePt: Float,
  val isRtl: Boolean,
  /** PDF name of the font used by the original text (null when unknown). */
  val fontName: String? = null,
  /** false when the box was found from pixels only (scanned page), so the old text is unknown. */
  val textKnown: Boolean
)

/**
 * Finds editable text on a page in two ways:
 *  1. Real text layer (PDFBox): exact position, size and an estimate of the font style.
 *  2. Pixels (scanned pages or text drawn as an image): detects the line under the finger.
 */
object PdfTextLocator {

  // ------------------------------------------------------------------ text layer

  suspend fun extractSegments(context: Context, file: File, pageIndex: Int): List<TextSegment> =
    withContext(Dispatchers.IO) {
      var doc: PDDocument? = null
      try {
        doc = PdfFileHelper.openDocument(file, FileLayer.getTempFolder(context))
        if (doc.isEncrypted || pageIndex < 0 || pageIndex >= doc.numberOfPages) {
          return@withContext emptyList<TextSegment>()
        }
        val page = doc.getPage(pageIndex)
        // Rotated pages use a different coordinate system; those fall back to pixel detection.
        if (PdfCoordMapper.normalizeRotation(page.rotation) != 0) {
          return@withContext emptyList<TextSegment>()
        }
        val box = page.cropBox ?: page.mediaBox
        val collector = SegmentCollector()
        collector.setStartPage(pageIndex + 1)
        collector.setEndPage(pageIndex + 1)
        collector.getText(doc)
        buildSegments(collector.chunks, box.width, box.height)
      } catch (_: Throwable) {
        emptyList<TextSegment>()
      } finally {
        try { doc?.close() } catch (_: Exception) {}
      }
    }

  private class Chunk(
    var text: String,
    var left: Float,
    var right: Float,
    val baseline: Float,
    val fontSize: Float,
    val fontName: String?
  )

  private class SegmentCollector : PDFTextStripper() {
    val chunks = mutableListOf<Chunk>()

    init {
      setSortByPosition(true)
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
      if (textPositions.isEmpty() || text.isBlank()) return
      val first = textPositions[0]
      var left = Float.MAX_VALUE
      var right = -Float.MAX_VALUE
      for (tp in textPositions) {
        left = min(left, tp.xDirAdj)
        right = max(right, tp.xDirAdj + tp.widthDirAdj)
      }
      val size = first.fontSizeInPt.takeIf { it > 0f } ?: first.heightDir
      val fontName = try { first.font?.name } catch (_: Throwable) { null }
      chunks.add(Chunk(text.trim(), left, right, first.yDirAdj, size, fontName))
    }
  }

  private fun buildSegments(chunks: List<Chunk>, pageW: Float, pageH: Float): List<TextSegment> {
    if (pageW <= 0f || pageH <= 0f) return emptyList()

    // Merge neighbouring words on the same line into one editable segment.
    val merged = ArrayList<Chunk>()
    for (c in chunks) {
      val last = merged.lastOrNull()
      if (last != null && abs(last.baseline - c.baseline) < 0.35f * last.fontSize) {
        val gapRight = c.left - last.right          // next word is to the right
        val gapLeft = last.left - c.right           // next word is to the left (right-to-left text)
        if (gapRight < 0.9f * last.fontSize && c.left >= last.left - 0.5f * last.fontSize) {
          last.text = last.text + (if (gapRight > 0.15f * last.fontSize) " " else "") + c.text
          last.right = max(last.right, c.right)
          continue
        } else if (gapLeft < 0.9f * last.fontSize && gapLeft > -0.5f * last.fontSize) {
          last.text = c.text + (if (gapLeft > 0.15f * last.fontSize) " " else "") + last.text
          last.left = min(last.left, c.left)
          continue
        }
      }
      merged.add(Chunk(c.text, c.left, c.right, c.baseline, c.fontSize, c.fontName))
    }

    val result = ArrayList<TextSegment>(merged.size)
    for (m in merged) {
      val fs = m.fontSize.coerceAtLeast(1f)
      val (family, bold, italic) = classifyFont(m.fontName)
      val cover = RectF(
        ((m.left - 0.12f * fs) / pageW).coerceIn(0f, 1f),
        ((m.baseline - 0.88f * fs) / pageH).coerceIn(0f, 1f),
        ((m.right + 0.12f * fs) / pageW).coerceIn(0f, 1f),
        ((m.baseline + 0.30f * fs) / pageH).coerceIn(0f, 1f)
      )
      if (cover.width() <= 0f || cover.height() <= 0f) continue
      result.add(
        TextSegment(
          text = m.text,
          coverBounds = cover,
          leftX = (m.left / pageW).coerceIn(0f, 1f),
          rightX = (m.right / pageW).coerceIn(0f, 1f),
          baselineY = (m.baseline / pageH).coerceIn(0f, 1f),
          fontFamily = family,
          isBold = bold,
          isItalic = italic,
          fontSizePt = fs,
          isRtl = containsRtl(m.text),
          fontName = m.fontName,
          textKnown = true
        )
      )
    }
    return result
  }

  /** Picks the closest of the built-in font families (Sans / Serif / Mono) for a PDF font name. */
  internal fun classifyFont(rawName: String?): Triple<String, Boolean, Boolean> {
    val name = (rawName ?: "").lowercase()
    val bold = name.contains("bold") || name.contains("black") || name.contains("heavy") || name.contains("semibold")
    val italic = name.contains("italic") || name.contains("oblique")
    val mono = listOf("courier", "mono", "consolas", "lucida console", "typewriter").any { name.contains(it) }
    val serif = !name.contains("sans") && listOf(
      "times", "serif", "georgia", "garamond", "palatino", "cambria", "minion", "book", "roman", "nastaliq", "naskh"
    ).any { name.contains(it) }
    val family = when {
      mono -> "Mono"
      serif -> "Serif"
      else -> "Sans"
    }
    return Triple(family, bold, italic)
  }

  internal fun containsRtl(text: String): Boolean {
    for (ch in text) {
      val c = ch.code
      if ((c in 0x0590..0x08FF) || (c in 0xFB1D..0xFEFF)) return true
    }
    return false
  }

  fun findAt(segments: List<TextSegment>, nx: Float, ny: Float): TextSegment? {
    var best: TextSegment? = null
    var bestArea = Float.MAX_VALUE
    for (s in segments) {
      val b = s.coverBounds
      if (nx >= b.left - 0.004f && nx <= b.right + 0.004f && ny >= b.top - 0.004f && ny <= b.bottom + 0.004f) {
        val area = b.width() * b.height()
        if (area < bestArea) {
          best = s
          bestArea = area
        }
      }
    }
    return best
  }

  // ------------------------------------------------------------------ pixels

  private fun lum(c: Int): Int =
    (77 * ((c shr 16) and 0xFF) + 150 * ((c shr 8) and 0xFF) + 29 * (c and 0xFF)) shr 8

  /** Most common colour of a pixel set (the page background in a text area). */
  private fun dominantColor(px: IntArray): Int {
    val cnt = IntArray(4096)
    val sr = IntArray(4096)
    val sg = IntArray(4096)
    val sb = IntArray(4096)
    val step = max(1, px.size / 100_000)
    var i = 0
    while (i < px.size) {
      val c = px[i]
      val r = (c shr 16) and 0xFF
      val g = (c shr 8) and 0xFF
      val b = c and 0xFF
      val k = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
      cnt[k]++
      sr[k] += r
      sg[k] += g
      sb[k] += b
      i += step
    }
    var best = 0
    for (k in 1 until 4096) if (cnt[k] > cnt[best]) best = k
    val n = max(1, cnt[best])
    return (0xFF shl 24) or ((sr[best] / n) shl 16) or ((sg[best] / n) shl 8) or (sb[best] / n)
  }

  /** Returns (background colour, text colour) found inside a normalized rectangle of the page bitmap. */
  fun sampleColors(bmp: Bitmap, area: RectF): Pair<Int, Int> {
    val w = bmp.width
    val h = bmp.height
    val x0 = (area.left * w).toInt().coerceIn(0, w - 1)
    val y0 = (area.top * h).toInt().coerceIn(0, h - 1)
    val x1 = (area.right * w).toInt().coerceIn(x0 + 1, w)
    val y1 = (area.bottom * h).toInt().coerceIn(y0 + 1, h)
    val ww = x1 - x0
    val wh = y1 - y0
    val all = IntArray(ww * wh)
    try {
      bmp.getPixels(all, 0, ww, x0, y0, ww, wh)
    } catch (_: Throwable) {
      return Pair(0xFFFFFFFF.toInt(), 0xFF000000.toInt())
    }
    val bg = dominantColor(all)
    val bgLum = lum(bg)
    var maxDiff = 0
    for (c in all) maxDiff = max(maxDiff, abs(lum(c) - bgLum))
    if (maxDiff < 40) {
      val ink = if (bgLum > 128) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
      return Pair(bg, ink)
    }
    val cutoff = (maxDiff * 0.8f).toInt()
    var sr = 0L
    var sg = 0L
    var sb = 0L
    var n = 0L
    for (c in all) {
      if (abs(lum(c) - bgLum) >= cutoff) {
        sr += (c shr 16) and 0xFF
        sg += (c shr 8) and 0xFF
        sb += c and 0xFF
        n++
      }
    }
    if (n == 0L) return Pair(bg, if (bgLum > 128) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
    val ink = (0xFF shl 24) or ((sr / n).toInt() shl 16) or ((sg / n).toInt() shl 8) or (sb / n).toInt()
    return Pair(bg, ink)
  }

  /**
   * Looks at the pixels around a tap and finds the line/word group of text under it.
   * Works on scanned pages. The old text is unknown, so [TextSegment.textKnown] is false.
   */
  fun findInBitmap(bmp: Bitmap, nx: Float, ny: Float, pageWidthPt: Float): TextSegment? {
    val w = bmp.width
    val h = bmp.height
    if (w < 20 || h < 20) return null
    val cx = (nx * w).toInt().coerceIn(0, w - 1)
    val cy = (ny * h).toInt().coerceIn(0, h - 1)
    val halfW = (w * 0.45f).toInt()
    val halfH = max(24, (h * 0.06f).toInt())
    val x0 = max(0, cx - halfW)
    val x1 = min(w - 1, cx + halfW)
    val y0 = max(0, cy - halfH)
    val y1 = min(h - 1, cy + halfH)
    val ww = x1 - x0 + 1
    val wh = y1 - y0 + 1
    val px = IntArray(ww * wh)
    try {
      bmp.getPixels(px, 0, ww, x0, y0, ww, wh)
    } catch (_: Throwable) {
      return null
    }

    val bgLum = lum(dominantColor(px))
    val ink = BooleanArray(px.size)
    val rowCnt = IntArray(wh)
    for (y in 0 until wh) {
      val base = y * ww
      var cnt = 0
      for (x in 0 until ww) {
        if (abs(lum(px[base + x]) - bgLum) > 55) {
          ink[base + x] = true
          cnt++
        }
      }
      rowCnt[y] = cnt
    }

    val ty = cy - y0
    val tx = cx - x0
    val snap = max(6, (h * 0.012f).toInt())
    var seed = -1
    for (d in 0..snap) {
      if (ty - d >= 0 && rowCnt[ty - d] >= 2) { seed = ty - d; break }
      if (ty + d < wh && rowCnt[ty + d] >= 2) { seed = ty + d; break }
    }
    if (seed < 0) return null

    val gapLimit = max(3, h / 900)
    fun extendRows(dir: Int): Int {
      var y = seed
      var gap = 0
      var last = seed
      while (true) {
        val n = y + dir
        if (n < 0 || n >= wh) break
        if (rowCnt[n] >= 2) {
          last = n
          gap = 0
        } else {
          gap++
          if (gap > gapLimit) break
        }
        y = n
      }
      return last
    }
    val top = extendRows(-1)
    val bottom = extendRows(1)
    val lineH = bottom - top + 1
    if (lineH < 4 || lineH > h * 0.12f) return null

    val colInk = BooleanArray(ww)
    for (y in top..bottom) {
      val base = y * ww
      for (x in 0 until ww) if (ink[base + x]) colInk[x] = true
    }
    var sx = -1
    for (d in 0..(lineH * 2)) {
      if (tx - d >= 0 && colInk[tx - d]) { sx = tx - d; break }
      if (tx + d < ww && colInk[tx + d]) { sx = tx + d; break }
    }
    if (sx < 0) return null
    val colGap = max(8, (lineH * 0.9f).toInt())
    fun extendCols(dir: Int): Int {
      var x = sx
      var gap = 0
      var last = sx
      while (true) {
        val n = x + dir
        if (n < 0 || n >= ww) break
        if (colInk[n]) {
          last = n
          gap = 0
        } else {
          gap++
          if (gap > colGap) break
        }
        x = n
      }
      return last
    }
    val left = extendCols(-1)
    val right = extendCols(1)

    val pad = 3
    val bx0 = max(0, x0 + left - pad)
    val bx1 = min(w - 1, x0 + right + pad)
    val by0 = max(0, y0 + top - pad)
    val by1 = min(h - 1, y0 + bottom + pad)
    if (bx1 - bx0 < 4) return null

    val pxToPt = if (pageWidthPt > 0f) pageWidthPt / w else 0.3f
    val boxHeightPt = lineH * pxToPt
    val fontSize = (boxHeightPt / 0.95f).coerceIn(5f, 120f)
    val baselinePx = (y0 + bottom) - 0.18f * lineH

    return TextSegment(
      text = "",
      coverBounds = RectF(bx0.toFloat() / w, by0.toFloat() / h, (bx1 + 1).toFloat() / w, (by1 + 1).toFloat() / h),
      leftX = (x0 + left).toFloat() / w,
      rightX = (x0 + right + 1).toFloat() / w,
      baselineY = baselinePx / h,
      fontFamily = "Sans",
      isBold = false,
      isItalic = false,
      fontSizePt = fontSize,
      isRtl = false,
      textKnown = false
    )
  }
}
